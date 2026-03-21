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
    val originInstallId: String,
    val lastModifiedByInstallId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val closedAtEpochMs: Long? = null,
    val deletedAtEpochMs: Long? = null,
    val modClock: String
) {
    @Suppress("UNUSED_PARAMETER")
    constructor(
        bucketUuid: String,
        name: String,
        trackingMode: BucketTrackingMode,
        balanceBehavior: BucketBalanceBehavior,
        defaultAllocatedAmountCents: Long,
        sortOrder: Int,
        isPrimary: Boolean,
        originInstallId: String,
        lastModifiedByInstallId: String,
        createdAtEpochMs: Long,
        updatedAtEpochMs: Long,
        closedAtEpochMs: Long? = null,
        deletedAtEpochMs: Long? = null,
        modClock: String
    ) : this(
        bucketUuid = bucketUuid,
        name = name,
        trackingMode = trackingMode,
        balanceBehavior = balanceBehavior,
        defaultAllocatedAmountCents = defaultAllocatedAmountCents,
        sortOrder = sortOrder,
        originInstallId = originInstallId,
        lastModifiedByInstallId = lastModifiedByInstallId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        closedAtEpochMs = closedAtEpochMs,
        deletedAtEpochMs = deletedAtEpochMs,
        modClock = modClock
    )

    val isClosed: Boolean
        get() = closedAtEpochMs != null || deletedAtEpochMs != null
}
