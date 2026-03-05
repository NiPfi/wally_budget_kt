package net.loeu.wallybudget.data.model

/**
 * Data model for spending forecast results.
 */
data class SpendingForecast(
    val estimatedEndCycleRemainingCents: Long = 0L,
    val projectedTotalSpentCents: Long = 0L,
    val projectedDailySpendCents: Long = 0L,
    
    // Fields from Duck.ai enhanced algorithm
    val confidenceScore: Double = 0.0,
    val confidenceRating: String = "Unknown",
    val lowerBoundCents: Long = 0L,
    val upperBoundCents: Long = 0L,
    val dailyAverageWeightedCents: Long = 0L,
    val trendSlopeCents: Double = 0.0,
    /**
     * The trend slope expressed as a percentage of the daily forecast.
     * Clamped to [-200, 200].
     * Primarily used for relative trend comparisons.
     */
    val trendSlopePercent: Int = 0,
    val detectedOutlierCount: Int = 0,
    val usedDataPoints: Int = 0
) {
    val isProjectedOverBudget: Boolean
        get() = estimatedEndCycleRemainingCents < 0L
}
