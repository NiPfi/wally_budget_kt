package net.loeu.wallybudget.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.BucketBalanceBehavior
import net.loeu.wallybudget.domain.model.BucketTrackingMode

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
    val trackingMode: BucketTrackingMode,
    val balanceBehavior: BucketBalanceBehavior,
    @ColumnInfo(defaultValue = "0")
    val defaultAllocatedAmountCents: Long,
    val sortOrder: Int,
    val originInstallId: String,
    val lastModifiedByInstallId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val closedAtEpochMs: Long? = null,
    val deletedAtEpochMs: Long? = null,
    val modClock: String
)

fun BudgetBucketEntity.toDomainModel(): BudgetBucket {
    return BudgetBucket(
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
}

fun BudgetBucket.toEntity(id: Long = 0L): BudgetBucketEntity {
    return BudgetBucketEntity(
        id = id,
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
}
