package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.data.local.entity.BudgetPolicyEntity
import net.loeu.wallybudget.data.local.entity.ExpenseEntity
import net.loeu.wallybudget.data.snapshot.DocumentUriGateway
import net.loeu.wallybudget.data.snapshot.GzipSnapshotCodec
import net.loeu.wallybudget.data.snapshot.SnapshotCompatibilityService
import net.loeu.wallybudget.data.snapshot.SnapshotJsonCodec
import net.loeu.wallybudget.data.snapshot.model.SnapshotBudgetPolicyRecordV1
import net.loeu.wallybudget.data.snapshot.model.SnapshotBudgetBucketRecordV3
import net.loeu.wallybudget.data.snapshot.model.SnapshotBucketAllocationPolicyRecordV3
import net.loeu.wallybudget.data.snapshot.model.SnapshotEnvelopeV1
import net.loeu.wallybudget.data.snapshot.model.SnapshotExpenseRecordV1
import net.loeu.wallybudget.data.snapshot.model.SnapshotSettingsRecordV1
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_NAME
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import net.loeu.wallybudget.domain.service.BucketAllocationResolver
import net.loeu.wallybudget.domain.model.SnapshotError
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BudgetAdjustmentResolver
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.ZoneId

class SnapshotUseCasesTest {

    private fun rebuildBucketHistory(
        expenseDao: FakeExpenseDao,
        bucketAllocationPolicyDao: FakeBucketAllocationPolicyDao = FakeBucketAllocationPolicyDao(),
        bucketAllocationAdjustmentDao: FakeBucketAllocationAdjustmentDao = FakeBucketAllocationAdjustmentDao(),
        bucketMonthlyHistoryDao: FakeBucketMonthlyHistoryDao = FakeBucketMonthlyHistoryDao()
    ): RebuildBucketMonthlyHistoryUseCase {
        return RebuildBucketMonthlyHistoryUseCase(
            bucketAllocationPolicyDao = bucketAllocationPolicyDao,
            bucketAllocationAdjustmentDao = bucketAllocationAdjustmentDao,
            expenseDao = expenseDao,
            bucketMonthlyHistoryDao = bucketMonthlyHistoryDao,
            budgetCalculationService = BudgetCalculationService(),
            bucketAllocationResolver = BucketAllocationResolver()
        )
    }

    @Test
    fun gzipSnapshotCodec_roundTripsCompactJsonEnvelope() {
        val payload = GzipSnapshotCodec().decodeFromBytes(
            GzipSnapshotCodec().encodeToGzip(
                SnapshotJsonCodec().encode(sampleEnvelope())
            )
        )

        assertTrue(payload.compressed)
        assertTrue(payload.text.contains("\"format\":\"wallybudget-snapshot\""))
        assertTrue(payload.text.contains("\"budgetPolicies\""))
        assertTrue(payload.text.contains("\"expenses\""))
    }

    @Test
    fun snapshotJsonCodec_decodesEnvelopeRecords() {
        val preparedEnvelope = SnapshotJsonCodec().decode(
            SnapshotJsonCodec().encode(sampleEnvelope())
        )

        assertEquals(1, preparedEnvelope.expenses.size)
        assertEquals(1, preparedEnvelope.budgetPolicies.size)
        assertEquals("expense-1", preparedEnvelope.expenses.single().recordUuid)
        assertEquals(100_000L, preparedEnvelope.settings.defaultMonthlyBudgetCents)
    }

    @Test
    fun snapshotJsonCodec_rejectsMissingRequiredEnvelopeFields() {
        val missingSettingsJson = """
            {"format":"wallybudget-snapshot","schemaVersion":1,"snapshotId":"id","exportedAtEpochMs":1,"writerInstallId":"install-a","snapshotModClock":"clock","appVersionName":"1.0","budgetPolicies":[],"expenses":[]}
        """.trimIndent()
        try {
            SnapshotJsonCodec().decode(missingSettingsJson)
            throw AssertionError("Expected malformed snapshot failure")
        } catch (exception: IllegalArgumentException) {
            assertTrue(exception.message?.contains("settings") == true)
        }
    }

    @Test
    fun prepareSnapshotImport_rejectsOversizedPayload() = runBlocking {
        val useCase = PrepareSnapshotImportUseCase(
            documentUriGateway = FakeDocumentUriGateway(
                inputBytes = ByteArray(PrepareSnapshotImportUseCase.MAX_SNAPSHOT_BYTES + 1) {
                    'a'.code.toByte()
                }
            ),
            gzipSnapshotCodec = GzipSnapshotCodec(),
            snapshotJsonCodec = SnapshotJsonCodec(),
            snapshotCompatibilityService = SnapshotCompatibilityService()
        )

        try {
            useCase.readSnapshotBytes(
                ByteArrayInputStream(ByteArray(PrepareSnapshotImportUseCase.MAX_SNAPSHOT_BYTES + 1))
            )
            throw AssertionError("Expected oversized snapshot to be rejected")
        } catch (exception: SnapshotOperationException) {
            assertEquals(
                SnapshotError.IoFailure("The selected snapshot file is too large to import safely."),
                exception.snapshotError
            )
        }
    }

    @Test
    fun prepareSnapshotImport_wrapsMappingFailuresAsMalformedSnapshot() = runBlocking {
        val bytes = GzipSnapshotCodec().encodeToGzip(
            """
            {"format":"wallybudget-snapshot","schemaVersion":1,"snapshotId":"snapshot-1","baseSnapshotId":null,"exportedAtEpochMs":1234,"writerInstallId":"install-a","snapshotModClock":"0000000001234-0000-install-a","appVersionName":"1.0","settings":{"recordUuid":"settings-1","defaultMonthlyBudgetCents":100000,"paydayDate":25,"lastResetTimestamp":0,"pendingCycleStartDate":null,"pendingCycleEndDateExclusive":null,"pendingCycleDetectedAtTimestamp":0,"updatedAtEpochMs":1234,"modClock":"0000000001234-0000-install-a","lastModifiedByInstallId":"install-a"},"budgetPolicies":[{"policyUuid":"policy-1","cycleStartDate":"2026-03-25","cycleEndDateExclusive":"2026-04-25","budgetAmountCents":100000,"paydayDayOfMonth":25,"originInstallId":"install-a","lastModifiedByInstallId":"install-a","createdAtEpochMs":1234,"updatedAtEpochMs":1234,"deletedAtEpochMs":null,"modClock":"0000000001234-0000-install-a"}],"expenses":[{"recordUuid":"expense-1","amountCents":5000,"timestampEpochMs":1234,"expenseDate":"2026-03-26","icon":"FOOD","originInstallId":"install-a","lastModifiedByInstallId":"install-a","createdAtEpochMs":1234,"updatedAtEpochMs":1234,"deletedAtEpochMs":null,"modClock":"0000000001234-0000-install-a"}]}
            """.trimIndent()
        )
        val useCase = PrepareSnapshotImportUseCase(
            documentUriGateway = FakeDocumentUriGateway(inputBytes = bytes),
            gzipSnapshotCodec = GzipSnapshotCodec(),
            snapshotJsonCodec = SnapshotJsonCodec(),
            snapshotCompatibilityService = SnapshotCompatibilityService()
        )

        try {
            useCase.prepareFromBytes(bytes)
            throw AssertionError("Expected malformed snapshot mapping failure")
        } catch (exception: SnapshotOperationException) {
            assertEquals(SnapshotError.MalformedSnapshot, exception.snapshotError)
        }
    }

    @Test
    fun prepareSnapshotImport_rejectsUnknownFundTransactionType() = runBlocking {
        val bytes = GzipSnapshotCodec().encodeToGzip(
            """
            {"format":"wallybudget-snapshot","schemaVersion":5,"snapshotId":"snapshot-1","baseSnapshotId":null,"exportedAtEpochMs":1234,"writerInstallId":"install-a","snapshotModClock":"0000000001234-0000-install-a","appVersionName":"1.0","settings":{"recordUuid":"settings-1","defaultMonthlyBudgetCents":100000,"paydayDate":25,"lastResetTimestamp":0,"pendingCycleStartDate":null,"pendingCycleEndDateExclusive":null,"pendingCycleDetectedAtTimestamp":0,"updatedAtEpochMs":1234,"modClock":"0000000001234-0000-install-a","lastModifiedByInstallId":"install-a"},"budgetPolicies":[{"policyUuid":"policy-1","cycleStartDate":"2026-03-25","cycleEndDateExclusive":"2026-04-25","budgetAmountCents":100000,"paydayDayOfMonth":25,"originInstallId":"install-a","lastModifiedByInstallId":"install-a","createdAtEpochMs":1234,"updatedAtEpochMs":1234,"deletedAtEpochMs":null,"modClock":"0000000001234-0000-install-a"}],"expenses":[],"funds":[{"uuid":"fund-1","name":"Savings","balanceCents":1000,"allocationPerCycleCents":0,"targetAmountCents":null,"sortOrder":0,"originInstallId":"install-a","lastModifiedByInstallId":"install-a","createdAtEpochMs":1234,"updatedAtEpochMs":1234,"closedAtEpochMs":null,"deletedAtEpochMs":null,"modClock":"0000000001234-0000-install-a"}],"fundTransactions":[{"uuid":"txn-1","fundUuid":"fund-1","amountCents":1000,"type":"BONUS","description":"","dateEpochMs":1234}]}
            """.trimIndent()
        )
        val useCase = PrepareSnapshotImportUseCase(
            documentUriGateway = FakeDocumentUriGateway(inputBytes = bytes),
            gzipSnapshotCodec = GzipSnapshotCodec(),
            snapshotJsonCodec = SnapshotJsonCodec(),
            snapshotCompatibilityService = SnapshotCompatibilityService()
        )

        try {
            useCase.prepareFromBytes(bytes)
            throw AssertionError("Expected malformed snapshot for unknown fund transaction type")
        } catch (exception: SnapshotOperationException) {
            assertEquals(SnapshotError.MalformedSnapshot, exception.snapshotError)
        }
    }

    @Test
    @Suppress("LongMethod")
    fun applyOnboardingRestore_restoresDataAndRebuildsHistory() = runBlocking {
        val expenseDao = FakeExpenseDao()
        val budgetPolicyDao = FakeBudgetPolicyDao()
        val budgetAdjustmentDao = FakeBudgetAdjustmentDao()
        val monthlyHistoryDao = FakeMonthlyHistoryDao()
        val settingsStore = FakeUserSettingsStore()
        val budgetBucketDao = FakeBudgetBucketDao()
        val bucketAllocationPolicyDao = FakeBucketAllocationPolicyDao()
        val bucketAllocationAdjustmentDao = FakeBucketAllocationAdjustmentDao()
        val bucketMonthlyHistoryDao = FakeBucketMonthlyHistoryDao()
        val lastResetTimestamp = LocalDate.of(2026, 4, 25)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val prepared = PreparedSnapshotImport(
            preview = net.loeu.wallybudget.domain.model.SnapshotImportPreview(
                exportedAtEpochMs = 1L,
                writerInstallId = "install-a",
                expenseCount = 1,
                tombstoneCount = 0,
                budgetPolicyCount = 1,
                defaultMonthlyBudgetCents = 100_000L,
                paydayDate = 25,
                compressed = true
            ),
            settings = UserSettings(
                monthlyBudgetCents = 100_000L,
                paydayDate = 25,
                lastResetTimestamp = lastResetTimestamp,
                installDeviceId = "install-a",
                settingsRecordUuid = "settings-1",
                settingsUpdatedAtEpochMs = 1L,
                settingsModClock = "0000000000001-0000-install-a",
                settingsLastModifiedByInstallId = "install-a"
            ),
            budgetPolicies = listOf(
                BudgetPolicyEntity(
                    policyUuid = "policy-1",
                    cycleStartDate = "2026-03-25",
                    cycleEndDateExclusive = "2026-04-25",
                    budgetAmountCents = 100_000L,
                    paydayDayOfMonth = 25,
                    originInstallId = "install-a",
                    lastModifiedByInstallId = "install-a",
                    createdAtEpochMs = 1L,
                    updatedAtEpochMs = 1L,
                    deletedAtEpochMs = null,
                    modClock = "0000000000001-0000-install-a"
                )
            ),
            budgetAdjustments = emptyList(),
            expenses = listOf(
                ExpenseEntity(
                    recordUuid = "expense-1",
                    amountCents = 12_000L,
                    description = "Groceries",
                    timestamp = 1L,
                    expenseDate = "2026-03-29",
                    icon = null,
                    originInstallId = "install-a",
                    lastModifiedByInstallId = "install-a",
                    createdAtEpochMs = 1L,
                    updatedAtEpochMs = 1L,
                    deletedAtEpochMs = null,
                    modClock = "0000000000001-0000-install-a"
                )
            )
        )
        val useCase = ApplyOnboardingRestoreUseCase(
            transactionRunner = FakeTransactionRunner(),
            expenseDao = expenseDao,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            budgetBucketDao = budgetBucketDao,
            bucketAllocationPolicyDao = bucketAllocationPolicyDao,
            bucketAllocationAdjustmentDao = bucketAllocationAdjustmentDao,
            bucketMonthlyHistoryDao = bucketMonthlyHistoryDao,
            userSettingsStore = settingsStore,
            rebuildMonthlyHistoryUseCase = RebuildMonthlyHistoryUseCase(
                budgetPolicyDao = budgetPolicyDao,
                budgetAdjustmentDao = budgetAdjustmentDao,
                expenseDao = expenseDao,
                monthlyHistoryDao = monthlyHistoryDao,
                budgetCalculationService = BudgetCalculationService(),
                budgetAdjustmentResolver = BudgetAdjustmentResolver()
            ),
            rebuildBucketMonthlyHistoryUseCase = rebuildBucketHistory(
                expenseDao = expenseDao,
                bucketAllocationPolicyDao = bucketAllocationPolicyDao,
                bucketAllocationAdjustmentDao = bucketAllocationAdjustmentDao,
                bucketMonthlyHistoryDao = bucketMonthlyHistoryDao
            )
        )

        val result = useCase(prepared)

        assertEquals(1, result.importedExpenseCount)
        assertEquals(1, result.importedBudgetPolicyCount)
        assertEquals(1, expenseDao.countAll())
        assertEquals(1, budgetPolicyDao.countAll())
        assertEquals(1, monthlyHistoryDao.currentHistory.size)
        assertTrue(settingsStore.currentSettings.isOnboardingCompleted)
    }

    @Test
    @Suppress("LongMethod")
    fun applyOnboardingRestore_allowsOverwriteWhileOnboardingIsIncomplete() = runBlocking {
        val existingExpenseDao = FakeExpenseDao(
            listOf(expenseEntityOn(1L, LocalDate.of(2026, 3, 25), 5_000L))
        )
        val existingBudgetPolicyDao = FakeBudgetPolicyDao(
            listOf(
                BudgetPolicyEntity(
                    policyUuid = "existing-policy",
                    cycleStartDate = "2026-03-25",
                    cycleEndDateExclusive = "2026-04-25",
                    budgetAmountCents = 80_000L,
                    paydayDayOfMonth = 25,
                    originInstallId = "install-a",
                    lastModifiedByInstallId = "install-a",
                    createdAtEpochMs = 1L,
                    updatedAtEpochMs = 1L,
                    deletedAtEpochMs = null,
                    modClock = "0000000000001-0000-install-a"
                )
            )
        )
        val existingHistoryDao = FakeMonthlyHistoryDao(
            initialHistory = listOf(
                historyEntity(
                    cycleStart = LocalDate.of(2026, 1, 25),
                    cycleEndExclusive = LocalDate.of(2026, 2, 25),
                    totalSpentCents = 50_000L,
                    budgetAmountCents = 90_000L
                )
            )
        )
        val existingBudgetAdjustmentDao = FakeBudgetAdjustmentDao()
        val existingBudgetBucketDao = FakeBudgetBucketDao()
        val existingBucketAllocationPolicyDao = FakeBucketAllocationPolicyDao()
        val existingBucketAllocationAdjustmentDao = FakeBucketAllocationAdjustmentDao()
        val existingBucketHistoryDao = FakeBucketMonthlyHistoryDao()
        val useCase = ApplyOnboardingRestoreUseCase(
            transactionRunner = FakeTransactionRunner(),
            expenseDao = existingExpenseDao,
            budgetPolicyDao = existingBudgetPolicyDao,
            budgetAdjustmentDao = existingBudgetAdjustmentDao,
            budgetBucketDao = existingBudgetBucketDao,
            bucketAllocationPolicyDao = existingBucketAllocationPolicyDao,
            bucketAllocationAdjustmentDao = existingBucketAllocationAdjustmentDao,
            bucketMonthlyHistoryDao = existingBucketHistoryDao,
            userSettingsStore = FakeUserSettingsStore(),
            rebuildMonthlyHistoryUseCase = RebuildMonthlyHistoryUseCase(
                budgetPolicyDao = existingBudgetPolicyDao,
                budgetAdjustmentDao = existingBudgetAdjustmentDao,
                expenseDao = existingExpenseDao,
                monthlyHistoryDao = existingHistoryDao,
                budgetCalculationService = BudgetCalculationService(),
                budgetAdjustmentResolver = BudgetAdjustmentResolver()
            ),
            rebuildBucketMonthlyHistoryUseCase = rebuildBucketHistory(
                expenseDao = existingExpenseDao,
                bucketAllocationPolicyDao = existingBucketAllocationPolicyDao,
                bucketAllocationAdjustmentDao = existingBucketAllocationAdjustmentDao,
                bucketMonthlyHistoryDao = existingBucketHistoryDao
            )
        )

        val result = useCase(
            PreparedSnapshotImport(
                preview = net.loeu.wallybudget.domain.model.SnapshotImportPreview(
                    exportedAtEpochMs = 1L,
                    writerInstallId = "install-a",
                    expenseCount = 0,
                    tombstoneCount = 0,
                    budgetPolicyCount = 0,
                    defaultMonthlyBudgetCents = 100_000L,
                    paydayDate = 25,
                    compressed = true
                ),
                settings = UserSettings(),
                budgetPolicies = emptyList(),
                budgetAdjustments = emptyList(),
                expenses = emptyList()
            )
        )

        assertEquals(0, result.importedExpenseCount)
        assertEquals(0, result.importedBudgetPolicyCount)
        assertEquals(0, existingExpenseDao.countAll())
        assertEquals(0, existingBudgetPolicyDao.countAll())
        assertTrue(existingHistoryDao.currentHistory.isEmpty())
    }

    @Test
    fun applyOnboardingRestore_blocksWhenOnboardingAlreadyCompleted() = runBlocking {
        val useCase = ApplyOnboardingRestoreUseCase(
            transactionRunner = FakeTransactionRunner(),
            expenseDao = FakeExpenseDao(listOf(expenseEntityOn(1L, LocalDate.of(2026, 3, 25), 5_000L))),
            budgetPolicyDao = FakeBudgetPolicyDao(),
            budgetAdjustmentDao = FakeBudgetAdjustmentDao(),
            budgetBucketDao = FakeBudgetBucketDao(),
            bucketAllocationPolicyDao = FakeBucketAllocationPolicyDao(),
            bucketAllocationAdjustmentDao = FakeBucketAllocationAdjustmentDao(),
            bucketMonthlyHistoryDao = FakeBucketMonthlyHistoryDao(),
            userSettingsStore = FakeUserSettingsStore(
                UserSettings(isOnboardingCompleted = true)
            ),
            rebuildMonthlyHistoryUseCase = RebuildMonthlyHistoryUseCase(
                budgetPolicyDao = FakeBudgetPolicyDao(),
                budgetAdjustmentDao = FakeBudgetAdjustmentDao(),
                expenseDao = FakeExpenseDao(),
                monthlyHistoryDao = FakeMonthlyHistoryDao(),
                budgetCalculationService = BudgetCalculationService(),
                budgetAdjustmentResolver = BudgetAdjustmentResolver()
            ),
            rebuildBucketMonthlyHistoryUseCase = rebuildBucketHistory(expenseDao = FakeExpenseDao())
        )

        try {
            useCase(
                PreparedSnapshotImport(
                    preview = net.loeu.wallybudget.domain.model.SnapshotImportPreview(
                        exportedAtEpochMs = 1L,
                        writerInstallId = "install-a",
                        expenseCount = 0,
                        tombstoneCount = 0,
                        budgetPolicyCount = 0,
                        defaultMonthlyBudgetCents = 100_000L,
                        paydayDate = 25,
                        compressed = true
                    ),
                    settings = UserSettings(),
                    budgetPolicies = emptyList(),
                    budgetAdjustments = emptyList(),
                    expenses = emptyList()
                )
            )
            throw AssertionError("Expected restore to be blocked")
        } catch (exception: SnapshotOperationException) {
            assertEquals(SnapshotError.OnboardingCompletedRestoreBlocked, exception.snapshotError)
        }
    }
}

private class FakeDocumentUriGateway(
    private val inputBytes: ByteArray = ByteArray(0)
) : DocumentUriGateway {
    val writtenBytes = ByteArrayOutputStream()

    override fun openInputStream(uri: android.net.Uri): InputStream = ByteArrayInputStream(inputBytes)

    override fun openOutputStream(uri: android.net.Uri): OutputStream = writtenBytes
}

@Suppress("LongMethod")
private fun sampleEnvelope(): SnapshotEnvelopeV1 {
    return SnapshotEnvelopeV1(
        format = SnapshotCompatibilityService.SNAPSHOT_FORMAT,
        schemaVersion = SnapshotCompatibilityService.CURRENT_SCHEMA_VERSION,
        snapshotId = "snapshot-1",
        baseSnapshotId = null,
        exportedAtEpochMs = 1234L,
        writerInstallId = "install-a",
        snapshotModClock = "0000000001234-0000-install-a",
        appVersionName = "1.0",
        settings = SnapshotSettingsRecordV1(
            recordUuid = "settings-1",
            defaultMonthlyBudgetCents = 100_000L,
            paydayDate = 25,
            lastResetTimestamp = 0L,
            pendingCycleStartDate = null,
            pendingCycleEndDateExclusive = null,
            pendingCycleDetectedAtTimestamp = 0L,
            updatedAtEpochMs = 1234L,
            modClock = "0000000001234-0000-install-a",
            lastModifiedByInstallId = "install-a"
        ),
        budgetPolicies = listOf(
            SnapshotBudgetPolicyRecordV1(
                policyUuid = "policy-1",
                cycleStartDate = "2026-03-25",
                cycleEndDateExclusive = "2026-04-25",
                budgetAmountCents = 100_000L,
                paydayDayOfMonth = 25,
                originInstallId = "install-a",
                lastModifiedByInstallId = "install-a",
                createdAtEpochMs = 1234L,
                updatedAtEpochMs = 1234L,
                deletedAtEpochMs = null,
                modClock = "0000000001234-0000-install-a"
            )
        ),
        budgetAdjustments = emptyList(),
        expenses = listOf(
            SnapshotExpenseRecordV1(
                recordUuid = "expense-1",
                amountCents = 5_000L,
                description = "Coffee",
                timestampEpochMs = 1234L,
                expenseDate = "2026-03-26",
                bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                icon = "FOOD",
                originInstallId = "install-a",
                lastModifiedByInstallId = "install-a",
                createdAtEpochMs = 1234L,
                updatedAtEpochMs = 1234L,
                deletedAtEpochMs = null,
                modClock = "0000000001234-0000-install-a"
            )
        ),
        budgetBuckets = listOf(
            SnapshotBudgetBucketRecordV3(
                bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                name = DEFAULT_SPENDING_BUCKET_NAME,
                trackingMode = "DAILY_TARGET",
                balanceBehavior = "RETURN_TO_PORTFOLIO",
                defaultAllocatedAmountCents = 100_000L,
                sortOrder = 0,
                isPrimary = true,
                originInstallId = "install-a",
                lastModifiedByInstallId = "install-a",
                createdAtEpochMs = 1234L,
                updatedAtEpochMs = 1234L,
                closedAtEpochMs = null,
                deletedAtEpochMs = null,
                modClock = "0000000001234-0000-install-a"
            )
        ),
        bucketAllocationPolicies = listOf(
            SnapshotBucketAllocationPolicyRecordV3(
                allocationUuid = "alloc-1",
                bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                cycleStartDate = "2026-03-25",
                cycleEndDateExclusive = "2026-04-25",
                allocatedAmountCents = 100_000L,
                originInstallId = "install-a",
                lastModifiedByInstallId = "install-a",
                createdAtEpochMs = 1234L,
                updatedAtEpochMs = 1234L,
                deletedAtEpochMs = null,
                modClock = "0000000001234-0000-install-a"
            )
        ),
        bucketAllocationAdjustments = emptyList()
    )
}
