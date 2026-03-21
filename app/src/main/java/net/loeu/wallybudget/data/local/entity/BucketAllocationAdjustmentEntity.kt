package net.loeu.wallybudget.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import net.loeu.wallybudget.domain.model.BucketAllocationAdjustment

@Entity(
    tableName = "bucket_allocation_adjustments",
    indices = [
        Index(value = ["adjustmentUuid"], unique = true),
        Index(value = ["bucketUuid"]),
        Index(value = ["cycleStartDate"]),
        Index(value = ["effectiveDate"]),
        Index(value = ["deletedAtEpochMs"])
    ]
)
data class BucketAllocationAdjustmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val adjustmentUuid: String,
    val bucketUuid: String,
    val cycleStartDate: String,
    val effectiveDate: String,
    val previousAllocatedAmountCents: Long,
    val newAllocatedAmountCents: Long,
    val originInstallId: String,
    val lastModifiedByInstallId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val deletedAtEpochMs: Long? = null,
    val modClock: String
)

fun BucketAllocationAdjustmentEntity.toDomainModel(): BucketAllocationAdjustment {
    return BucketAllocationAdjustment(
        adjustmentUuid = adjustmentUuid,
        bucketUuid = bucketUuid,
        cycleStartDate = cycleStartDate,
        effectiveDate = effectiveDate,
        previousAllocatedAmountCents = previousAllocatedAmountCents,
        newAllocatedAmountCents = newAllocatedAmountCents,
        originInstallId = originInstallId,
        lastModifiedByInstallId = lastModifiedByInstallId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        deletedAtEpochMs = deletedAtEpochMs,
        modClock = modClock
    )
}

fun BucketAllocationAdjustment.toEntity(id: Long = 0L): BucketAllocationAdjustmentEntity {
    return BucketAllocationAdjustmentEntity(
        id = id,
        adjustmentUuid = adjustmentUuid,
        bucketUuid = bucketUuid,
        cycleStartDate = cycleStartDate,
        effectiveDate = effectiveDate,
        previousAllocatedAmountCents = previousAllocatedAmountCents,
        newAllocatedAmountCents = newAllocatedAmountCents,
        originInstallId = originInstallId,
        lastModifiedByInstallId = lastModifiedByInstallId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        deletedAtEpochMs = deletedAtEpochMs,
        modClock = modClock
    )
}
