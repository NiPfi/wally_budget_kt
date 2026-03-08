package net.loeu.wallybudget.domain.service

import net.loeu.wallybudget.data.model.BudgetState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun forecastMonthlySpending_zeroHeavyHistory_doesNotTreatEverySpendDayAsAnomaly() {
        val forecast = calculator.forecastMonthlySpending(
            budgetState = testBudgetState(),
            allHistoricalExpenses = List(27) { 0L } + listOf(4_000L, 5_000L, 40_000L, 50_000L),
            currentCycleExpenses = listOf(0L),
            daysInMonth = 30
        )

        assertEquals(0, forecast.detectedOutlierCount)
        assertTrue(forecast.projectedDailySpendCents >= 3_000L)
    }

    @Test
    fun forecastMonthlySpending_earlyCycle_doesNotExtrapolateTrendFromSyntheticTodayAllowance() {
        val historicalDailyExpenses = buildList {
            add(4_000L)
            addAll(List(9) { 0L })
            add(5_000L)
            addAll(List(9) { 0L })
            add(40_000L)
            addAll(List(9) { 0L })
            add(50_000L)
        }

        val forecast = calculator.forecastMonthlySpending(
            budgetState = testBudgetState(),
            allHistoricalExpenses = historicalDailyExpenses,
            currentCycleExpenses = listOf(0L, 0L),
            daysInMonth = 30
        )

        assertEquals(0.0, forecast.trendSlopeCents, 0.0)
        assertTrue(forecast.projectedTotalSpentCents < 120_000L)
    }

    @Test
    fun forecastMonthlySpending_singleCurrentSpendDay_doesNotCreateIncreasingTrend() {
        val historicalDailyExpenses = buildList {
            add(4_000L)
            addAll(List(9) { 0L })
            add(5_000L)
            addAll(List(9) { 0L })
            add(40_000L)
            addAll(List(9) { 0L })
            add(50_000L)
        }

        val forecast = calculator.forecastMonthlySpending(
            budgetState = testBudgetState(totalSpentThisCycleCents = 4_000L),
            allHistoricalExpenses = historicalDailyExpenses,
            currentCycleExpenses = listOf(0L, 0L, 0L, 4_000L, 0L),
            daysInMonth = 30
        )

        assertEquals(0.0, forecast.trendSlopeCents, 0.0)
        assertTrue(forecast.projectedTotalSpentCents < 120_000L)
        assertTrue(forecast.lowerBoundCents >= 4_000L)
    }

    @Test
    fun forecastMonthlySpending_lowerBound_neverDropsBelowAlreadySpentAmount() {
        val forecast = calculator.forecastMonthlySpending(
            budgetState = testBudgetState(totalSpentThisCycleCents = 4_000L),
            allHistoricalExpenses = List(30) { 0L },
            currentCycleExpenses = listOf(4_000L, 0L),
            daysInMonth = 30
        )

        assertTrue(forecast.lowerBoundCents >= 4_000L)
    }

    private fun testBudgetState(totalSpentThisCycleCents: Long = 0L): BudgetState {
        return BudgetState(
            monthlyBudgetCents = 100_000L,
            totalSpentThisCycleCents = totalSpentThisCycleCents,
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
