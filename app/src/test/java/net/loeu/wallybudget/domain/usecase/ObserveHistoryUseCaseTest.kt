package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.domain.model.HomeOverviewState
import net.loeu.wallybudget.domain.model.TimelineLockState
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ObserveHistoryUseCaseTest {

    @Test
    fun invoke_buildsCurrentFutureAndCompletedSections_inDisplayOrder() = runBlocking {
        val expenseDao = FakeExpenseDao(
            listOf(
                expenseEntityOn(4L, LocalDate.of(2026, 3, 10), 1_500L),
                expenseEntityOn(5L, LocalDate.of(2026, 2, 5), 2_500L),
                expenseEntityOn(1L, LocalDate.of(2026, 4, 10), 2_000L),
                expenseEntityOn(2L, LocalDate.of(2026, 4, 12), 3_000L),
                expenseEntityOn(3L, LocalDate.of(2026, 3, 28), 4_000L)
            )
        )
        val historyDao = FakeMonthlyHistoryDao(
            listOf(
                historyEntity(LocalDate.of(2026, 2, 25), LocalDate.of(2026, 3, 25), 50_000L),
                historyEntity(LocalDate.of(2026, 1, 25), LocalDate.of(2026, 2, 25), 60_000L)
            )
        )
        val settingsStore = FakeUserSettingsStore(
            UserSettings(
                monthlyBudgetCents = 100_000L,
                paydayDate = 25,
                lastResetTimestamp = LocalDate.of(2026, 3, 25)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            )
        )
        val budgetCalculationService = BudgetCalculationService()
        val useCase = ObserveHistoryUseCase(
            expenseDao = expenseDao,
            monthlyHistoryDao = historyDao,
            cycleOverviewDao = FakeCycleOverviewDao(expenseDao),
            budgetCalculationService = budgetCalculationService
        )
        val today = LocalDate.of(2026, 4, 10)
        val homeOverviewState = HomeOverviewState(
            effectiveCurrentDate = today,
            budgetState = budgetCalculationService.calculateBudgetState(
                settings = settingsStore.currentSettings,
                now = today,
                totalSpentThisCycleCents = 6_000L,
                spentTodayCents = 2_000L,
                cumulativeSavingsCents = 0L
            ),
            todayExpenses = emptyList(),
            activeCycleExpenseSections = emptyList(),
            pendingCycleCloseoutState = null,
            timelineLockState = TimelineLockState()
        )

        val state = useCase(kotlinx.coroutines.flow.flowOf(homeOverviewState)).first()

        assertEquals(2, state.monthlyHistory.size)
        assertEquals(listOf("Current cycle", "Future-dated expenses", "Feb 25 - Mar 24, 2026", "Jan 25 - Feb 24, 2026"), state.historySections.map { it.title })
        assertEquals(1, state.historySections[2].daySections.size)
        assertEquals(1_500L, state.historySections[2].daySections.single().totalSpentCents)
        assertEquals(1, state.historySections[3].daySections.size)
        assertEquals(2_500L, state.historySections[3].daySections.single().totalSpentCents)
    }
}
