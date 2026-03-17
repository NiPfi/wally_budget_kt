package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BudgetAdjustmentResolver
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ObserveForecastUseCaseTest {

    @Test
    fun invoke_usesRecentExpensesAndHistoryToProduceForecast() = runBlocking {
        val expenseDao = FakeExpenseDao(
            listOf(
                expenseEntityOn(1L, LocalDate.of(2026, 3, 26), 4_000L),
                expenseEntityOn(2L, LocalDate.of(2026, 4, 1), 5_000L),
                expenseEntityOn(3L, LocalDate.of(2026, 4, 9), 6_000L),
                expenseEntityOn(4L, LocalDate.of(2026, 4, 10), 2_000L)
            )
        )
        val historyDao = FakeMonthlyHistoryDao(
            listOf(
                historyEntity(LocalDate.of(2026, 2, 25), LocalDate.of(2026, 3, 25), 80_000L)
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
        val budgetPolicyDao = FakeBudgetPolicyDao()
        val budgetAdjustmentDao = FakeBudgetAdjustmentDao()
        val budgetCalculationService = BudgetCalculationService()
        val useCase = ObserveForecastUseCase(
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            expenseDao = expenseDao,
            monthlyHistoryDao = historyDao,
            userSettingsStore = settingsStore,
            currentDateProvider = FakeCurrentDateProvider(LocalDate.of(2026, 4, 10)),
            budgetCalculationService = budgetCalculationService,
            cycleScheduleResolver = CycleScheduleResolver(budgetCalculationService),
            budgetAdjustmentResolver = BudgetAdjustmentResolver()
        )

        val forecast = useCase().first()

        assertTrue(forecast.projectedTotalSpentCents > 0L)
        assertTrue(forecast.usedDataPoints > 0)
    }
}
