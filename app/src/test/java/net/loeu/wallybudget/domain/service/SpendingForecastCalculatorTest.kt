package net.loeu.wallybudget.domain.service

import net.loeu.wallybudget.data.model.BudgetState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SpendingForecastCalculatorTest {

    private val calculator = SpendingForecastCalculator()

    @Test
    fun forecastMonthlySpending_doesNotCountSyntheticTodayPlaceholderAsAnomaly() {
        val forecast = calculator.forecastMonthlySpending(
            budgetState = testBudgetState(),
            allHistoricalExpenses = emptyList(),
            currentCycleExpenses = listOf(0L, 0L, 0L, 0L, 0L),
            daysInMonth = 30
        )

        assertEquals(0, forecast.detectedOutlierCount)
    }

    @Test
    fun forecastMonthlySpending_stillCountsRealHistoricalOutliers() {
        val forecast = calculator.forecastMonthlySpending(
            budgetState = testBudgetState(),
            allHistoricalExpenses = listOf(0L, 0L, 0L, 10_000L),
            currentCycleExpenses = emptyList(),
            daysInMonth = 30
        )

        assertEquals(1, forecast.detectedOutlierCount)
    }

    private fun testBudgetState(): BudgetState {
        return BudgetState(
            monthlyBudgetCents = 100_000L,
            totalSpentThisCycleCents = 0L,
            dailyBudgetCents = 0L,
            spentTodayCents = 0L,
            remainingTodayCents = 0L,
            daysRemainingInCycle = 30,
            cumulativeSavingsCents = 0L,
            paydayDate = 1,
            cycleStartDate = LocalDate.of(2026, 1, 1)
        )
    }
}
