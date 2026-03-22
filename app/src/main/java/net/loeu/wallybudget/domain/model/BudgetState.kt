package net.loeu.wallybudget.domain.model

import java.time.LocalDate

/**
 * Budget state for the current cycle
 */
data class BudgetState(
    val monthlyBudgetCents: Long,
    val totalSpentThisCycleCents: Long,
    val dailyBudgetCents: Long,
    val spentTodayCents: Long,
    val remainingTodayCents: Long,
    val daysRemainingInCycle: Int,
    @Deprecated("Savings are modeled through funds.")
    val cumulativeSavingsCents: Long = 0L,
    val paydayDate: Int,
    val cycleStartDate: LocalDate
) {
    val remainingCycleCents: Long
        get() = monthlyBudgetCents - totalSpentThisCycleCents
}
