package net.loeu.wallybudget.data.local.entity

import androidx.room.Entity
import net.loeu.wallybudget.domain.model.MonthlyHistory

/**
 * Historical record of monthly budget cycles.
 * 
 * INVARIANT: cycleEndDate is EXCLUSIVE (it is the start date of the next cycle).
 * This ensures that ChronoUnit.DAYS.between(cycleStart, cycleEnd) correctly calculates 
 * the total number of days in the cycle.
 */
@Entity(
    tableName = "monthly_history",
    primaryKeys = ["cycleStartDate"]
)
data class MonthlyHistoryEntity(
    val cycleStartDate: String, // ISO date format (YYYY-MM-DD) - unique identifier for the cycle
    val budgetAmountCents: Long,
    val totalSpentCents: Long,
    val surplusCents: Long, // positive if under budget, negative if over
    val cycleEndDate: String, // ISO date format (YYYY-MM-DD) - Exclusive (start of the next cycle)
    val endTimestamp: Long // When this cycle ended
)

fun MonthlyHistoryEntity.toDomainModel(): MonthlyHistory {
    return MonthlyHistory(
        cycleStartDate = cycleStartDate,
        budgetAmountCents = budgetAmountCents,
        totalSpentCents = totalSpentCents,
        surplusCents = surplusCents,
        cycleEndDate = cycleEndDate,
        endTimestamp = endTimestamp
    )
}

fun MonthlyHistory.toEntity(): MonthlyHistoryEntity {
    return MonthlyHistoryEntity(
        cycleStartDate = cycleStartDate,
        budgetAmountCents = budgetAmountCents,
        totalSpentCents = totalSpentCents,
        surplusCents = surplusCents,
        cycleEndDate = cycleEndDate,
        endTimestamp = endTimestamp
    )
}
