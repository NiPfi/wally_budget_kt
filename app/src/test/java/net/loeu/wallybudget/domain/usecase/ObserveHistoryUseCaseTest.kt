package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
        val useCase = ObserveHistoryUseCase(
            expenseDao = expenseDao,
            monthlyHistoryDao = historyDao,
            cycleOverviewDao = FakeCycleOverviewDao(expenseDao),
            userSettingsStore = settingsStore,
            currentDateProvider = FakeCurrentDateProvider(LocalDate.of(2026, 4, 10)),
            budgetCalculationService = BudgetCalculationService()
        )

        val state = useCase().first()

        assertEquals(2, state.monthlyHistory.size)
        assertEquals(listOf("Current cycle", "Future-dated expenses", "Feb 25 - Mar 24, 2026", "Jan 25 - Feb 24, 2026"), state.historySections.map { it.title })
    }
}
