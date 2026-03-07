package net.loeu.wallybudget.domain.service

import net.loeu.wallybudget.data.model.BudgetState
import net.loeu.wallybudget.data.model.SpendingForecast
import net.loeu.wallybudget.domain.config.ForecastConfig
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt

class SpendingForecastCalculator {

    sealed class DataPrep {
        abstract val cleanedExpenses: List<Long>
        abstract val outlierCount: Int
        abstract val retainedIndexes: Set<Int>

        /**
         * Represents a successful outlier detection with calculated quartile statistics.
         */
        data class OutliersDetected(
            override val cleanedExpenses: List<Long>,
            override val outlierCount: Int,
            override val retainedIndexes: Set<Int>
        ) : DataPrep()

        /**
         * Explicitly represents that no outlier detection was performed (e.g., due to small sample size).
         */
        data class NoDetectionPerformed(
            override val cleanedExpenses: List<Long>,
            override val retainedIndexes: Set<Int>
        ) : DataPrep() {
            override val outlierCount: Int = 0
        }
    }

    data class TrendData(
        val slope: Double,
        val intercept: Double
    )

    /**
     * Prepares data by calculating quartiles and filtering outliers using the IQR method.
     * Uses linear interpolation for percentile calculation to handle small datasets gracefully.
     */
    fun prepareAndCleanData(allExpenses: List<Long>): DataPrep {
        if (allExpenses.isEmpty()) {
            return DataPrep.NoDetectionPerformed(
                cleanedExpenses = emptyList(),
                retainedIndexes = emptySet()
            )
        }
        
        // Outlier detection is generally not meaningful for very small datasets.
        if (allExpenses.size < ForecastConfig.MIN_DATA_POINTS_FOR_OUTLIERS) {
            return DataPrep.NoDetectionPerformed(
                cleanedExpenses = allExpenses,
                retainedIndexes = allExpenses.indices.toSet()
            )
        }

        val sorted = allExpenses.sorted()
        val q1 = calculatePercentile(sorted, 0.25)
        val q3 = calculatePercentile(sorted, 0.75)
        val iqr = q3 - q1

        val lowerBound = q1 - (ForecastConfig.IQR_MULTIPLIER * iqr)
        val upperBound = q3 + (ForecastConfig.IQR_MULTIPLIER * iqr)

        val retainedEntries = allExpenses.mapIndexedNotNull { index, value ->
            if (value.toDouble() in lowerBound..upperBound) {
                index to value
            } else {
                null
            }
        }
        val cleaned = retainedEntries.map { it.second }
        val outlierCount = allExpenses.size - cleaned.size

        return DataPrep.OutliersDetected(
            cleanedExpenses = cleaned,
            outlierCount = outlierCount,
            retainedIndexes = retainedEntries.mapTo(mutableSetOf()) { it.first }
        )
    }

    /**
     * Calculates the p-th percentile of a sorted list using linear interpolation (Type 7).
     * This method is more robust for small datasets than simple index-based access.
     */
    private fun calculatePercentile(sortedData: List<Long>, percentile: Double): Double {
        if (sortedData.isEmpty()) return 0.0
        if (sortedData.size == 1) return sortedData[0].toDouble()

        // Type 7: (n-1)p + 1 (adjusted for 0-indexing)
        val rank = percentile * (sortedData.size - 1)
        val index = rank.toInt()
        val fraction = rank - index

        return if (index >= sortedData.size - 1) {
            sortedData.last().toDouble()
        } else {
            sortedData[index] + fraction * (sortedData[index + 1] - sortedData[index])
        }
    }

    fun calculateWeightedMovingAverage(expenses: List<Long>, windowSize: Int, decayFactor: Double): Long {
        if (expenses.isEmpty()) return 0L
        
        val window = expenses.takeLast(windowSize)
        var totalWeight = 0.0
        var weightedSum = 0.0

        window.forEachIndexed { index, value ->
            val weight = decayFactor.pow((window.size - 1 - index).toDouble())
            weightedSum += value * weight
            totalWeight += weight
        }

        return (weightedSum / totalWeight).roundToLong()
    }

    fun calculateWeightedTrend(expenses: List<Long>, decayFactor: Double): TrendData {
        if (expenses.size < 2) return TrendData(0.0, 0.0)

        var sumW = 0.0
        var sumWX = 0.0
        var sumWY = 0.0
        var sumWXX = 0.0
        var sumWXY = 0.0

        expenses.forEachIndexed { i, y ->
            val weight = decayFactor.pow((expenses.size - 1 - i).toDouble())
            val x = i.toDouble()
            sumW += weight
            sumWX += weight * x
            sumWY += weight * y
            sumWXX += weight * x * x
            sumWXY += weight * x * y
        }

        val denominator = sumW * sumWXX - sumWX * sumWX
        if (abs(denominator) < 1e-10) return TrendData(0.0, 0.0)

        val slope = (sumW * sumWXY - sumWX * sumWY) / denominator
        val intercept = (sumWY - slope * sumWX) / sumW

        return TrendData(slope, intercept)
    }

    fun calculateForecastConfidence(cleanedExpenses: List<Long>, outlierCount: Int, daysElapsed: Int): Double {
        if (cleanedExpenses.isEmpty()) return 0.0

        val mean = cleanedExpenses.average()
        val stdDev = if (cleanedExpenses.size > 1) {
            sqrt(cleanedExpenses.sumOf { (it.toDouble() - mean).pow(2.0) } / (cleanedExpenses.size - 1))
        } else {
            0.0
        }

        // Consistency score (35% weight): 1 / (1 + coefficientOfVariation)
        val cv = if (mean != 0.0) stdDev / mean else 0.0
        val consistencyScore = 1.0 / (1.0 + cv)

        // Sample size score (35% weight): min(daysElapsed / 30, 1.0)
        val sampleSizeScore = min(daysElapsed.toDouble() / 30.0, 1.0)

        // Outlier ratio score (20% weight): 1 - (outlierCount / totalExpenseCount)
        val totalCount = cleanedExpenses.size + outlierCount
        val outlierRatioScore = 1.0 - (outlierCount.toDouble() / totalCount.coerceAtLeast(1))

        // Early-month penalty (10% weight): max(0.7, daysElapsed / 10)
        val earlyMonthPenalty = max(0.7, min(daysElapsed.toDouble() / 10.0, 1.0))

        val confidence = (consistencyScore * 0.35) +
                (sampleSizeScore * 0.35) +
                (outlierRatioScore * 0.20) +
                (earlyMonthPenalty * 0.10)

        return confidence.coerceIn(0.0, 1.0)
    }

    fun forecastMonthlySpending(
        budgetState: BudgetState,
        allHistoricalExpenses: List<Long>,
        currentCycleExpenses: List<Long>,
        daysInMonth: Int
    ): SpendingForecast {
        val dailyAllowance = budgetState.monthlyBudgetCents / daysInMonth

        // Normalize today's spending: replace the last entry of the current cycle with the daily allowance.
        // This prevents today's anomalous spending from skewing the forward-looking forecast components
        // (WMA and trend), while we still account for the actual spend in the final total.
        val normalizedCurrentCycle = if (currentCycleExpenses.isNotEmpty()) {
            currentCycleExpenses.dropLast(1) + dailyAllowance
        } else {
            currentCycleExpenses
        }

        val combinedExpenses = allHistoricalExpenses + normalizedCurrentCycle
        val prep = prepareAndCleanData(combinedExpenses)
        val syntheticTodayIndex = currentCycleExpenses.lastIndex
            .takeIf { it >= 0 }
            ?.let { allHistoricalExpenses.size + it }
        val adjustedOutlierCount = syntheticTodayIndex?.let { index ->
            if (index in prep.retainedIndexes) {
                prep.outlierCount
            } else {
                // The normalized "today" placeholder is synthetic forecast input, not a real anomaly.
                (prep.outlierCount - 1).coerceAtLeast(0)
            }
        } ?: prep.outlierCount
        
        val daysElapsed = currentCycleExpenses.size
        val daysRemaining = (daysInMonth - daysElapsed).coerceAtLeast(0)

        // Short-term average: weighted moving average (heavily reactive to recent days)
        val shortTermAverage = calculateWeightedMovingAverage(
            prep.cleanedExpenses, 
            ForecastConfig.WEIGHTED_AVERAGE_WINDOW_DAYS, 
            ForecastConfig.DECAY_FACTOR
        )

        // Long-term average: simple average of cleaned historical data (stable baseline)
        val longTermAverage = if (prep.cleanedExpenses.isNotEmpty()) {
            prep.cleanedExpenses.average()
        } else {
            0.0
        }
        
        val trend = calculateWeightedTrend(prep.cleanedExpenses, ForecastConfig.DECAY_FACTOR)
        val confidence = calculateForecastConfidence(prep.cleanedExpenses, adjustedOutlierCount, daysElapsed)

        // Confidence-weighted blending: 
        // Higher confidence -> trust the short-term weighted average more.
        // Lower confidence -> revert towards the long-term stable average.
        val dailyForecast = (confidence * shortTermAverage + (1.0 - confidence) * longTermAverage).roundToLong()

        // Properly account for linear trend accumulation over time.
        // Formula: Sum of (dailyForecast + slope * day) for day = 1 to daysRemaining.
        // Result: dailyForecast * daysRemaining + slope * (daysRemaining * (daysRemaining + 1) / 2)
        // Trend is also dampened by confidence to avoid wild swings on noisy data.
        val projectedRemainingCents = (
            dailyForecast * daysRemaining +
            trend.slope * confidence * ForecastConfig.TREND_DAMPENING_FACTOR * daysRemaining * (daysRemaining + 1) / 2.0
        ).roundToLong().coerceAtLeast(0L)

        // Use the actual total spent this cycle (including today's real spending) for the final projection.
        val projectedTotalSpentCents = budgetState.totalSpentThisCycleCents + projectedRemainingCents
        val estimatedEndCycleRemainingCents = budgetState.monthlyBudgetCents - projectedTotalSpentCents

        // Confidence-adjusted margin of error
        val mean = prep.cleanedExpenses.average()
        val stdDev = if (prep.cleanedExpenses.size > 1) {
            sqrt(prep.cleanedExpenses.sumOf { (it.toDouble() - mean).pow(2.0) } / (prep.cleanedExpenses.size - 1))
        } else {
            0.0
        }
        
        // Standard error of the sum for the remaining days (SE_sum = daily_stdDev * sqrt(N)).
        // We use a minimum uncertainty window to avoid artificially narrow bounds on the last day.
        val uncertaintyWindow = max(daysRemaining.toDouble(), ForecastConfig.MIN_UNCERTAINTY_DAYS)
        val standardErrorOfSum = stdDev * sqrt(uncertaintyWindow)

        // Margin of error calculation.
        // We use a baseline z-score of 1.96 (95% confidence level).
        // Low heuristic confidence expands the interval to reflect additional uncertainty.
        // The uncertainty expansion uses linear interpolation: expansion = 2.0 - confidence.
        // This maps confidence=1.0 to 1.0x (no expansion) and confidence=0.0 to 2.0x (double width).
        val zScore = 1.96
        val uncertaintyExpansion = 2.0 - confidence
        val marginOfError = zScore * standardErrorOfSum * uncertaintyExpansion
        
        val lowerBound = (projectedTotalSpentCents - marginOfError).roundToLong().coerceAtLeast(0L)
        val upperBound = (projectedTotalSpentCents + marginOfError).roundToLong()

        val confidenceRating = when {
            confidence >= 0.85 -> "Very High"
            confidence >= 0.70 -> "High"
            confidence >= 0.55 -> "Moderate"
            confidence >= 0.40 -> "Low"
            else -> "Very Low"
        }

        return SpendingForecast(
            estimatedEndCycleRemainingCents = estimatedEndCycleRemainingCents,
            projectedTotalSpentCents = projectedTotalSpentCents,
            projectedDailySpendCents = dailyForecast, // Using blended forecast as the "pace"
            trendSlopePercent = if (dailyForecast > 0L) {
                ((trend.slope / dailyForecast.toDouble()) * 100.0).roundToInt().coerceIn(-200, 200)
            } else {
                0
            },
            confidenceScore = confidence,
            confidenceRating = confidenceRating,
            lowerBoundCents = lowerBound,
            upperBoundCents = upperBound,
            dailyAverageWeightedCents = dailyForecast,
            trendSlopeCents = trend.slope,
            detectedOutlierCount = adjustedOutlierCount,
            usedDataPoints = prep.cleanedExpenses.size
        )
    }
}
