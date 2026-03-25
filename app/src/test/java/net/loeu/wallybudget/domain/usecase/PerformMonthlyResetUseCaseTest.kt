package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BucketAllocationResolver
import net.loeu.wallybudget.domain.service.BudgetAdjustmentResolver
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PerformMonthlyResetUseCaseTest {

    private val budgetCalculationService = BudgetCalculationService()
    private val cycleScheduleResolver = CycleScheduleResolver(budgetCalculationService)
    private val budgetAdjustmentResolver = BudgetAdjustmentResolver()

    private fun bucketHistoryRebuildUseCase(expenseDao: FakeExpenseDao): RebuildBucketMonthlyHistoryUseCase {
        return RebuildBucketMonthlyHistoryUseCase(
            bucketAllocationPolicyDao = FakeBucketAllocationPolicyDao(),
            bucketAllocationAdjustmentDao = FakeBucketAllocationAdjustmentDao(),
            expenseDao = expenseDao,
            bucketMonthlyHistoryDao = FakeBucketMonthlyHistoryDao(),
            budgetCalculationService = budgetCalculationService,
            bucketAllocationResolver = BucketAllocationResolver()
        )
    }

    @Test
    fun invoke_recoversMissingPendingCycle_whenPreviousCycleHasExpensesButNoArchive() = runBlocking {
        val transactionRunner = FakeTransactionRunner()
        val expenseDao = FakeExpenseDao(
            listOf(
                expenseEntityOn(1L, LocalDate.of(2026, 3, 20), 4_000L)
            )
        )
        val budgetPolicyDao = FakeBudgetPolicyDao()
        val budgetAdjustmentDao = FakeBudgetAdjustmentDao()
        val historyDao = FakeMonthlyHistoryDao()
        val userSettingsStore = FakeUserSettingsStore()
        val useCase = PerformMonthlyResetUseCase(
            transactionRunner = transactionRunner,
            expenseDao = expenseDao,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            budgetBucketDao = FakeBudgetBucketDao(),
            monthlyHistoryDao = historyDao,
            userSettingsStore = userSettingsStore,
            budgetCalculationService = budgetCalculationService,
            cycleScheduleResolver = cycleScheduleResolver,
            budgetAdjustmentResolver = budgetAdjustmentResolver,
            rebuildBucketMonthlyHistoryUseCase = bucketHistoryRebuildUseCase(expenseDao),
            hybridLogicalClockService = HybridLogicalClockService()
        )
        val settings = UserSettings(
            monthlyBudgetCents = 100_000L,
            paydayDate = 25,
            lastResetTimestamp = dateMillis(LocalDate.of(2026, 3, 25))
        )

        useCase(settings, LocalDate.of(2026, 4, 10))

        assertEquals("2026-02-25", userSettingsStore.currentSettings.pendingCycleStartDate)
        assertEquals("2026-03-25", userSettingsStore.currentSettings.pendingCycleEndDateExclusive)
        assertEquals(0, historyDao.currentHistory.size)
        assertEquals(0, transactionRunner.transactionCount)
    }

    @Test
    fun invoke_archivesFullyEndedCycles_andSetsLatestEndedCyclePending() = runBlocking {
        val transactionRunner = FakeTransactionRunner()
        val expenseDao = FakeExpenseDao(
            listOf(
                expenseEntityOn(1L, LocalDate.of(2026, 2, 26), 5_000L),
                expenseEntityOn(2L, LocalDate.of(2026, 3, 5), 6_000L),
                expenseEntityOn(3L, LocalDate.of(2026, 3, 28), 7_000L),
                expenseEntityOn(4L, LocalDate.of(2026, 4, 8), 8_000L)
            )
        )
        val budgetPolicyDao = FakeBudgetPolicyDao()
        val budgetAdjustmentDao = FakeBudgetAdjustmentDao()
        val historyDao = FakeMonthlyHistoryDao()
        val userSettingsStore = FakeUserSettingsStore()
        val useCase = PerformMonthlyResetUseCase(
            transactionRunner = transactionRunner,
            expenseDao = expenseDao,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            budgetBucketDao = FakeBudgetBucketDao(),
            monthlyHistoryDao = historyDao,
            userSettingsStore = userSettingsStore,
            budgetCalculationService = budgetCalculationService,
            cycleScheduleResolver = cycleScheduleResolver,
            budgetAdjustmentResolver = budgetAdjustmentResolver,
            rebuildBucketMonthlyHistoryUseCase = bucketHistoryRebuildUseCase(expenseDao),
            hybridLogicalClockService = HybridLogicalClockService()
        )
        val settings = UserSettings(
            monthlyBudgetCents = 100_000L,
            paydayDate = 25,
            lastResetTimestamp = dateMillis(LocalDate.of(2026, 2, 25))
        )

        useCase(settings, LocalDate.of(2026, 4, 26))

        assertEquals(1, transactionRunner.transactionCount)
        assertEquals(1, historyDao.currentHistory.size)
        assertEquals("2026-02-25", historyDao.currentHistory.single().cycleStartDate)
        assertEquals("2026-03-25", userSettingsStore.currentSettings.pendingCycleStartDate)
        assertEquals("2026-04-25", userSettingsStore.currentSettings.pendingCycleEndDateExclusive)
        assertEquals(dateMillis(LocalDate.of(2026, 4, 25)), userSettingsStore.currentSettings.lastResetTimestamp)
    }

    @Test
    fun invoke_clearsAndArchivesStalePendingCycle_beforeSettingNewPendingCycle() = runBlocking {
        val transactionRunner = FakeTransactionRunner()
        val expenseDao = FakeExpenseDao(
            listOf(
                expenseEntityOn(1L, LocalDate.of(2026, 2, 28), 5_000L),
                expenseEntityOn(2L, LocalDate.of(2026, 3, 10), 6_000L),
                expenseEntityOn(3L, LocalDate.of(2026, 3, 30), 7_000L)
            )
        )
        val budgetPolicyDao = FakeBudgetPolicyDao()
        val budgetAdjustmentDao = FakeBudgetAdjustmentDao()
        val historyDao = FakeMonthlyHistoryDao()
        val userSettingsStore = FakeUserSettingsStore()
        val useCase = PerformMonthlyResetUseCase(
            transactionRunner = transactionRunner,
            expenseDao = expenseDao,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            budgetBucketDao = FakeBudgetBucketDao(),
            monthlyHistoryDao = historyDao,
            userSettingsStore = userSettingsStore,
            budgetCalculationService = budgetCalculationService,
            cycleScheduleResolver = cycleScheduleResolver,
            budgetAdjustmentResolver = budgetAdjustmentResolver,
            rebuildBucketMonthlyHistoryUseCase = bucketHistoryRebuildUseCase(expenseDao),
            hybridLogicalClockService = HybridLogicalClockService()
        )
        val settings = UserSettings(
            monthlyBudgetCents = 100_000L,
            paydayDate = 25,
            lastResetTimestamp = dateMillis(LocalDate.of(2026, 2, 25)),
            pendingCycleStartDate = "2026-02-25",
            pendingCycleEndDateExclusive = "2026-03-25",
            pendingCycleDetectedAtTimestamp = 123L
        )

        useCase(settings, LocalDate.of(2026, 4, 26))

        assertEquals(1, userSettingsStore.clearPendingCount)
        assertNotNull(historyDao.findByCycleStart("2026-02-25"))
        assertEquals("2026-03-25", userSettingsStore.currentSettings.pendingCycleStartDate)
    }

    @Test
    fun invoke_doesNotStampPoliciesForSettledClosingBuckets() = runBlocking {
        val bucketCycleBaselineDao = FakeBucketCycleBaselineDao()
        val budgetBucketDao = FakeBudgetBucketDao(
            listOf(
                bucketEntity(bucketUuid = "open", name = "Open", defaultAllocatedAmountCents = 80_000L),
                bucketEntity(
                    id = 2L,
                    bucketUuid = "closing",
                    name = "Closing",
                    defaultAllocatedAmountCents = 20_000L,
                    sortOrder = 1,
                    settledCloseCycleEndDateExclusive = "2026-04-25"
                )
            )
        )
        val useCase = PerformMonthlyResetUseCase(
            transactionRunner = FakeTransactionRunner(),
            expenseDao = FakeExpenseDao(),
            budgetPolicyDao = FakeBudgetPolicyDao(),
            budgetAdjustmentDao = FakeBudgetAdjustmentDao(),
            budgetBucketDao = budgetBucketDao,
            bucketCycleBaselineDao = bucketCycleBaselineDao,
            monthlyHistoryDao = FakeMonthlyHistoryDao(),
            userSettingsStore = FakeUserSettingsStore(),
            budgetCalculationService = budgetCalculationService,
            cycleScheduleResolver = cycleScheduleResolver,
            budgetAdjustmentResolver = budgetAdjustmentResolver,
            rebuildBucketMonthlyHistoryUseCase = bucketHistoryRebuildUseCase(FakeExpenseDao()),
            hybridLogicalClockService = HybridLogicalClockService()
        )

        useCase(
            UserSettings(
                monthlyBudgetCents = 100_000L,
                paydayDate = 25,
                lastResetTimestamp = dateMillis(LocalDate.of(2026, 3, 25))
            ),
            LocalDate.of(2026, 4, 26)
        )

        assertNotNull(bucketCycleBaselineDao.findActiveBaselineForCycle("open", "2026-04-25"))
        assertNull(bucketCycleBaselineDao.findActiveBaselineForCycle("closing", "2026-04-25"))
    }

    private fun dateMillis(date: LocalDate): Long {
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
