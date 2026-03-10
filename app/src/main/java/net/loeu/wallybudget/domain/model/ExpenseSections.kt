package net.loeu.wallybudget.domain.model

import java.time.LocalDate

data class ExpenseDaySection(
    val date: LocalDate,
    val expenses: List<Expense>,
    val totalSpentCents: Long,
    val remainingForDayCents: Long? = null,
    val isToday: Boolean = false,
    val isEditable: Boolean = false
)

data class ExpenseCycleSection(
    val cycleStartDate: LocalDate,
    val cycleEndDateExclusive: LocalDate,
    val title: String,
    val budgetAmountCents: Long,
    val totalSpentCents: Long,
    val surplusCents: Long,
    val daySections: List<ExpenseDaySection>,
    val isActiveCycle: Boolean,
    val isReadOnly: Boolean,
    val isCompletedCycle: Boolean = false
)

data class PendingCycleCloseoutState(
    val cycleStartDate: LocalDate,
    val cycleEndDateExclusive: LocalDate,
    val budgetAmountCents: Long,
    val totalSpentCents: Long,
    val surplusCents: Long,
    val averageDailySpendCents: Long,
    val biggestExpense: Expense?,
    val highestSpendDay: LocalDate?,
    val topCategory: ExpenseCategory?,
    val trendSummary: String,
    val daySections: List<ExpenseDaySection>
)
