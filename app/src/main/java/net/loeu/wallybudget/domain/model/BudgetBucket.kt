package net.loeu.wallybudget.domain.model

const val DEFAULT_SPENDING_BUCKET_UUID = "00000000-0000-0000-0000-000000000001"
const val DEFAULT_SPENDING_BUCKET_NAME = "Spending money"

data class BudgetBucket(
    val bucketUuid: String,
    val name: String,
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

    @Suppress("UNUSED_PARAMETER")
    constructor(
        bucketUuid: String,
        name: String,
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

    @Deprecated("Buckets are always spending buckets.")
    val trackingMode: BucketTrackingMode
        get() = BucketTrackingMode.DAILY_TARGET

    @Deprecated("Bucket balance behavior is no longer used.")
    val balanceBehavior: BucketBalanceBehavior
        get() = BucketBalanceBehavior.RETURN_TO_PORTFOLIO
}
