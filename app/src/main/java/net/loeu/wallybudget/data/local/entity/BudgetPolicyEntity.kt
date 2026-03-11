package net.loeu.wallybudget.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import net.loeu.wallybudget.domain.model.BudgetPolicy

@Entity(
    tableName = "budget_policies",
    indices = [
        Index(value = ["cycleStartDate"]),
        Index(value = ["cycleEndDateExclusive"]),
        Index(value = ["deletedAtEpochMs"]),
        Index(value = ["policyUuid"], unique = true)
    ]
)
data class BudgetPolicyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val policyUuid: String,
    val cycleStartDate: String,
    val cycleEndDateExclusive: String,
    val budgetAmountCents: Long,
    val paydayDayOfMonth: Int,
    val originInstallId: String,
    val lastModifiedByInstallId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val deletedAtEpochMs: Long? = null,
    val modClock: String
)

fun BudgetPolicyEntity.toDomainModel(): BudgetPolicy {
    return BudgetPolicy(
        policyUuid = policyUuid,
        cycleStartDate = cycleStartDate,
        cycleEndDateExclusive = cycleEndDateExclusive,
        budgetAmountCents = budgetAmountCents,
        paydayDayOfMonth = paydayDayOfMonth,
        originInstallId = originInstallId,
        lastModifiedByInstallId = lastModifiedByInstallId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        deletedAtEpochMs = deletedAtEpochMs,
        modClock = modClock
    )
}

fun BudgetPolicy.toEntity(id: Long = 0L): BudgetPolicyEntity {
    return BudgetPolicyEntity(
        id = id,
        policyUuid = policyUuid,
        cycleStartDate = cycleStartDate,
        cycleEndDateExclusive = cycleEndDateExclusive,
        budgetAmountCents = budgetAmountCents,
        paydayDayOfMonth = paydayDayOfMonth,
        originInstallId = originInstallId,
        lastModifiedByInstallId = lastModifiedByInstallId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        deletedAtEpochMs = deletedAtEpochMs,
        modClock = modClock
    )
}
