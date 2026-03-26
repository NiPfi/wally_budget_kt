package net.loeu.wallybudget.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import net.loeu.wallybudget.data.local.db.LegacyBucketBalanceBehavior
import net.loeu.wallybudget.data.local.db.LegacyBucketTrackingMode
import net.loeu.wallybudget.domain.model.BudgetBucket

@Entity(
    tableName = "budget_buckets",
    indices = [
        Index(value = ["bucketUuid"], unique = true),
        Index(value = ["sortOrder"]),
        Index(value = ["closedAtEpochMs"]),
        Index(value = ["deletedAtEpochMs"])
    ]
)
data class BudgetBucketEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bucketUuid: String,
    val name: String,
    val trackingMode: LegacyBucketTrackingMode = LegacyBucketTrackingMode.DAILY_TARGET,
    val balanceBehavior: LegacyBucketBalanceBehavior = LegacyBucketBalanceBehavior.RETURN_TO_PORTFOLIO,
    @ColumnInfo(defaultValue = "0")
    val defaultAllocatedAmountCents: Long,
    val sortOrder: Int,
    val originInstallId: String,
    val lastModifiedByInstallId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val settledCloseCycleEndDateExclusive: String? = null,
    val closedAtEpochMs: Long? = null,
    val deletedAtEpochMs: Long? = null,
    val modClock: String,
    @ColumnInfo(defaultValue = "0")
    val monthScoped: Boolean = false
) {
    @Suppress("UNUSED_PARAMETER")
    constructor(
        id: Long = 0,
        bucketUuid: String,
        name: String,
        trackingMode: net.loeu.wallybudget.domain.model.BucketTrackingMode,
        balanceBehavior: net.loeu.wallybudget.domain.model.BucketBalanceBehavior,
        defaultAllocatedAmountCents: Long,
        sortOrder: Int,
        originInstallId: String,
        lastModifiedByInstallId: String,
        createdAtEpochMs: Long,
        updatedAtEpochMs: Long,
        settledCloseCycleEndDateExclusive: String? = null,
        closedAtEpochMs: Long? = null,
        deletedAtEpochMs: Long? = null,
        modClock: String
    ) : this(
        id = id,
        bucketUuid = bucketUuid,
        name = name,
        trackingMode = LegacyBucketTrackingMode.valueOf(trackingMode.name),
        balanceBehavior = LegacyBucketBalanceBehavior.valueOf(balanceBehavior.name),
        defaultAllocatedAmountCents = defaultAllocatedAmountCents,
        sortOrder = sortOrder,
        originInstallId = originInstallId,
        lastModifiedByInstallId = lastModifiedByInstallId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        settledCloseCycleEndDateExclusive = settledCloseCycleEndDateExclusive,
        closedAtEpochMs = closedAtEpochMs,
        deletedAtEpochMs = deletedAtEpochMs,
        modClock = modClock
    )
}

fun BudgetBucketEntity.toDomainModel(): BudgetBucket {
    return BudgetBucket(
        bucketUuid = bucketUuid,
        name = name,
        defaultAllocatedAmountCents = defaultAllocatedAmountCents,
        sortOrder = sortOrder,
        originInstallId = originInstallId,
        lastModifiedByInstallId = lastModifiedByInstallId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        settledCloseCycleEndDateExclusive = settledCloseCycleEndDateExclusive,
        closedAtEpochMs = closedAtEpochMs,
        deletedAtEpochMs = deletedAtEpochMs,
        modClock = modClock,
        monthScoped = monthScoped
    )
}

fun BudgetBucket.toEntity(id: Long = 0L): BudgetBucketEntity {
    return BudgetBucketEntity(
        id = id,
        bucketUuid = bucketUuid,
        name = name,
        defaultAllocatedAmountCents = defaultAllocatedAmountCents,
        sortOrder = sortOrder,
        originInstallId = originInstallId,
        lastModifiedByInstallId = lastModifiedByInstallId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        settledCloseCycleEndDateExclusive = settledCloseCycleEndDateExclusive,
        closedAtEpochMs = closedAtEpochMs,
        deletedAtEpochMs = deletedAtEpochMs,
        modClock = modClock,
        monthScoped = monthScoped
    )
}
