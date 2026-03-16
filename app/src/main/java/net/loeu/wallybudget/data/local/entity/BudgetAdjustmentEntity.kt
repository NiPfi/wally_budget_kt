package net.loeu.wallybudget.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import net.loeu.wallybudget.domain.model.BudgetAdjustment

@Entity(
    tableName = "budget_adjustments",
    indices = [
        Index(value = ["cycleStartDate"]),
        Index(value = ["effectiveDate"]),
        Index(value = ["deletedAtEpochMs"]),
        Index(value = ["adjustmentUuid"], unique = true)
    ]
)
data class BudgetAdjustmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val adjustmentUuid: String,
    val cycleStartDate: String,
    val effectiveDate: String,
    val previousMonthlyBudgetCents: Long,
    val newMonthlyBudgetCents: Long,
    val originInstallId: String,
    val lastModifiedByInstallId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val deletedAtEpochMs: Long? = null,
    val modClock: String
)

fun BudgetAdjustmentEntity.toDomainModel(): BudgetAdjustment {
    return BudgetAdjustment(
        adjustmentUuid = adjustmentUuid,
        cycleStartDate = cycleStartDate,
        effectiveDate = effectiveDate,
        previousMonthlyBudgetCents = previousMonthlyBudgetCents,
        newMonthlyBudgetCents = newMonthlyBudgetCents,
        originInstallId = originInstallId,
        lastModifiedByInstallId = lastModifiedByInstallId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        deletedAtEpochMs = deletedAtEpochMs,
        modClock = modClock
    )
}

fun BudgetAdjustment.toEntity(id: Long = 0L): BudgetAdjustmentEntity {
    return BudgetAdjustmentEntity(
        id = id,
        adjustmentUuid = adjustmentUuid,
        cycleStartDate = cycleStartDate,
        effectiveDate = effectiveDate,
        previousMonthlyBudgetCents = previousMonthlyBudgetCents,
        newMonthlyBudgetCents = newMonthlyBudgetCents,
        originInstallId = originInstallId,
        lastModifiedByInstallId = lastModifiedByInstallId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        deletedAtEpochMs = deletedAtEpochMs,
        modClock = modClock
    )
}
