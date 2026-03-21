package net.loeu.wallybudget.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import net.loeu.wallybudget.domain.model.BucketAllocationPolicy

@Entity(
    tableName = "bucket_allocation_policies",
    indices = [
        Index(value = ["allocationUuid"], unique = true),
        Index(value = ["bucketUuid"]),
        Index(value = ["cycleStartDate"]),
        Index(value = ["cycleEndDateExclusive"]),
        Index(value = ["deletedAtEpochMs"])
    ]
)
data class BucketAllocationPolicyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val allocationUuid: String,
    val bucketUuid: String,
    val cycleStartDate: String,
    val cycleEndDateExclusive: String,
    val allocatedAmountCents: Long,
    val originInstallId: String,
    val lastModifiedByInstallId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val deletedAtEpochMs: Long? = null,
    val modClock: String
)

fun BucketAllocationPolicyEntity.toDomainModel(): BucketAllocationPolicy {
    return BucketAllocationPolicy(
        allocationUuid = allocationUuid,
        bucketUuid = bucketUuid,
        cycleStartDate = cycleStartDate,
        cycleEndDateExclusive = cycleEndDateExclusive,
        allocatedAmountCents = allocatedAmountCents,
        originInstallId = originInstallId,
        lastModifiedByInstallId = lastModifiedByInstallId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        deletedAtEpochMs = deletedAtEpochMs,
        modClock = modClock
    )
}

fun BucketAllocationPolicy.toEntity(id: Long = 0L): BucketAllocationPolicyEntity {
    return BucketAllocationPolicyEntity(
        id = id,
        allocationUuid = allocationUuid,
        bucketUuid = bucketUuid,
        cycleStartDate = cycleStartDate,
        cycleEndDateExclusive = cycleEndDateExclusive,
        allocatedAmountCents = allocatedAmountCents,
        originInstallId = originInstallId,
        lastModifiedByInstallId = lastModifiedByInstallId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        deletedAtEpochMs = deletedAtEpochMs,
        modClock = modClock
    )
}
