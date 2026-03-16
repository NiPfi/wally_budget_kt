package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.domain.model.UserSettings
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
        val historyDao = FakeMonthlyHistoryDao(
            listOf(
                historyEntity(LocalDate.of(2026, 2, 25), LocalDate.of(2026, 3, 25), 80_000L)
            )
        )
        val budgetPolicyDao = FakeBudgetPolicyDao()
        val budgetAdjustmentDao = FakeBudgetAdjustmentDao()
        val settingsStore = FakeUserSettingsStore(
            UserSettings(
                monthlyBudgetCents = 100_000L,
                paydayDate = 25,
                lastResetTimestamp = LocalDate.of(2026, 3, 25)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                pendingCycleStartDate = "2026-03-25",
                pendingCycleEndDateExclusive = "2026-04-25"
            )
        )
        val budgetCalculationService = BudgetCalculationService()
        val useCase = ObserveHomeOverviewUseCase(
            expenseDao = expenseDao,
            monthlyHistoryDao = historyDao,
            cycleOverviewDao = FakeCycleOverviewDao(expenseDao),
            budgetAdjustmentDao = budgetAdjustmentDao,
            budgetPolicyDao = budgetPolicyDao,
            userSettingsStore = settingsStore,
            currentDateProvider = FakeCurrentDateProvider(LocalDate.of(2026, 4, 10)),
            budgetCalculationService = budgetCalculationService,
            cycleScheduleResolver = CycleScheduleResolver(budgetCalculationService),
            budgetAdjustmentResolver = BudgetAdjustmentResolver()
        )

        val state = useCase().first()

        assertEquals(LocalDate.of(2026, 4, 10), state.effectiveCurrentDate)
        assertEquals(2_000L, state.todayExpenses.single().amountCents)
        assertEquals(LocalDate.of(2026, 3, 25), state.budgetState.cycleStartDate)
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
        val historyDao = FakeMonthlyHistoryDao()
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
        val settingsStore = FakeUserSettingsStore(
            UserSettings(
                monthlyBudgetCents = 120_000L,
                paydayDate = 25,
                lastResetTimestamp = LocalDate.of(2026, 4, 25)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                pendingCycleStartDate = "2026-03-25",
                pendingCycleEndDateExclusive = "2026-04-25"
            )
        )
        val budgetCalculationService = BudgetCalculationService()
        val useCase = ObserveHomeOverviewUseCase(
            expenseDao = expenseDao,
            monthlyHistoryDao = historyDao,
            cycleOverviewDao = FakeCycleOverviewDao(expenseDao),
            budgetAdjustmentDao = budgetAdjustmentDao,
            budgetPolicyDao = budgetPolicyDao,
            userSettingsStore = settingsStore,
            currentDateProvider = FakeCurrentDateProvider(LocalDate.of(2026, 4, 26)),
            budgetCalculationService = budgetCalculationService,
            cycleScheduleResolver = CycleScheduleResolver(budgetCalculationService),
            budgetAdjustmentResolver = BudgetAdjustmentResolver()
        )

        val state = useCase().first()
        val pendingCloseout = requireNotNull(state.pendingCycleCloseoutState)

        assertEquals(109_678L, pendingCloseout.budgetAmountCents)
        assertEquals(99_678L, pendingCloseout.surplusCents)
    }
}
