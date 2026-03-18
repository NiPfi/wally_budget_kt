package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.data.local.entity.BucketMonthlyHistoryEntity
import net.loeu.wallybudget.data.local.entity.BudgetBucketEntity
import net.loeu.wallybudget.domain.model.BucketBalanceBehavior
import net.loeu.wallybudget.domain.model.BucketTrackingMode
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_NAME
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BucketAllocationResolver
import net.loeu.wallybudget.domain.service.BudgetAdjustmentResolver
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ObserveHomeOverviewUseCaseTest {

    @Test
    fun invoke_combinesBudgetState_todayExpenses_sections_andPendingCloseout() = runBlocking {
        val expenseDao = FakeExpenseDao(
            listOf(
                expenseEntityOn(1L, LocalDate.of(2026, 4, 10), 2_000L),
                expenseEntityOn(2L, LocalDate.of(2026, 4, 9), 3_000L),
                expenseEntityOn(3L, LocalDate.of(2026, 4, 12), 4_000L)
            )
        )
        val bucketHistoryDao = FakeBucketMonthlyHistoryDao(
            listOf(
                BucketMonthlyHistoryEntity(
                    bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                    cycleStartDate = "2026-02-25",
                    budgetAmountCents = 100_000L,
                    totalSpentCents = 80_000L,
                    surplusCents = 20_000L,
                    cycleEndDate = "2026-03-25",
                    endTimestamp = LocalDate.of(2026, 3, 25)
                        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                )
            )
        )
        val useCase = createUseCase(
            expenseDao = expenseDao,
            settingsStore = FakeUserSettingsStore(
                userSettings(
                    monthlyBudgetCents = 100_000L,
                    portfolioMonthlyBudgetCents = 400_000L,
                    lastResetDate = LocalDate.of(2026, 3, 25),
                    pendingCycleStartDate = "2026-03-25",
                    pendingCycleEndDateExclusive = "2026-04-25"
                )
            ),
            currentDate = LocalDate.of(2026, 4, 10),
            budgetBucketDao = spendingBucketDao(defaultAllocatedAmountCents = 100_000L),
            bucketHistoryDao = bucketHistoryDao
        )

        val state = useCase().first()

        assertEquals(LocalDate.of(2026, 4, 10), state.effectiveCurrentDate)
        assertEquals(2_000L, state.todayExpenses.single().amountCents)
        assertEquals(LocalDate.of(2026, 3, 25), state.budgetState.cycleStartDate)
        assertEquals(100_000L, state.budgetState.monthlyBudgetCents)
        assertTrue(state.timelineLockState.isLocked)
        assertEquals(17, state.activeCycleExpenseSections.size)
        assertNotNull(state.pendingCycleCloseoutState)
    }

    @Test
    fun invoke_appliesBudgetAdjustmentsToPendingCloseoutTotals() = runBlocking {
        val expenseDao = FakeExpenseDao(
            listOf(
                expenseEntityOn(1L, LocalDate.of(2026, 3, 28), 6_000L),
                expenseEntityOn(2L, LocalDate.of(2026, 4, 5), 4_000L)
            )
        )
        val budgetPolicyDao = FakeBudgetPolicyDao(
            listOf(
                budgetPolicyEntity(
                    id = 1L,
                    cycleStart = LocalDate.of(2026, 3, 25),
                    cycleEndExclusive = LocalDate.of(2026, 4, 25),
                    budgetAmountCents = 100_000L
                )
            )
        )
        val budgetAdjustmentDao = FakeBudgetAdjustmentDao(
            listOf(
                budgetAdjustmentEntity(
                    id = 1L,
                    cycleStart = LocalDate.of(2026, 3, 25),
                    effectiveDate = LocalDate.of(2026, 4, 10),
                    previousMonthlyBudgetCents = 100_000L,
                    newMonthlyBudgetCents = 120_000L
                )
            )
        )
        val useCase = createUseCase(
            expenseDao = expenseDao,
            settingsStore = FakeUserSettingsStore(
                userSettings(
                    monthlyBudgetCents = 120_000L,
                    lastResetDate = LocalDate.of(2026, 4, 25),
                    pendingCycleStartDate = "2026-03-25",
                    pendingCycleEndDateExclusive = "2026-04-25"
                )
            ),
            currentDate = LocalDate.of(2026, 4, 26),
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            budgetBucketDao = spendingBucketDao(defaultAllocatedAmountCents = 100_000L)
        )

        val state = useCase().first()
        val pendingCloseout = requireNotNull(state.pendingCycleCloseoutState)

        assertEquals(109_678L, pendingCloseout.budgetAmountCents)
        assertEquals(99_678L, pendingCloseout.surplusCents)
    }

    private fun createUseCase(
        expenseDao: FakeExpenseDao,
        settingsStore: FakeUserSettingsStore,
        currentDate: LocalDate,
        budgetPolicyDao: FakeBudgetPolicyDao = FakeBudgetPolicyDao(),
        budgetAdjustmentDao: FakeBudgetAdjustmentDao = FakeBudgetAdjustmentDao(),
        budgetBucketDao: FakeBudgetBucketDao = spendingBucketDao(),
        bucketAllocationPolicyDao: FakeBucketAllocationPolicyDao = FakeBucketAllocationPolicyDao(),
        bucketAllocationAdjustmentDao: FakeBucketAllocationAdjustmentDao = FakeBucketAllocationAdjustmentDao(),
        bucketHistoryDao: FakeBucketMonthlyHistoryDao = FakeBucketMonthlyHistoryDao()
    ): ObserveHomeOverviewUseCase {
        val budgetCalculationService = BudgetCalculationService()
        return ObserveHomeOverviewUseCase(
            expenseDao = expenseDao,
            cycleOverviewDao = FakeCycleOverviewDao(expenseDao),
            budgetAdjustmentDao = budgetAdjustmentDao,
            budgetPolicyDao = budgetPolicyDao,
            budgetBucketDao = budgetBucketDao,
            bucketAllocationPolicyDao = bucketAllocationPolicyDao,
            bucketAllocationAdjustmentDao = bucketAllocationAdjustmentDao,
            bucketMonthlyHistoryDao = bucketHistoryDao,
            userSettingsStore = settingsStore,
            currentDateProvider = FakeCurrentDateProvider(currentDate),
            budgetCalculationService = budgetCalculationService,
            cycleScheduleResolver = CycleScheduleResolver(budgetCalculationService),
            budgetAdjustmentResolver = BudgetAdjustmentResolver(),
            bucketAllocationResolver = BucketAllocationResolver()
        )
    }

    private fun spendingBucketDao(defaultAllocatedAmountCents: Long = 100_000L): FakeBudgetBucketDao {
        return FakeBudgetBucketDao(listOf(spendingBucketEntity(defaultAllocatedAmountCents)))
    }

    private fun spendingBucketEntity(defaultAllocatedAmountCents: Long): BudgetBucketEntity {
        return BudgetBucketEntity(
            bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
            name = DEFAULT_SPENDING_BUCKET_NAME,
            trackingMode = BucketTrackingMode.DAILY_TARGET,
            balanceBehavior = BucketBalanceBehavior.RETURN_TO_PORTFOLIO,
            defaultAllocatedAmountCents = defaultAllocatedAmountCents,
            sortOrder = 0,
            isPrimary = true,
            originInstallId = "test-install-id",
            lastModifiedByInstallId = "test-install-id",
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
            modClock = "0000000000001-0000-test-install-id"
        )
    }

    private fun userSettings(
        monthlyBudgetCents: Long,
        lastResetDate: LocalDate,
        pendingCycleStartDate: String,
        pendingCycleEndDateExclusive: String,
        portfolioMonthlyBudgetCents: Long? = null
    ): UserSettings {
        return UserSettings(
            monthlyBudgetCents = monthlyBudgetCents,
            portfolioMonthlyBudgetCents = portfolioMonthlyBudgetCents,
            paydayDate = 25,
            primaryBucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
            selectedBucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
            lastResetTimestamp = lastResetDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            pendingCycleStartDate = pendingCycleStartDate,
            pendingCycleEndDateExclusive = pendingCycleEndDateExclusive
        )
    }
}
