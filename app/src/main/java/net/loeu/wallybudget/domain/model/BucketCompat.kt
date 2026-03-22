package net.loeu.wallybudget.domain.model

@Deprecated("Buckets are always spending buckets. Kept only for compatibility.")
enum class BucketTrackingMode {
    DAILY_TARGET,
    CYCLE_RESERVE
}

@Deprecated("Bucket balance behavior is no longer used. Kept only for compatibility.")
enum class BucketBalanceBehavior {
    RETURN_TO_PORTFOLIO,
    RETAIN_IN_BUCKET
}
