@file:Suppress("LongMethod", "MaxLineLength")

package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.data.local.entity.BucketAllocationAdjustmentEntity
import net.loeu.wallybudget.data.local.entity.BucketAllocationPolicyEntity
import net.loeu.wallybudget.data.local.entity.toDomainModel
import net.loeu.wallybudget.domain.model.DEFAULT_FUND_UUID
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BucketAllocationResolver
import net.loeu.wallybudget.domain.service.BudgetAdjustmentResolver
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate

class ConcludePendingCycleUseCaseTest {

    @Test
    fun invoke_archivesPendingCycleAndClearsPreferences() = runBlocking {
        val transactionRunner = FakeTransactionRunner()
        val expenseDao = FakeExpenseDao(
            listOf(
                expenseEntityOn(1L, LocalDate.of(2026, 3, 28), 3_000L),
                expenseEntityOn(2L, LocalDate.of(2026, 4, 4), 4_000L)
            )
        )
        val budgetPolicyDao = FakeBudgetPolicyDao()
        val budgetAdjustmentDao = FakeBudgetAdjustmentDao()
        val historyDao = FakeMonthlyHistoryDao()
        val bucketAllocationPolicyDao = FakeBucketAllocationPolicyDao()
        val bucketAllocationAdjustmentDao = FakeBucketAllocationAdjustmentDao()
        val bucketHistoryDao = FakeBucketMonthlyHistoryDao()
        val fundDao = FakeFundDao()
        val fundTransactionDao = FakeFundTransactionDao()
        val settingsStore = FakeUserSettingsStore()
        val budgetCalculationService = BudgetCalculationService()
        val useCase = ConcludePendingCycleUseCase(
            transactionRunner = transactionRunner,
            expenseDao = expenseDao,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            budgetBucketDao = FakeBudgetBucketDao(),
            monthlyHistoryDao = historyDao,
            bucketAllocationAdjustmentDao = bucketAllocationAdjustmentDao,
            bucketCycleBaselineDao = FakeBucketCycleBaselineDao(),
            fundDao = fundDao,
            fundTransactionDao = fundTransactionDao,
            userSettingsStore = settingsStore,
            budgetCalculationService = budgetCalculationService,
            cycleScheduleResolver = CycleScheduleResolver(budgetCalculationService),
            budgetAdjustmentResolver = BudgetAdjustmentResolver(),
            bucketAllocationResolver = BucketAllocationResolver(),
            hybridLogicalClockService = HybridLogicalClockService(),
            rebuildBucketMonthlyHistoryUseCase = RebuildBucketMonthlyHistoryUseCase(
                bucketAllocationPolicyDao = bucketAllocationPolicyDao,
                bucketAllocationAdjustmentDao = bucketAllocationAdjustmentDao,
                expenseDao = expenseDao,
                bucketMonthlyHistoryDao = bucketHistoryDao,
                budgetCalculationService = budgetCalculationService,
                bucketAllocationResolver = BucketAllocationResolver()
            )
        )
        val settings = UserSettings(
            monthlyBudgetCents = 100_000L,
            paydayDate = 25,
            pendingCycleStartDate = "2026-03-25",
            pendingCycleEndDateExclusive = "2026-04-25"
        )

        useCase(settings)

        assertEquals(1, transactionRunner.transactionCount)
        assertEquals(1, historyDao.currentHistory.size)
        assertNull(settingsStore.currentSettings.pendingCycleStartDate)
        assertEquals(1, settingsStore.clearPendingCount)
    }

    @Test
    fun invoke_depositsAdjustedSurplusIntoDefaultFundWhenAllocationsAreZero() = runBlocking {
        val pendingCycleStart = LocalDate.of(2026, 3, 25)
        val pendingCycleEnd = LocalDate.of(2026, 4, 25)
        val groceriesExpense = expenseEntityOn(1L, pendingCycleStart.plusDays(2), 20_00L).copy(bucketUuid = "groceries")
        val fundDao = defaultFundDao(initialBalanceCents = 10_00L)
        val fundTransactionDao = FakeFundTransactionDao()
        val bucketAllocationAdjustmentDao = bucketAllocationAdjustmentDao(pendingCycleStart)
        val useCase = concludePendingCycleUseCase(
            pendingCycleStart = pendingCycleStart,
            pendingCycleEnd = pendingCycleEnd,
            expenses = listOf(groceriesExpense),
            budgetBucketDao = FakeBudgetBucketDao(
                listOf(bucketEntity(bucketUuid = "groceries", name = "Groceries", defaultAllocatedAmountCents = 0L))
            ),
            bucketCycleBaselineDao = FakeBucketCycleBaselineDao(
                listOf(
                    bucketCycleBaselineEntity(
                        bucketUuid = "groceries",
                        cycleStartDate = pendingCycleStart.toString(),
                        cycleEndDateExclusive = pendingCycleEnd.toString(),
                        baselineAmountCents = 30_00L
                    )
                )
            ),
            bucketAllocationAdjustmentDao = bucketAllocationAdjustmentDao,
            fundDao = fundDao,
            fundTransactionDao = fundTransactionDao
        )

        useCase(
            UserSettings(
                monthlyBudgetCents = 100_000L,
                paydayDate = 25,
                pendingCycleStartDate = pendingCycleStart.toString(),
                pendingCycleEndDateExclusive = pendingCycleEnd.toString()
            )
        )

        val updatedFund = fundDao.findByUuid(DEFAULT_FUND_UUID)
        val expectedDepositAmount = 30_00L - 20_00L
        assertEquals(10_00L + expectedDepositAmount, updatedFund?.balanceCents)
        assertEquals("test-install-id", updatedFund?.lastModifiedByInstallId)
        assertEquals(1, fundTransactionDao.currentTransactions.size)
        assertEquals(expectedDepositAmount, fundTransactionDao.currentTransactions.single().amountCents)
    }

    @Test
    fun invoke_netsSurplusAcrossBucketsBeforeDepositingToFund() = runBlocking {
        val pendingCycleStart = LocalDate.of(2026, 3, 25)
        val pendingCycleEnd = LocalDate.of(2026, 4, 25)
        val fundDao = defaultFundDao(initialBalanceCents = 10_00L)
        val fundTransactionDao = FakeFundTransactionDao()
        val useCase = concludePendingCycleUseCase(
            pendingCycleStart = pendingCycleStart,
            pendingCycleEnd = pendingCycleEnd,
            expenses = listOf(
                expenseEntityOn(1L, pendingCycleStart.plusDays(1), 60_00L).copy(bucketUuid = "groceries"),
                expenseEntityOn(2L, pendingCycleStart.plusDays(2), 5_00L).copy(bucketUuid = "fun")
            ),
            budgetBucketDao = FakeBudgetBucketDao(
                listOf(
                    bucketEntity(bucketUuid = "groceries", name = "Groceries", defaultAllocatedAmountCents = 0L),
                    bucketEntity(id = 2L, bucketUuid = "fun", name = "Fun", defaultAllocatedAmountCents = 0L, sortOrder = 1)
                )
            ),
            bucketCycleBaselineDao = FakeBucketCycleBaselineDao(
                listOf(
                    bucketCycleBaselineEntity(
                        bucketUuid = "groceries",
                        cycleStartDate = pendingCycleStart.toString(),
                        cycleEndDateExclusive = pendingCycleEnd.toString(),
                        baselineAmountCents = 50_00L
                    ),
                    bucketCycleBaselineEntity(
                        id = 2L,
                        baselineUuid = "fun-baseline",
                        bucketUuid = "fun",
                        cycleStartDate = pendingCycleStart.toString(),
                        cycleEndDateExclusive = pendingCycleEnd.toString(),
                        baselineAmountCents = 20_00L
                    )
                )
            ),
            bucketAllocationAdjustmentDao = FakeBucketAllocationAdjustmentDao(),
            fundDao = fundDao,
            fundTransactionDao = fundTransactionDao
        )

        useCase(
            UserSettings(
                monthlyBudgetCents = 100_000L,
                paydayDate = 25,
                pendingCycleStartDate = pendingCycleStart.toString(),
                pendingCycleEndDateExclusive = pendingCycleEnd.toString()
            )
        )

        assertEquals(1, fundTransactionDao.currentTransactions.size)
        assertEquals(5_00L, fundTransactionDao.currentTransactions.single().amountCents)
        assertEquals(15_00L, fundDao.findByUuid(DEFAULT_FUND_UUID)?.balanceCents)
    }

    @Test
    fun invoke_finalizesSettledClosingBucketsAtRollover() = runBlocking {
        val pendingCycleStart = LocalDate.of(2026, 3, 25)
        val pendingCycleEnd = LocalDate.of(2026, 4, 25)
        val budgetBucketDao = FakeBudgetBucketDao(
            listOf(
                bucketEntity(
                    bucketUuid = "bills",
                    name = "Bills",
                    defaultAllocatedAmountCents = 200_00L,
                    settledCloseCycleEndDateExclusive = pendingCycleEnd.toString()
                )
            )
        )
        val useCase = ConcludePendingCycleUseCase(
            transactionRunner = FakeTransactionRunner(),
            expenseDao = FakeExpenseDao(),
            budgetPolicyDao = FakeBudgetPolicyDao(listOf(budgetPolicyEntity(1L, pendingCycleStart, pendingCycleEnd))),
            budgetAdjustmentDao = FakeBudgetAdjustmentDao(),
            budgetBucketDao = budgetBucketDao,
            bucketAllocationAdjustmentDao = FakeBucketAllocationAdjustmentDao(),
            bucketCycleBaselineDao = FakeBucketCycleBaselineDao(),
            monthlyHistoryDao = FakeMonthlyHistoryDao(),
            fundDao = FakeFundDao(),
            fundTransactionDao = FakeFundTransactionDao(),
            userSettingsStore = FakeUserSettingsStore(),
            budgetCalculationService = BudgetCalculationService(),
            cycleScheduleResolver = CycleScheduleResolver(BudgetCalculationService()),
            budgetAdjustmentResolver = BudgetAdjustmentResolver(),
            bucketAllocationResolver = BucketAllocationResolver(),
            hybridLogicalClockService = HybridLogicalClockService(),
            rebuildBucketMonthlyHistoryUseCase = RebuildBucketMonthlyHistoryUseCase(
                bucketAllocationPolicyDao = FakeBucketAllocationPolicyDao(),
                bucketAllocationAdjustmentDao = FakeBucketAllocationAdjustmentDao(),
                expenseDao = FakeExpenseDao(),
                bucketMonthlyHistoryDao = FakeBucketMonthlyHistoryDao(),
                budgetCalculationService = BudgetCalculationService(),
                bucketAllocationResolver = BucketAllocationResolver()
            )
        )

        useCase(
            UserSettings(
                monthlyBudgetCents = 100_000L,
                paydayDate = 25,
                pendingCycleStartDate = pendingCycleStart.toString(),
                pendingCycleEndDateExclusive = pendingCycleEnd.toString()
            )
        )

        val updatedBucket = budgetBucketDao.findByBucketUuid("bills")
        assertNotNull(updatedBucket?.closedAtEpochMs)
        assertNull(updatedBucket?.settledCloseCycleEndDateExclusive)
    }

    private fun defaultFundDao(initialBalanceCents: Long): FakeFundDao {
        return FakeFundDao(
            listOf(
                fundEntity(
                    uuid = DEFAULT_FUND_UUID,
                    balanceCents = initialBalanceCents,
                    allocationPerCycleCents = 0L,
                    updatedAtEpochMs = 1L,
                    modClock = "0000000000001-0000-test-install-id"
                )
            )
        )
    }

    private fun bucketAllocationPolicyDao(
        pendingCycleStart: LocalDate,
        pendingCycleEnd: LocalDate
    ): FakeBucketAllocationPolicyDao {
        return FakeBucketAllocationPolicyDao(
            listOf(
                BucketAllocationPolicyEntity(
                    id = 1L,
                    allocationUuid = "alloc-1",
                    bucketUuid = "groceries",
                    cycleStartDate = pendingCycleStart.toString(),
                    cycleEndDateExclusive = pendingCycleEnd.toString(),
                    allocatedAmountCents = 30_00L,
                    originInstallId = "test-install-id",
                    lastModifiedByInstallId = "test-install-id",
                    createdAtEpochMs = 1L,
                    updatedAtEpochMs = 1L,
                    modClock = "0000000000001-0000-test-install-id"
                )
            )
        )
    }

    private fun bucketAllocationAdjustmentDao(pendingCycleStart: LocalDate): FakeBucketAllocationAdjustmentDao {
        return FakeBucketAllocationAdjustmentDao(
            listOf(
                BucketAllocationAdjustmentEntity(
                    id = 1L,
                    adjustmentUuid = "adj-1",
                    bucketUuid = "groceries",
                    cycleStartDate = pendingCycleStart.toString(),
                    effectiveDate = pendingCycleStart.plusDays(1).toString(),
                    previousAllocatedAmountCents = 30_00L,
                    newAllocatedAmountCents = 50_00L,
                    originInstallId = "test-install-id",
                    lastModifiedByInstallId = "test-install-id",
                    createdAtEpochMs = 2L,
                    updatedAtEpochMs = 2L,
                    modClock = "0000000000002-0000-test-install-id"
                )
            )
        )
    }

    private fun concludePendingCycleUseCase(
        pendingCycleStart: LocalDate,
        pendingCycleEnd: LocalDate,
        expenses: List<net.loeu.wallybudget.data.local.entity.ExpenseEntity>,
        budgetBucketDao: FakeBudgetBucketDao = FakeBudgetBucketDao(),
        bucketCycleBaselineDao: FakeBucketCycleBaselineDao = FakeBucketCycleBaselineDao(),
        bucketAllocationAdjustmentDao: FakeBucketAllocationAdjustmentDao,
        fundDao: FakeFundDao,
        fundTransactionDao: FakeFundTransactionDao
    ): ConcludePendingCycleUseCase {
        val budgetCalculationService = BudgetCalculationService()
        return ConcludePendingCycleUseCase(
            transactionRunner = FakeTransactionRunner(),
            expenseDao = FakeExpenseDao(expenses),
            budgetPolicyDao = FakeBudgetPolicyDao(listOf(budgetPolicyEntity(1L, pendingCycleStart, pendingCycleEnd))),
            budgetAdjustmentDao = FakeBudgetAdjustmentDao(),
            budgetBucketDao = budgetBucketDao,
            bucketCycleBaselineDao = bucketCycleBaselineDao,
            bucketAllocationAdjustmentDao = bucketAllocationAdjustmentDao,
            monthlyHistoryDao = FakeMonthlyHistoryDao(),
            fundDao = fundDao,
            fundTransactionDao = fundTransactionDao,
            userSettingsStore = FakeUserSettingsStore(),
            budgetCalculationService = budgetCalculationService,
            cycleScheduleResolver = CycleScheduleResolver(budgetCalculationService),
            budgetAdjustmentResolver = BudgetAdjustmentResolver(),
            bucketAllocationResolver = BucketAllocationResolver(),
            hybridLogicalClockService = HybridLogicalClockService(),
            rebuildBucketMonthlyHistoryUseCase = RebuildBucketMonthlyHistoryUseCase(
                bucketAllocationPolicyDao = FakeBucketAllocationPolicyDao(),
                bucketAllocationAdjustmentDao = bucketAllocationAdjustmentDao,
                expenseDao = FakeExpenseDao(expenses),
                bucketMonthlyHistoryDao = FakeBucketMonthlyHistoryDao(),
                budgetCalculationService = budgetCalculationService,
                bucketAllocationResolver = BucketAllocationResolver()
            )
        )
    }
}
