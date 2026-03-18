package net.loeu.wallybudget.domain.model

import java.time.LocalDate

data class PortfolioState(
    val portfolioTotalBudgetCents: Long,
    val allocatedToBucketsCents: Long,
    val unassignedPlannedBudgetCents: Long,
    val totalSpentThisCycleCents: Long,
    val remainingThisCycleCents: Long,
    val completedCycleReserveCents: Long,
    val netReserveCents: Long,
    val earmarkedReserveCents: Long,
    val unassignedReserveCents: Long,
    val cycleStartDate: LocalDate,
    val cycleEndDateExclusive: LocalDate
)

data class BucketSummaryState(
    val bucket: BudgetBucket,
    val allocatedThisCycleCents: Long,
    val spentThisCycleCents: Long,
    val remainingThisCycleCents: Long,
    val overspentCents: Long,
    val earmarkedBalanceCents: Long,
    val budgetState: BudgetState? = null
)

data class SelectedBucketOverview(
    val bucket: BudgetBucket,
    val summary: BucketSummaryState,
    val budgetState: BudgetState?,
    val todayExpenses: List<Expense>,
    val activeCycleExpenseSections: List<ExpenseDaySection>,
    val spendingForecast: SpendingForecast?
)

data class PortfolioOverviewState(
    val effectiveCurrentDate: LocalDate,
    val portfolioState: PortfolioState,
    val bucketSummaries: List<BucketSummaryState>,
    val selectedBucketOverview: SelectedBucketOverview,
    val pendingCycleCloseoutState: PendingCycleCloseoutState?,
    val timelineLockState: TimelineLockState
)
