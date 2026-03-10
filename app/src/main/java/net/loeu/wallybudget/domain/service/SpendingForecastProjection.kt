package net.loeu.wallybudget.domain.service

import net.loeu.wallybudget.domain.config.ForecastConfig
import net.loeu.wallybudget.domain.model.BudgetState
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sqrt

internal data class ForecastProjectionInputs(
    val prep: SpendingForecastCalculator.DataPrep,
    val dailyForecast: Long,
    val trend: SpendingForecastCalculator.TrendData,
    val confidence: Double,
    val currentEvidenceWeight: Double,
    val daysRemaining: Int,
    val adjustedOutlierCount: Int
)

internal data class ForecastProjection(
    val projectedTotalSpentCents: Long,
    val estimatedEndCycleRemainingCents: Long,
    val lowerBound: Long,
    val upperBound: Long,
    val recoverableOverspendCents: Long,
    val grossRecoverableOverspendCents: Long,
    val confidenceRating: String
)

internal fun SpendingForecastCalculator.buildForecastProjectionInputs(
    budgetState: BudgetState,
    allHistoricalExpenses: List<Long>,
    completedCurrentCycleExpenses: List<Long>,
    completedCycleDailyAverages: List<Long>,
    daysInMonth: Int,
    daysElapsed: Int
): ForecastProjectionInputs {
    val dailyAllowance = budgetState.monthlyBudgetCents / daysInMonth
    val prep = prepareAndCleanData(allHistoricalExpenses + completedCurrentCycleExpenses)
    val completedDays = completedCurrentCycleExpenses.size
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
    val currentEvidenceWeight =
        (completedDays / (completedDays + ForecastConfig.PRIOR_STRENGTH_DAYS)).coerceIn(0.0, 1.0)
    val trend = calculateForecastTrend(completedCurrentCycleExpenses)
    val confidence = calculateForecastConfidence(prep.cleanedExpenses, prep.outlierCount, daysElapsed)

    return ForecastProjectionInputs(
        prep = prep,
        dailyForecast = (
            (1.0 - currentEvidenceWeight) * cyclePriorAverage +
                currentEvidenceWeight * currentPaceEstimate
            ).roundToLong(),
        trend = trend,
        confidence = confidence,
        currentEvidenceWeight = currentEvidenceWeight,
        daysRemaining = (daysInMonth - daysElapsed).coerceAtLeast(0),
        adjustedOutlierCount = prep.outlierCount
    )
}

internal fun SpendingForecastCalculator.buildForecastProjection(
    budgetState: BudgetState,
    daysInMonth: Int,
    projectionInputs: ForecastProjectionInputs
): ForecastProjection {
    val trendWeight =
        (projectionInputs.currentEvidenceWeight * projectionInputs.confidence).coerceIn(0.0, 1.0)
    val projectedRemainingCents = (
        projectionInputs.dailyForecast * projectionInputs.daysRemaining +
            projectionInputs.trend.slope * trendWeight * ForecastConfig.TREND_DAMPENING_FACTOR *
            projectionInputs.daysRemaining * (projectionInputs.daysRemaining + 1) / 2.0
        ).roundToLong().coerceAtLeast(0L)
    val projectedTotalSpentCents = budgetState.totalSpentThisCycleCents + projectedRemainingCents
    val estimatedEndCycleRemainingCents =
        budgetState.monthlyBudgetCents - projectedTotalSpentCents
    val spentBeforeTodayCents =
        (budgetState.totalSpentThisCycleCents - budgetState.spentTodayCents).coerceAtLeast(0L)
    val effectiveDailyAllowanceCents =
        budgetState.remainingTodayCents + budgetState.spentTodayCents
    val recoverableBaselineRemainingCents =
        budgetState.monthlyBudgetCents -
            (spentBeforeTodayCents + effectiveDailyAllowanceCents + projectedRemainingCents)
    val grossRecoverableOverspendCents = calculateRecoverableOverspend(
        estimatedEndCycleRemainingCents = recoverableBaselineRemainingCents,
        confidence = projectionInputs.confidence,
        daysRemaining = projectionInputs.daysRemaining,
        daysInCycle = daysInMonth
    )
    val bounds = forecastBounds(
        budgetState = budgetState,
        prep = projectionInputs.prep,
        projectedTotalSpentCents = projectedTotalSpentCents,
        confidence = projectionInputs.confidence,
        daysRemaining = projectionInputs.daysRemaining
    )

    return ForecastProjection(
        projectedTotalSpentCents = projectedTotalSpentCents,
        estimatedEndCycleRemainingCents = estimatedEndCycleRemainingCents,
        lowerBound = bounds.first,
        upperBound = bounds.second,
        recoverableOverspendCents = grossRecoverableOverspendCents.coerceAtMost(
            estimatedEndCycleRemainingCents.coerceAtLeast(0L)
        ),
        grossRecoverableOverspendCents = grossRecoverableOverspendCents,
        confidenceRating = confidenceRating(projectionInputs.confidence)
    )
}

private fun SpendingForecastCalculator.calculateCurrentPaceEstimate(
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

private fun SpendingForecastCalculator.calculateCyclePriorAverage(
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

private fun SpendingForecastCalculator.calculateForecastTrend(
    completedCurrentCycleExpenses: List<Long>
): SpendingForecastCalculator.TrendData {
    val completedNonZeroDays = completedCurrentCycleExpenses.count { it > 0L }
    return if (
        completedCurrentCycleExpenses.size >= ForecastConfig.MIN_COMPLETED_DAYS_FOR_CURRENT_TREND &&
        completedNonZeroDays >= ForecastConfig.MIN_NON_ZERO_DAYS_FOR_CURRENT_TREND
    ) {
        calculateWeightedTrend(completedCurrentCycleExpenses, ForecastConfig.DECAY_FACTOR)
    } else {
        SpendingForecastCalculator.TrendData(0.0, 0.0)
    }
}

private fun forecastBounds(
    budgetState: BudgetState,
    prep: SpendingForecastCalculator.DataPrep,
    projectedTotalSpentCents: Long,
    confidence: Double,
    daysRemaining: Int
): Pair<Long, Long> {
    val mean = prep.cleanedExpenses.average()
    val stdDev = if (prep.cleanedExpenses.size > 1) {
        sqrt(
            prep.cleanedExpenses.sumOf { (it.toDouble() - mean).pow(2.0) } /
                (prep.cleanedExpenses.size - 1)
        )
    } else {
        0.0
    }
    val standardErrorOfSum =
        stdDev * sqrt(max(daysRemaining.toDouble(), ForecastConfig.MIN_UNCERTAINTY_DAYS))
    val marginOfError = 1.96 * standardErrorOfSum * (2.0 - confidence)
    val lowerBound = (projectedTotalSpentCents - marginOfError)
        .roundToLong()
        .coerceAtLeast(budgetState.totalSpentThisCycleCents)
    val upperBound = (projectedTotalSpentCents + marginOfError).roundToLong()
    return lowerBound to upperBound
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

    val remainingCycleShare = (daysRemaining.toDouble() / daysInCycle.toDouble()).coerceIn(0.0, 1.0)
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

private fun confidenceRating(confidence: Double): String = when {
    confidence >= 0.85 -> "Very High"
    confidence >= 0.70 -> "High"
    confidence >= 0.55 -> "Moderate"
    confidence >= 0.40 -> "Low"
    else -> "Very Low"
}
