package net.loeu.wallybudget.data.model

import java.time.LocalDate

/**
 * Budget state for the current cycle
 */
data class BudgetState(
    val monthlyBudget: Double,
    val totalSpentThisCycle: Double,
    val dailyBudget: Double,
    val spentToday: Double,
    val remainingToday: Double,
    val daysRemainingInCycle: Int,
    val cumulativeSavings: Double, // Overall savings/deficit across all months
    val paydayDate: Int,
    val cycleStartDate: LocalDate
)

