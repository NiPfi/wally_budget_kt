package net.loeu.wallybudget.domain.service

import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.domain.model.SpendingForecast
import net.loeu.wallybudget.domain.config.ForecastConfig
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt

class SpendingForecastCalculator {

    private data class IndexedExpense(
        val index: Int,
        val value: Long
    )

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
        return when {
            allExpenses.isEmpty() -> noDetectionPerformed(allExpenses)

            // Outlier detection is generally not meaningful for very small datasets.
            allExpenses.size < ForecastConfig.MIN_DATA_POINTS_FOR_OUTLIERS -> {
                noDetectionPerformed(allExpenses)
            }

            else -> {
                val allIndexedExpenses = allExpenses.mapIndexed { index, value ->
                    IndexedExpense(index = index, value = value)
                }
                val primaryDetection = detectOutliers(allIndexedExpenses)
                val positiveExpenses = allIndexedExpenses.filter { it.value > 0L }

                primaryDetection ?: if (positiveExpenses.size < ForecastConfig.MIN_DATA_POINTS_FOR_OUTLIERS) {
                    noDetectionPerformed(allExpenses)
                } else {
                    detectOutliers(
                        entries = positiveExpenses,
                        alwaysRetained = allIndexedExpenses.filter { it.value == 0L }
                    ) ?: noDetectionPerformed(allExpenses)
                }
            }
        }
    }

    private fun detectOutliers(
        entries: List<IndexedExpense>,
        alwaysRetained: List<IndexedExpense> = emptyList()
    ): DataPrep.OutliersDetected? {
        return if (entries.size < ForecastConfig.MIN_DATA_POINTS_FOR_OUTLIERS) {
            null
        } else {
            val sorted = entries.map { it.value }.sorted()
            val q1 = calculatePercentile(sorted, 0.25)
            val q3 = calculatePercentile(sorted, 0.75)
            val iqr = q3 - q1

            if (iqr == 0.0) {
                null
            } else {
                val lowerBound = q1 - (ForecastConfig.IQR_MULTIPLIER * iqr)
                val upperBound = q3 + (ForecastConfig.IQR_MULTIPLIER * iqr)

                val retainedEntries = (alwaysRetained + entries.filter { entry ->
                    entry.value.toDouble() in lowerBound..upperBound
                }).sortedBy { it.index }

                DataPrep.OutliersDetected(
                    cleanedExpenses = retainedEntries.map { it.value },
                    outlierCount = entries.size - retainedEntries.count { retained ->
                        entries.any { it.index == retained.index }
                    },
                    retainedIndexes = retainedEntries.mapTo(mutableSetOf()) { it.index }
                )
            }
        }
    }

    /**
     * Calculates the p-th percentile of a sorted list using linear interpolation (Type 7).
     * This method is more robust for small datasets than simple index-based access.
     */
    private fun calculatePercentile(sortedData: List<Long>, percentile: Double): Double {
        return when {
            sortedData.isEmpty() -> 0.0
            sortedData.size == 1 -> sortedData[0].toDouble()
            else -> {
                // Type 7: (n-1)p + 1 (adjusted for 0-indexing)
                val rank = percentile * (sortedData.size - 1)
                val index = rank.toInt()
                val fraction = rank - index

                if (index >= sortedData.size - 1) {
                    sortedData.last().toDouble()
                } else {
                    sortedData[index] + fraction * (sortedData[index + 1] - sortedData[index])
                }
            }
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
        return if (expenses.size < 2) {
            TrendData(0.0, 0.0)
        } else {
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
            if (abs(denominator) < 1e-10) {
                TrendData(0.0, 0.0)
            } else {
                val slope = (sumW * sumWXY - sumWX * sumWY) / denominator
                val intercept = (sumWY - slope * sumWX) / sumW
                TrendData(slope, intercept)
            }
        }
    }

    private fun noDetectionPerformed(allExpenses: List<Long>): DataPrep.NoDetectionPerformed {
        return DataPrep.NoDetectionPerformed(
            cleanedExpenses = allExpenses,
            retainedIndexes = allExpenses.indices.toSet()
        )
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
        completedCycleDailyAverages: List<Long>,
        daysInMonth: Int
    ): SpendingForecast {
        val dailyAllowance = budgetState.monthlyBudgetCents / daysInMonth
        val completedCurrentCycleExpenses = currentCycleExpenses.dropLast(1)
        val combinedExpenses = allHistoricalExpenses + completedCurrentCycleExpenses
        val prep = prepareAndCleanData(combinedExpenses)
        val daysElapsed = currentCycleExpenses.size
        val daysRemaining = (daysInMonth - daysElapsed).coerceAtLeast(0)
        val completedDays = completedCurrentCycleExpenses.size
        val adjustedOutlierCount = prep.outlierCount

        // Long-term average: simple average of cleaned historical data (stable baseline)
        val longTermAverage = if (prep.cleanedExpenses.isNotEmpty()) {
            prep.cleanedExpenses.average()
        } else {
            0.0
        }
        val cyclePriorAverage = calculateCyclePriorAverage(
            completedCycleDailyAverages = completedCycleDailyAverages,
            fallbackDailyAverage = longTermAverage.roundToLong().takeIf { it > 0L } ?: dailyAllowance
        )
        val currentPaceEstimate = calculateCurrentPaceEstimate(
            budgetState = budgetState,
            completedCurrentCycleExpenses = completedCurrentCycleExpenses,
            completedDays = completedDays,
            cyclePriorAverage = cyclePriorAverage
        )
        val currentEvidenceWeight = (
            completedDays / (completedDays + ForecastConfig.PRIOR_STRENGTH_DAYS)
        ).coerceIn(0.0, 1.0)

        val completedNonZeroDays = completedCurrentCycleExpenses.count { it > 0L }
        val trend = if (
            completedCurrentCycleExpenses.size >= ForecastConfig.MIN_COMPLETED_DAYS_FOR_CURRENT_TREND &&
            completedNonZeroDays >= ForecastConfig.MIN_NON_ZERO_DAYS_FOR_CURRENT_TREND
        ) {
            calculateWeightedTrend(completedCurrentCycleExpenses, ForecastConfig.DECAY_FACTOR)
        } else {
            TrendData(0.0, 0.0)
        }
        val confidence = calculateForecastConfidence(prep.cleanedExpenses, adjustedOutlierCount, daysElapsed)
        val trendWeight = (currentEvidenceWeight * confidence).coerceIn(0.0, 1.0)

        // Completed cycles define the prior and completed current-cycle days update that prior
        // gradually, so sparse recent activity does not overwhelm the longer-term baseline.
        val dailyForecast = (
            (1.0 - currentEvidenceWeight) * cyclePriorAverage +
            currentEvidenceWeight * currentPaceEstimate
        ).roundToLong()

        // Properly account for linear trend accumulation over time.
        // Formula: Sum of (dailyForecast + slope * day) for day = 1 to daysRemaining.
        // Result: dailyForecast * daysRemaining + slope * (daysRemaining * (daysRemaining + 1) / 2)
        // Trend is also dampened by confidence to avoid wild swings on noisy data.
        val projectedRemainingCents = (
            dailyForecast * daysRemaining +
                trend.slope * trendWeight * ForecastConfig.TREND_DAMPENING_FACTOR *
                daysRemaining * (daysRemaining + 1) / 2.0
        ).roundToLong().coerceAtLeast(0L)

        // Use the actual total spent this cycle (including today's real spending) for the final projection.
        val projectedTotalSpentCents = budgetState.totalSpentThisCycleCents + projectedRemainingCents
        val estimatedEndCycleRemainingCents = budgetState.monthlyBudgetCents - projectedTotalSpentCents
        val spentBeforeTodayCents =
            (budgetState.totalSpentThisCycleCents - budgetState.spentTodayCents).coerceAtLeast(0L)
        val effectiveDailyAllowanceCents = budgetState.remainingTodayCents + budgetState.spentTodayCents
        val recoverableOverspendBaselineRemainingCents =
            budgetState.monthlyBudgetCents -
                (spentBeforeTodayCents + effectiveDailyAllowanceCents + projectedRemainingCents)
        val grossRecoverableOverspendCents = calculateRecoverableOverspend(
            estimatedEndCycleRemainingCents = recoverableOverspendBaselineRemainingCents,
            confidence = confidence,
            daysRemaining = daysRemaining,
            daysInCycle = daysInMonth
        )
        val recoverableOverspendCents = grossRecoverableOverspendCents.coerceAtMost(
            estimatedEndCycleRemainingCents.coerceAtLeast(0L)
        )

        // Confidence-adjusted margin of error
        val mean = prep.cleanedExpenses.average()
        val stdDev = if (prep.cleanedExpenses.size > 1) {
            sqrt(
                prep.cleanedExpenses.sumOf { (it.toDouble() - mean).pow(2.0) } /
                    (prep.cleanedExpenses.size - 1)
            )
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
        
        val lowerBound = (projectedTotalSpentCents - marginOfError)
            .roundToLong()
            .coerceAtLeast(budgetState.totalSpentThisCycleCents)
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
            usedDataPoints = prep.cleanedExpenses.size,
            recoverableOverspendCents = recoverableOverspendCents,
            grossRecoverableOverspendCents = grossRecoverableOverspendCents
        )
    }

    private fun calculateRecoverableOverspend(
        estimatedEndCycleRemainingCents: Long,
        confidence: Double,
        daysRemaining: Int,
        daysInCycle: Int
    ): Long {
        if (estimatedEndCycleRemainingCents <= 0L || daysRemaining <= 0 || daysInCycle <= 0) {
            return 0L
        }

        val remainingCycleShare =
            (daysRemaining.toDouble() / daysInCycle.toDouble()).coerceIn(0.0, 1.0)
        val taperedRemainingShare = (
            ((1.0 - ForecastConfig.RECOVERABLE_OVERSPEND_TAPER_QUADRATIC_WEIGHT) *
                remainingCycleShare) +
                (
                    ForecastConfig.RECOVERABLE_OVERSPEND_TAPER_QUADRATIC_WEIGHT *
                        remainingCycleShare * remainingCycleShare
                    )
            ).coerceIn(0.0, 1.0)

        return (estimatedEndCycleRemainingCents * confidence * taperedRemainingShare)
            .roundToLong()
            .coerceIn(0L, estimatedEndCycleRemainingCents)
    }

    private fun calculateCurrentPaceEstimate(
        budgetState: BudgetState,
        completedCurrentCycleExpenses: List<Long>,
        completedDays: Int,
        cyclePriorAverage: Long
    ): Double {
        val observedCycleAverage = if (completedDays > 0) {
            (budgetState.totalSpentThisCycleCents - budgetState.spentTodayCents).toDouble() /
                completedDays
        } else {
            cyclePriorAverage.toDouble()
        }
        val recentCompletedAverage = if (completedCurrentCycleExpenses.isNotEmpty()) {
            calculateWeightedMovingAverage(
                expenses = completedCurrentCycleExpenses,
                windowSize = ForecastConfig.WEIGHTED_AVERAGE_WINDOW_DAYS,
                decayFactor = ForecastConfig.DECAY_FACTOR
            ).toDouble()
        } else {
            observedCycleAverage
        }

        return if (
            completedDays >= ForecastConfig.MIN_DATA_POINTS_FOR_OUTLIERS &&
            completedCurrentCycleExpenses.count { it > 0L } >= 2
        ) {
            (observedCycleAverage + recentCompletedAverage) / 2.0
        } else {
            observedCycleAverage
        }
    }

    private fun calculateCyclePriorAverage(
        completedCycleDailyAverages: List<Long>,
        fallbackDailyAverage: Long
    ): Long {
        if (completedCycleDailyAverages.isEmpty()) {
            return fallbackDailyAverage
        }

        return calculateWeightedMovingAverage(
            expenses = completedCycleDailyAverages,
            windowSize = ForecastConfig.PRIOR_CYCLE_WINDOW,
            decayFactor = ForecastConfig.PRIOR_CYCLE_DECAY_FACTOR
        )
    }
}
