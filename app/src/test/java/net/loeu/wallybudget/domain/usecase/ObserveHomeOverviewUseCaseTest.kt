package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BudgetCalculationService
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
        val useCase = ObserveHomeOverviewUseCase(
            expenseDao = expenseDao,
            monthlyHistoryDao = historyDao,
            cycleOverviewDao = FakeCycleOverviewDao(expenseDao),
            budgetPolicyDao = budgetPolicyDao,
            userSettingsStore = settingsStore,
            currentDateProvider = FakeCurrentDateProvider(LocalDate.of(2026, 4, 10)),
            budgetCalculationService = BudgetCalculationService()
        )

        val state = useCase().first()

        assertEquals(LocalDate.of(2026, 4, 10), state.effectiveCurrentDate)
        assertEquals(2_000L, state.todayExpenses.single().amountCents)
        assertEquals(LocalDate.of(2026, 3, 25), state.budgetState.cycleStartDate)
        assertTrue(state.timelineLockState.isLocked)
        assertEquals(17, state.activeCycleExpenseSections.size)
        assertNotNull(state.pendingCycleCloseoutState)
    }
}
