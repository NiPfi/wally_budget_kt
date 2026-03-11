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
        val completedCurrentCycleExpenses = currentCycleExpenses.dropLast(1)
        val daysElapsed = currentCycleExpenses.size
        val projectionInputs = buildForecastProjectionInputs(
            budgetState = budgetState,
            allHistoricalExpenses = allHistoricalExpenses,
            completedCurrentCycleExpenses = completedCurrentCycleExpenses,
            completedCycleDailyAverages = completedCycleDailyAverages,
            daysInMonth = daysInMonth,
            daysElapsed = daysElapsed
        )
        val projection = buildForecastProjection(
            budgetState = budgetState,
            daysInMonth = daysInMonth,
            projectionInputs = projectionInputs
        )

        return SpendingForecast(
            estimatedEndCycleRemainingCents = projection.estimatedEndCycleRemainingCents,
            projectedTotalSpentCents = projection.projectedTotalSpentCents,
            projectedDailySpendCents = projectionInputs.dailyForecast,
            trendSlopePercent = if (projectionInputs.dailyForecast > 0L) {
                (
                    (projectionInputs.trend.slope / projectionInputs.dailyForecast.toDouble()) *
                        100.0
                    ).roundToInt().coerceIn(-200, 200)
            } else {
                0
            },
            confidenceScore = projectionInputs.confidence,
            confidenceRating = projection.confidenceRating,
            lowerBoundCents = projection.lowerBound,
            upperBoundCents = projection.upperBound,
            dailyAverageWeightedCents = projectionInputs.dailyForecast,
            trendSlopeCents = projectionInputs.trend.slope,
            detectedOutlierCount = projectionInputs.adjustedOutlierCount,
            usedDataPoints = projectionInputs.prep.cleanedExpenses.size,
            recoverableOverspendCents = projection.recoverableOverspendCents,
            grossRecoverableOverspendCents = projection.grossRecoverableOverspendCents
        )
    }
}
