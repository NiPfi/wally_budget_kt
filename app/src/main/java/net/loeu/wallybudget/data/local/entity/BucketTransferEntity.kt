package net.loeu.wallybudget.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import net.loeu.wallybudget.domain.model.BucketTransfer
import net.loeu.wallybudget.domain.model.BucketTransferReason

@Entity(
    tableName = "bucket_transfers",
    indices = [
        Index(value = ["transferUuid"], unique = true),
        Index(value = ["cycleStartDate"]),
        Index(value = ["fromBucketUuid"]),
        Index(value = ["toBucketUuid"]),
        Index(value = ["deletedAtEpochMs"])
    ]
)
data class BucketTransferEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val transferUuid: String,
    val fromBucketUuid: String?,
    val toBucketUuid: String?,
    val amountCents: Long,
    val reason: BucketTransferReason,
    val cycleStartDate: String,
    val cycleEndDateExclusive: String,
    val effectiveDate: String,
    val originInstallId: String,
    val lastModifiedByInstallId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val deletedAtEpochMs: Long? = null,
    val modClock: String
)

fun BucketTransferEntity.toDomainModel(): BucketTransfer {
    return BucketTransfer(
        transferUuid = transferUuid,
        fromBucketUuid = fromBucketUuid,
        toBucketUuid = toBucketUuid,
        amountCents = amountCents,
        reason = reason,
        cycleStartDate = cycleStartDate,
        cycleEndDateExclusive = cycleEndDateExclusive,
        effectiveDate = effectiveDate,
        originInstallId = originInstallId,
        lastModifiedByInstallId = lastModifiedByInstallId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        deletedAtEpochMs = deletedAtEpochMs,
        modClock = modClock
    )
}

fun BucketTransfer.toEntity(id: Long = 0L): BucketTransferEntity {
    return BucketTransferEntity(
        id = id,
        transferUuid = transferUuid,
        fromBucketUuid = fromBucketUuid,
        toBucketUuid = toBucketUuid,
        amountCents = amountCents,
        reason = reason,
        cycleStartDate = cycleStartDate,
        cycleEndDateExclusive = cycleEndDateExclusive,
        effectiveDate = effectiveDate,
        originInstallId = originInstallId,
        lastModifiedByInstallId = lastModifiedByInstallId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        deletedAtEpochMs = deletedAtEpochMs,
        modClock = modClock
    )
}
