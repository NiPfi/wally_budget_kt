package net.loeu.wallybudget.domain.model

const val DEFAULT_SPENDING_BUCKET_UUID = "00000000-0000-0000-0000-000000000001"
const val DEFAULT_SPENDING_BUCKET_NAME = "Spending money"

enum class BucketTrackingMode {
    DAILY_TARGET,
    CYCLE_RESERVE
}

enum class BucketBalanceBehavior {
    RETURN_TO_PORTFOLIO,
    RETAIN_IN_BUCKET
}

data class BudgetBucket(
    val bucketUuid: String,
    val name: String,
    val trackingMode: BucketTrackingMode,
    val balanceBehavior: BucketBalanceBehavior,
    val defaultAllocatedAmountCents: Long,
    val sortOrder: Int,
    val isPrimary: Boolean,
    val originInstallId: String,
    val lastModifiedByInstallId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val closedAtEpochMs: Long? = null,
    val deletedAtEpochMs: Long? = null,
    val modClock: String
) {
    val isClosed: Boolean
        get() = closedAtEpochMs != null || deletedAtEpochMs != null
}
