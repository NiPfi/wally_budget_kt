package net.loeu.wallybudget.domain.config

/**
 * Centralized configuration for the spending forecast algorithm.
 */
object ForecastConfig {
    /**
     * Number of days of historical expense data to include in the forecast analysis.
     * Recommended range: 30–90 days.
     */
    const val HISTORICAL_DAYS_LOOKBACK = 60

    /**
     * Window size for the weighted moving average calculation.
     * Larger = smoother, less responsive; smaller = more responsive.
     * Recommended range: 7–21 days.
     */
    const val WEIGHTED_AVERAGE_WINDOW_DAYS = 14

    /**
     * Exponential decay factor for weight prioritization (recent days weighted higher).
     * Higher = emphasize recent days; lower = balance recent + historical.
     * Recommended range: 0.85–0.97.
     */
    const val DECAY_FACTOR = 0.88

    /**
     * Minimum acceptable confidence score before showing an uncertainty warning.
     * Recommended range: 0.50–0.85.
     */
    const val MIN_CONFIDENCE_THRESHOLD = 0.60

    /**
     * Interquartile Range (IQR) multiplier for outlier detection.
     * 1.5 is standard; 3.0 for highly volatile spending patterns.
     */
    const val IQR_MULTIPLIER = 2.0

    /**
     * Minimum number of data points required to perform outlier detection.
     * IQR-based detection is unreliable for very small samples.
     */
    const val MIN_DATA_POINTS_FOR_OUTLIERS = 4

    /**
     * Multiplier applied to the calculated trend slope to dampen its impact.
     * A value of 0.5 represents a conservative approach, assuming historical trends 
     * may not fully persist or may be partially accounted for in the daily average.
     */
    const val TREND_DAMPENING_FACTOR = 0.5

    /**
     * Prior strength for the completed-cycle baseline, expressed in "pseudo-days" of evidence.
     * Higher values make the forecast hold onto previous cycles longer before current-cycle data dominates.
     */
    const val PRIOR_STRENGTH_DAYS = 10.0

    /**
     * Minimum number of completed current-cycle days required before using a current-cycle trend.
     * Early in a cycle, the trend signal is too unstable and should defer to the cycle prior.
     */
    const val MIN_COMPLETED_DAYS_FOR_CURRENT_TREND = 10

    /**
     * Minimum number of non-zero completed current-cycle days required before using a current-cycle trend.
     * A couple of isolated purchases are not enough evidence of persistent acceleration.
     */
    const val MIN_NON_ZERO_DAYS_FOR_CURRENT_TREND = 3

    /**
     * Maximum number of completed cycles to include in the cycle prior.
     */
    const val PRIOR_CYCLE_WINDOW = 4

    /**
     * Exponential decay used when weighting recent completed cycles into the cycle prior.
     */
    const val PRIOR_CYCLE_DECAY_FACTOR = 0.75

    /**
     * Blend factor for the recoverable-overspend taper.
     * 0.0 = purely linear with days remaining, 1.0 = fully quadratic.
     * A partial blend keeps mid-cycle headroom more usable while still tightening hard near cycle end.
     */
    const val RECOVERABLE_OVERSPEND_TAPER_QUADRATIC_WEIGHT = 0.35

    /**
     * Minimum days of uncertainty to assume even when near the end of a cycle.
     * Prevents artificially narrow confidence bounds on the final day.
     */
    const val MIN_UNCERTAINTY_DAYS = 0.5

    /**
     * Threshold in cents to determine if a spending trend is significant.
     * Values below this threshold are considered "Stable".
     * Equivalent to ~$15/month change; below this is considered noise.
     */
    const val TREND_SIGNIFICANCE_THRESHOLD_CENTS = 50.0
}
