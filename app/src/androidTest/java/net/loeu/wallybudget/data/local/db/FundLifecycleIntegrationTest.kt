package net.loeu.wallybudget.data.local.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.DEFAULT_FUND_NAME
import net.loeu.wallybudget.domain.model.DEFAULT_FUND_UUID
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_NAME
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import net.loeu.wallybudget.domain.model.Fund
import net.loeu.wallybudget.domain.model.FundTransactionType
import net.loeu.wallybudget.domain.model.FundType
import net.loeu.wallybudget.domain.model.PendingPaydayUndo
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BucketAllocationResolver
import net.loeu.wallybudget.domain.service.BudgetAdjustmentResolver
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import net.loeu.wallybudget.domain.usecase.ConcludePendingCycleUseCase
import net.loeu.wallybudget.domain.usecase.EnsureDefaultFundUseCase
import net.loeu.wallybudget.domain.usecase.PerformMonthlyResetUseCase
import net.loeu.wallybudget.domain.usecase.RebuildBucketMonthlyHistoryUseCase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FundLifecycleIntegrationTest {

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BudgetDatabase::class.java.name,
        FrameworkSQLiteOpenHelperFactory()
    )

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val hybridLogicalClockService = HybridLogicalClockService()
    private val budgetCalculationService = BudgetCalculationService()
    private val cycleScheduleResolver = CycleScheduleResolver(budgetCalculationService)
    private val budgetAdjustmentResolver = BudgetAdjustmentResolver()
    private val bucketAllocationResolver = BucketAllocationResolver()

    @After
    fun tearDown() {
        context.deleteDatabase(defaultMigrationDatabaseName)
    }

    @Test
    fun ensureDefaultFundUseCase_normalizesMigratedDefaultFund() = runBlocking {
        createLegacyDatabaseWithFunds()

        val database = Room.databaseBuilder(context, BudgetDatabase::class.java, defaultMigrationDatabaseName)
            .addMigrations(*BudgetDatabaseMigrations.all(defaultMigrationInstallId))
            .allowMainThreadQueries()
            .build()

        database.openHelper.writableDatabase

        val settingsStore = TestUserSettingsStore()
        val useCase = EnsureDefaultFundUseCase(
            transactionRunner = database,
            userSettingsStore = settingsStore,
            fundDao = database.fundDao(),
            hybridLogicalClockService = hybridLogicalClockService
        )

        useCase(LocalDate.of(2026, 3, 31))

        val defaultFund = database.fundDao().findByUuid(DEFAULT_FUND_UUID)
        assertEquals(DEFAULT_FUND_NAME, defaultFund?.name)
        assertEquals(FundType.DEFAULT_RESERVE, defaultFund?.fundType)
        assertEquals(12_500L, defaultFund?.balanceCents)
        assertEquals(0L, defaultFund?.allocationPerCycleCents)
        assertNull(defaultFund?.targetAmountCents)
        assertEquals(0, defaultFund?.sortOrder)
        assertNull(defaultFund?.closedAtEpochMs)
        assertNull(defaultFund?.deletedAtEpochMs)

        val goalFund = database.fundDao().findByUuid("fund-goal")
        assertEquals(FundType.GOAL, goalFund?.fundType)
        assertEquals("Travel", goalFund?.name)
        assertEquals(4_500L, goalFund?.balanceCents)
        assertEquals(1, goalFund?.sortOrder)

        database.close()
    }

    @Test
    fun rolloverToNextMonth_depositsCloseoutSurplusIntoFundsByWeight() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, BudgetDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val settingsStore = TestUserSettingsStore(
            UserSettings(
                monthlyBudgetCents = 100_000L,
                portfolioMonthlyBudgetCents = 100_000L,
                paydayDate = 25,
                lastResetTimestamp = epochMillis(LocalDate.of(2026, 3, 25))
            )
        )

        try {
            val identitySettings = settingsStore.ensureIdentity()
            seedBaseCycleState(database, identitySettings.installDeviceId)

            val resetUseCase = PerformMonthlyResetUseCase(
                transactionRunner = database,
                expenseDao = database.expenseDao(),
                budgetPolicyDao = database.budgetPolicyDao(),
                budgetAdjustmentDao = database.budgetAdjustmentDao(),
                budgetBucketDao = database.budgetBucketDao(),
                bucketCycleBaselineDao = null,
                monthlyHistoryDao = database.monthlyHistoryDao(),
                userSettingsStore = settingsStore,
                budgetCalculationService = budgetCalculationService,
                cycleScheduleResolver = cycleScheduleResolver,
                budgetAdjustmentResolver = budgetAdjustmentResolver,
                rebuildBucketMonthlyHistoryUseCase = RebuildBucketMonthlyHistoryUseCase(
                    bucketAllocationPolicyDao = database.bucketAllocationPolicyDao(),
                    bucketAllocationAdjustmentDao = database.bucketAllocationAdjustmentDao(),
                    expenseDao = database.expenseDao(),
                    bucketMonthlyHistoryDao = database.bucketMonthlyHistoryDao(),
                    budgetCalculationService = budgetCalculationService,
                    bucketAllocationResolver = bucketAllocationResolver
                ),
                hybridLogicalClockService = hybridLogicalClockService
            )

            val concludeUseCase = ConcludePendingCycleUseCase(
                transactionRunner = database,
                expenseDao = database.expenseDao(),
                budgetPolicyDao = database.budgetPolicyDao(),
                budgetAdjustmentDao = database.budgetAdjustmentDao(),
                budgetBucketDao = database.budgetBucketDao(),
                bucketCycleBaselineDao = null,
                bucketTransferDao = null,
                bucketAllocationAdjustmentDao = database.bucketAllocationAdjustmentDao(),
                monthlyHistoryDao = database.monthlyHistoryDao(),
                fundDao = database.fundDao(),
                fundTransactionDao = database.fundTransactionDao(),
                userSettingsStore = settingsStore,
                budgetCalculationService = budgetCalculationService,
                cycleScheduleResolver = cycleScheduleResolver,
                budgetAdjustmentResolver = budgetAdjustmentResolver,
                bucketAllocationResolver = bucketAllocationResolver,
                hybridLogicalClockService = hybridLogicalClockService,
                rebuildBucketMonthlyHistoryUseCase = RebuildBucketMonthlyHistoryUseCase(
                    bucketAllocationPolicyDao = database.bucketAllocationPolicyDao(),
                    bucketAllocationAdjustmentDao = database.bucketAllocationAdjustmentDao(),
                    expenseDao = database.expenseDao(),
                    bucketMonthlyHistoryDao = database.bucketMonthlyHistoryDao(),
                    budgetCalculationService = budgetCalculationService,
                    bucketAllocationResolver = bucketAllocationResolver
                )
            )

            resetUseCase(settingsStore.currentSettings, LocalDate.of(2026, 4, 26))

            assertEquals("2026-03-25", settingsStore.currentSettings.pendingCycleStartDate)
            assertEquals("2026-04-25", settingsStore.currentSettings.pendingCycleEndDateExclusive)
            assertEquals(epochMillis(LocalDate.of(2026, 4, 25)), settingsStore.currentSettings.lastResetTimestamp)

            concludeUseCase(settingsStore.currentSettings)

            assertNull(settingsStore.currentSettings.pendingCycleStartDate)
            assertNull(settingsStore.currentSettings.pendingCycleEndDateExclusive)
            val monthlyHistory = database.monthlyHistoryDao().getAll()
            assertEquals(1, monthlyHistory.size)
            assertEquals("2026-03-25", monthlyHistory.single().cycleStartDate)
            assertEquals(40_000L, monthlyHistory.single().totalSpentCents)
            assertEquals(60_000L, monthlyHistory.single().surplusCents)

            val reserveFund = database.fundDao().findByUuid(DEFAULT_FUND_UUID)
            val goalFund = database.fundDao().findByUuid("goal-fund")
            assertEquals(FundType.DEFAULT_RESERVE, reserveFund?.fundType)
            assertEquals(FundType.GOAL, goalFund?.fundType)
            assertEquals(112_000L, reserveFund?.balanceCents)
            assertEquals(48_000L, goalFund?.balanceCents)

            val fundTransactions = database.fundTransactionDao().getAllForSnapshot()
            assertEquals(2, fundTransactions.size)
            assertEquals(
                setOf(
                    DEFAULT_FUND_UUID to 112_000L,
                    "goal-fund" to 48_000L
                ),
                fundTransactions.map { it.fundUuid to it.amountCents }.toSet()
            )
            assertEquals(
                setOf(FundTransactionType.DEPOSIT),
                fundTransactions.map { it.type }.toSet()
            )
        } finally {
            database.close()
        }
    }

    private suspend fun createLegacyDatabaseWithFunds() {
        migrationHelper.createDatabase(defaultMigrationDatabaseName, 16).use { db ->
            db.execSQL(
                """
                INSERT INTO `funds` (
                    `uuid`,
                    `name`,
                    `balanceCents`,
                    `allocationPerCycleCents`,
                    `targetAmountCents`,
                    `sortOrder`,
                    `originInstallId`,
                    `lastModifiedByInstallId`,
                    `createdAtEpochMs`,
                    `updatedAtEpochMs`,
                    `closedAtEpochMs`,
                    `deletedAtEpochMs`,
                    `modClock`
                ) VALUES (
                    '$DEFAULT_FUND_UUID',
                    '$DEFAULT_FUND_NAME',
                    12500,
                    0,
                    NULL,
                    0,
                    'legacy-install',
                    'legacy-install',
                    1234,
                    1234,
                    9876,
                    NULL,
                    '0000000001234-0000-legacy-install'
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `funds` (
                    `uuid`,
                    `name`,
                    `balanceCents`,
                    `allocationPerCycleCents`,
                    `targetAmountCents`,
                    `sortOrder`,
                    `originInstallId`,
                    `lastModifiedByInstallId`,
                    `createdAtEpochMs`,
                    `updatedAtEpochMs`,
                    `closedAtEpochMs`,
                    `deletedAtEpochMs`,
                    `modClock`
                ) VALUES (
                    'fund-goal',
                    'Travel',
                    4500,
                    0,
                    NULL,
                    1,
                    'legacy-install',
                    'legacy-install',
                    1235,
                    1235,
                    NULL,
                    NULL,
                    '0000000001235-0000-legacy-install'
                )
                """.trimIndent()
            )
        }
    }

    private suspend fun seedBaseCycleState(
        database: BudgetDatabase,
        installId: String
    ) {
        val now = epochMillis(LocalDate.of(2026, 3, 25))
        val defaultFund = Fund(
            uuid = DEFAULT_FUND_UUID,
            name = DEFAULT_FUND_NAME,
            fundType = FundType.DEFAULT_RESERVE,
            balanceCents = 0L,
            allocationPerCycleCents = 70_000L,
            targetAmountCents = null,
            sortOrder = 0,
            originInstallId = installId,
            lastModifiedByInstallId = installId,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            modClock = hybridLogicalClockService.format(now, 0, installId)
        )
        val goalFund = Fund(
            uuid = "goal-fund",
            name = "Travel",
            fundType = FundType.GOAL,
            balanceCents = 0L,
            allocationPerCycleCents = 30_000L,
            targetAmountCents = null,
            sortOrder = 1,
            originInstallId = installId,
            lastModifiedByInstallId = installId,
            createdAtEpochMs = now + 1,
            updatedAtEpochMs = now + 1,
            modClock = hybridLogicalClockService.format(now + 1, 0, installId)
        )
        val spendingBucket = BudgetBucket(
            bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
            name = DEFAULT_SPENDING_BUCKET_NAME,
            defaultAllocatedAmountCents = 60_000L,
            sortOrder = 0,
            originInstallId = installId,
            lastModifiedByInstallId = installId,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            modClock = hybridLogicalClockService.format(now, 0, installId)
        )
        val goalBucket = BudgetBucket(
            bucketUuid = "travel-bucket",
            name = "Travel",
            defaultAllocatedAmountCents = 40_000L,
            sortOrder = 1,
            originInstallId = installId,
            lastModifiedByInstallId = installId,
            createdAtEpochMs = now + 1,
            updatedAtEpochMs = now + 1,
            modClock = hybridLogicalClockService.format(now + 1, 0, installId)
        )

        database.fundDao().insert(defaultFund.toEntity())
        database.fundDao().insert(goalFund.toEntity())
        database.budgetBucketDao().insert(spendingBucket.toEntity())
        database.budgetBucketDao().insert(goalBucket.toEntity())
        database.expenseDao().insert(
            expense(
                recordUuid = "expense-default",
                bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                amountCents = 20_000L,
                expenseDate = LocalDate.of(2026, 4, 5),
                installId = installId
            ).toEntity()
        )
        database.expenseDao().insert(
            expense(
                recordUuid = "expense-goal",
                bucketUuid = "travel-bucket",
                amountCents = 20_000L,
                expenseDate = LocalDate.of(2026, 4, 10),
                installId = installId
            ).toEntity()
        )
    }

    private fun expense(
        recordUuid: String,
        bucketUuid: String,
        amountCents: Long,
        expenseDate: LocalDate,
        installId: String
    ) = net.loeu.wallybudget.domain.model.Expense(
        recordUuid = recordUuid,
        bucketUuid = bucketUuid,
        amountCents = amountCents,
        description = recordUuid,
        timestamp = epochMillis(expenseDate),
        expenseDate = expenseDate.toString(),
        originInstallId = installId,
        lastModifiedByInstallId = installId,
        createdAtEpochMs = epochMillis(expenseDate),
        updatedAtEpochMs = epochMillis(expenseDate),
        modClock = hybridLogicalClockService.format(epochMillis(expenseDate), 0, installId)
    )

    private fun epochMillis(date: LocalDate): Long {
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private val defaultMigrationDatabaseName = "fund-lifecycle-integration-test"
    private val defaultMigrationInstallId = "fund-lifecycle-install"

    private class TestUserSettingsStore(
        initialSettings: UserSettings = UserSettings()
    ) : UserSettingsStore {
        private val mutableUserSettings = MutableStateFlow(initialSettings)
        private val mutablePendingPaydayUndo = MutableStateFlow<PendingPaydayUndo?>(null)

        override val userSettings: Flow<UserSettings> = mutableUserSettings
        override val pendingPaydayUndo: Flow<PendingPaydayUndo?> = mutablePendingPaydayUndo

        override suspend fun ensureIdentity(): UserSettings {
            if (mutableUserSettings.value.installDeviceId.isBlank()) {
                mutableUserSettings.value = mutableUserSettings.value.copy(
                    installDeviceId = "test-install-id",
                    settingsRecordUuid = UUID.randomUUID().toString(),
                    settingsUpdatedAtEpochMs = 1L,
                    settingsModClock = "0000000000001-0000-test-install-id",
                    settingsLastModifiedByInstallId = "test-install-id"
                )
            }
            return mutableUserSettings.value
        }

        override suspend fun updateCycleSettings(monthlyBudgetCents: Long, paydayDate: Int) {
            mutableUserSettings.value = mutableUserSettings.value.copy(
                monthlyBudgetCents = monthlyBudgetCents,
                paydayDate = paydayDate
            )
        }

        override suspend fun updatePortfolioMonthlyBudget(amountCents: Long?) {
            mutableUserSettings.value = mutableUserSettings.value.copy(portfolioMonthlyBudgetCents = amountCents)
        }

        override suspend fun updateMonthlyBudget(amountCents: Long) {
            mutableUserSettings.value = mutableUserSettings.value.copy(monthlyBudgetCents = amountCents)
        }

        override suspend fun updatePaydayDate(day: Int) {
            mutableUserSettings.value = mutableUserSettings.value.copy(paydayDate = day)
        }

        override suspend fun updateSelectedBucket(selectedBucketUuid: String?) {
            mutableUserSettings.value = mutableUserSettings.value.copy(selectedBucketUuid = selectedBucketUuid)
        }

        override suspend fun updateLastResetTimestamp(timestamp: Long) {
            mutableUserSettings.value = mutableUserSettings.value.copy(lastResetTimestamp = timestamp)
        }

        override suspend fun updateLastSeenDate(date: LocalDate) {
            mutableUserSettings.value = mutableUserSettings.value.copy(lastSeenDate = date.toString())
        }

        override suspend fun completeOnboarding() {
            mutableUserSettings.value = mutableUserSettings.value.copy(isOnboardingCompleted = true)
        }

        override suspend fun setPendingCycle(
            cycleStartDate: LocalDate,
            cycleEndDateExclusive: LocalDate,
            detectedAtTimestamp: Long
        ) {
            mutableUserSettings.value = mutableUserSettings.value.copy(
                pendingCycleStartDate = cycleStartDate.toString(),
                pendingCycleEndDateExclusive = cycleEndDateExclusive.toString(),
                pendingCycleDetectedAtTimestamp = detectedAtTimestamp
            )
        }

        override suspend fun clearPendingCycle() {
            mutableUserSettings.value = mutableUserSettings.value.copy(
                pendingCycleStartDate = null,
                pendingCycleEndDateExclusive = null,
                pendingCycleDetectedAtTimestamp = 0L
            )
        }

        override suspend fun savePendingPaydayUndo(pendingPaydayUndo: PendingPaydayUndo) {
            mutablePendingPaydayUndo.value = pendingPaydayUndo
        }

        override suspend fun clearPendingPaydayUndo() {
            mutablePendingPaydayUndo.value = null
        }

        override suspend fun restoreFromSnapshot(settings: UserSettings, onboardingCompleted: Boolean) {
            mutableUserSettings.value = settings.copy(isOnboardingCompleted = onboardingCompleted)
            mutablePendingPaydayUndo.value = null
        }

        val currentSettings: UserSettings
            get() = mutableUserSettings.value
    }
}
