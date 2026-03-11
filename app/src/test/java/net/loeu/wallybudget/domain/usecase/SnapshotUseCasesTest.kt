package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.data.local.entity.BudgetPolicyEntity
import net.loeu.wallybudget.data.local.entity.ExpenseEntity
import net.loeu.wallybudget.data.snapshot.GzipSnapshotCodec
import net.loeu.wallybudget.data.snapshot.SnapshotCompatibilityService
import net.loeu.wallybudget.data.snapshot.SnapshotJsonCodec
import net.loeu.wallybudget.data.snapshot.model.SnapshotBudgetPolicyRecordV1
import net.loeu.wallybudget.data.snapshot.model.SnapshotEnvelopeV1
import net.loeu.wallybudget.data.snapshot.model.SnapshotExpenseRecordV1
import net.loeu.wallybudget.data.snapshot.model.SnapshotSettingsRecordV1
import net.loeu.wallybudget.domain.model.SnapshotError
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SnapshotUseCasesTest {

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
    @Suppress("LongMethod")
    fun applyOnboardingRestore_restoresDataAndRebuildsHistory() = runBlocking {
        val expenseDao = FakeExpenseDao()
        val budgetPolicyDao = FakeBudgetPolicyDao()
        val monthlyHistoryDao = FakeMonthlyHistoryDao()
        val settingsStore = FakeUserSettingsStore()
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
            monthlyHistoryDao = monthlyHistoryDao,
            userSettingsStore = settingsStore,
            rebuildMonthlyHistoryUseCase = RebuildMonthlyHistoryUseCase(
                budgetPolicyDao = budgetPolicyDao,
                expenseDao = expenseDao,
                monthlyHistoryDao = monthlyHistoryDao,
                budgetCalculationService = BudgetCalculationService()
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
    fun applyOnboardingRestore_blocksWhenProfileNotEmpty() = runBlocking {
        val useCase = ApplyOnboardingRestoreUseCase(
            transactionRunner = FakeTransactionRunner(),
            expenseDao = FakeExpenseDao(listOf(expenseEntityOn(1L, LocalDate.of(2026, 3, 25), 5_000L))),
            budgetPolicyDao = FakeBudgetPolicyDao(),
            monthlyHistoryDao = FakeMonthlyHistoryDao(),
            userSettingsStore = FakeUserSettingsStore(),
            rebuildMonthlyHistoryUseCase = RebuildMonthlyHistoryUseCase(
                budgetPolicyDao = FakeBudgetPolicyDao(),
                expenseDao = FakeExpenseDao(),
                monthlyHistoryDao = FakeMonthlyHistoryDao(),
                budgetCalculationService = BudgetCalculationService()
            )
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
                    expenses = emptyList()
                )
            )
            throw AssertionError("Expected restore to be blocked")
        } catch (exception: SnapshotImportException) {
            assertEquals(SnapshotError.NonEmptyProfileRestoreBlocked, exception.snapshotError)
        }
    }
}

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
        expenses = listOf(
            SnapshotExpenseRecordV1(
                recordUuid = "expense-1",
                amountCents = 5_000L,
                description = "Coffee",
                timestampEpochMs = 1234L,
                expenseDate = "2026-03-26",
                icon = "FOOD",
                originInstallId = "install-a",
                lastModifiedByInstallId = "install-a",
                createdAtEpochMs = 1234L,
                updatedAtEpochMs = 1234L,
                deletedAtEpochMs = null,
                modClock = "0000000001234-0000-install-a"
            )
        )
    )
}
