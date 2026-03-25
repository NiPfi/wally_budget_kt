package net.loeu.wallybudget.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import net.loeu.wallybudget.domain.model.BucketCycleBaseline

@Entity(
    tableName = "bucket_cycle_baselines",
    indices = [
        Index(value = ["baselineUuid"], unique = true),
        Index(value = ["bucketUuid"]),
        Index(value = ["cycleStartDate"]),
        Index(value = ["cycleEndDateExclusive"]),
        Index(value = ["deletedAtEpochMs"])
    ]
)
data class BucketCycleBaselineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val baselineUuid: String,
    val bucketUuid: String,
    val cycleStartDate: String,
    val cycleEndDateExclusive: String,
    val baselineAmountCents: Long,
    val originInstallId: String,
    val lastModifiedByInstallId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val deletedAtEpochMs: Long? = null,
    val modClock: String
)

fun BucketCycleBaselineEntity.toDomainModel(): BucketCycleBaseline {
    return BucketCycleBaseline(
        baselineUuid = baselineUuid,
        bucketUuid = bucketUuid,
        cycleStartDate = cycleStartDate,
        cycleEndDateExclusive = cycleEndDateExclusive,
        baselineAmountCents = baselineAmountCents,
        originInstallId = originInstallId,
        lastModifiedByInstallId = lastModifiedByInstallId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        deletedAtEpochMs = deletedAtEpochMs,
        modClock = modClock
    )
}

fun BucketCycleBaseline.toEntity(id: Long = 0L): BucketCycleBaselineEntity {
    return BucketCycleBaselineEntity(
        id = id,
        baselineUuid = baselineUuid,
        bucketUuid = bucketUuid,
        cycleStartDate = cycleStartDate,
        cycleEndDateExclusive = cycleEndDateExclusive,
        baselineAmountCents = baselineAmountCents,
        originInstallId = originInstallId,
        lastModifiedByInstallId = lastModifiedByInstallId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        deletedAtEpochMs = deletedAtEpochMs,
        modClock = modClock
    )
}
