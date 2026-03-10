package net.loeu.wallybudget.domain.model

import java.time.LocalDate

data class HomeOverviewState(
    val effectiveCurrentDate: LocalDate,
    val budgetState: BudgetState,
    val todayExpenses: List<Expense>,
    val activeCycleExpenseSections: List<ExpenseDaySection>,
    val pendingCycleCloseoutState: PendingCycleCloseoutState?,
    val timelineLockState: TimelineLockState
)
