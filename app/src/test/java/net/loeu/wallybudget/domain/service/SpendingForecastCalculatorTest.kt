package net.loeu.wallybudget.domain.service

import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.domain.config.ForecastConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.roundToLong

class SpendingForecastCalculatorTest {

    private val calculator = SpendingForecastCalculator()

    @Test
    fun forecastMonthlySpending_doesNotCountSyntheticTodayPlaceholderAsAnomaly() {
        val forecast = calculator.forecastMonthlySpending(
            budgetState = testBudgetState(),
            allHistoricalExpenses = emptyList(),
            currentCycleExpenses = listOf(0L, 0L, 0L, 0L, 0L),
            completedCycleDailyAverages = emptyList(),
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
            completedCycleDailyAverages = emptyList(),
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
            completedCycleDailyAverages = listOf(3_194L),
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
            completedCycleDailyAverages = listOf(3_194L),
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
            completedCycleDailyAverages = listOf(3_194L),
            daysInMonth = 30
        )

        assertEquals(0.0, forecast.trendSlopeCents, 0.0)
        assertTrue(forecast.projectedDailySpendCents >= 2_500L)
        assertTrue(forecast.projectedTotalSpentCents < 130_000L)
        assertTrue(forecast.lowerBoundCents >= 4_000L)
    }

    @Test
    fun forecastMonthlySpending_lowerBound_neverDropsBelowAlreadySpentAmount() {
        val forecast = calculator.forecastMonthlySpending(
            budgetState = testBudgetState(totalSpentThisCycleCents = 4_000L),
            allHistoricalExpenses = List(30) { 0L },
            currentCycleExpenses = listOf(4_000L, 0L),
            completedCycleDailyAverages = emptyList(),
            daysInMonth = 30
        )

        assertTrue(forecast.lowerBoundCents >= 4_000L)
    }

    @Test
    fun forecastMonthlySpending_recoverableOverspend_shrinksAsCycleRunsOut() {
        val earlyCycleForecast = calculator.forecastMonthlySpending(
            budgetState = testBudgetState(),
            allHistoricalExpenses = List(30) { 0L },
            currentCycleExpenses = listOf(0L),
            completedCycleDailyAverages = listOf(2_000L),
            daysInMonth = 30
        )

        val lateCycleForecast = calculator.forecastMonthlySpending(
            budgetState = testBudgetState(totalSpentThisCycleCents = 50_000L),
            allHistoricalExpenses = List(30) { 0L },
            currentCycleExpenses = List(26) { 2_000L } + listOf(0L),
            completedCycleDailyAverages = listOf(2_000L),
            daysInMonth = 30
        )

        assertTrue(earlyCycleForecast.recoverableOverspendCents > 0L)
        assertTrue(lateCycleForecast.recoverableOverspendCents >= 0L)
        assertTrue(lateCycleForecast.recoverableOverspendCents < earlyCycleForecast.recoverableOverspendCents)
    }

    @Test
    fun forecastMonthlySpending_recoverableOverspend_usesSlightlyNonlinearDaysLeftTaper() {
        val forecast = calculator.forecastMonthlySpending(
            budgetState = testBudgetState(),
            allHistoricalExpenses = List(30) { 0L },
            currentCycleExpenses = listOf(0L),
            completedCycleDailyAverages = listOf(2_000L),
            daysInMonth = 30
        )

        val projectedLeft = forecast.estimatedEndCycleRemainingCents.toDouble()
        val remainingShare = 29.0 / 30.0
        val taperedShare =
            ((1.0 - ForecastConfig.RECOVERABLE_OVERSPEND_TAPER_QUADRATIC_WEIGHT) * remainingShare) +
                (ForecastConfig.RECOVERABLE_OVERSPEND_TAPER_QUADRATIC_WEIGHT * remainingShare * remainingShare)
        val expected = (projectedLeft * forecast.confidenceScore * taperedShare)
            .roundToLong()

        assertEquals(expected, forecast.recoverableOverspendCents)
        assertEquals(expected, forecast.grossRecoverableOverspendCents)
    }

    @Test
    fun forecastMonthlySpending_recoverableOverspend_doesNotShrinkAsExpensesAreAddedToday() {
        val forecastBeforeTodaySpend = calculator.forecastMonthlySpending(
            budgetState = testBudgetState(
                totalSpentThisCycleCents = 7_000L,
                spentTodayCents = 0L,
                remainingTodayCents = 3_333L
            ),
            allHistoricalExpenses = List(30) { 0L },
            currentCycleExpenses = listOf(4_000L, 3_000L, 0L),
            completedCycleDailyAverages = listOf(2_000L),
            daysInMonth = 30
        )

        val forecastAfterTodaySpend = calculator.forecastMonthlySpending(
            budgetState = testBudgetState(
                totalSpentThisCycleCents = 9_000L,
                spentTodayCents = 2_000L,
                remainingTodayCents = 1_333L
            ),
            allHistoricalExpenses = List(30) { 0L },
            currentCycleExpenses = listOf(4_000L, 3_000L, 2_000L),
            completedCycleDailyAverages = listOf(2_000L),
            daysInMonth = 30
        )

        assertEquals(
            forecastBeforeTodaySpend.grossRecoverableOverspendCents,
            forecastAfterTodaySpend.grossRecoverableOverspendCents
        )
    }

    @Test
    fun forecastMonthlySpending_recoverableOverspend_isCappedToZeroWhenProjectionIsDeficit() {
        val forecast = calculator.forecastMonthlySpending(
            budgetState = testBudgetState(
                totalSpentThisCycleCents = 101_000L,
                spentTodayCents = 4_000L,
                remainingTodayCents = -1_000L
            ),
            allHistoricalExpenses = List(30) { 0L },
            currentCycleExpenses = List(29) { 3_500L } + listOf(4_000L),
            completedCycleDailyAverages = listOf(3_500L),
            daysInMonth = 30
        )

        assertTrue(forecast.estimatedEndCycleRemainingCents < 0L)
        assertTrue(forecast.grossRecoverableOverspendCents >= 0L)
        assertEquals(0L, forecast.recoverableOverspendCents)
    }

    private fun testBudgetState(
        totalSpentThisCycleCents: Long = 0L,
        spentTodayCents: Long = 0L,
        remainingTodayCents: Long = 0L
    ): BudgetState {
        return BudgetState(
            monthlyBudgetCents = 100_000L,
            totalSpentThisCycleCents = totalSpentThisCycleCents,
            dailyBudgetCents = 0L,
            spentTodayCents = spentTodayCents,
            remainingTodayCents = remainingTodayCents,
            daysRemainingInCycle = 30,
            cumulativeSavingsCents = 0L,
            paydayDate = 1,
            cycleStartDate = LocalDate.of(2026, 1, 1)
        )
    }
}
