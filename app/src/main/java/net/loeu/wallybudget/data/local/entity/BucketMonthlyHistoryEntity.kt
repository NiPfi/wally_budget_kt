package net.loeu.wallybudget.data.local.entity

import androidx.room.Entity
import net.loeu.wallybudget.domain.model.BucketMonthlyHistory

@Entity(
    tableName = "bucket_monthly_history",
    primaryKeys = ["bucketUuid", "cycleStartDate"]
)
data class BucketMonthlyHistoryEntity(
    val bucketUuid: String,
    val cycleStartDate: String,
    val budgetAmountCents: Long,
    val totalSpentCents: Long,
    val surplusCents: Long,
    val cycleEndDate: String,
    val endTimestamp: Long
)

fun BucketMonthlyHistoryEntity.toDomainModel(): BucketMonthlyHistory {
    return BucketMonthlyHistory(
        bucketUuid = bucketUuid,
        cycleStartDate = cycleStartDate,
        budgetAmountCents = budgetAmountCents,
        totalSpentCents = totalSpentCents,
        surplusCents = surplusCents,
        cycleEndDate = cycleEndDate,
        endTimestamp = endTimestamp
    )
}

fun BucketMonthlyHistory.toEntity(): BucketMonthlyHistoryEntity {
    return BucketMonthlyHistoryEntity(
        bucketUuid = bucketUuid,
        cycleStartDate = cycleStartDate,
        budgetAmountCents = budgetAmountCents,
        totalSpentCents = totalSpentCents,
        surplusCents = surplusCents,
        cycleEndDate = cycleEndDate,
        endTimestamp = endTimestamp
    )
}
