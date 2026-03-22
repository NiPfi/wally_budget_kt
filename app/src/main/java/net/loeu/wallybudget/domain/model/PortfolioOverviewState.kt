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
    @Deprecated("Bucket-level reserve semantics were removed.")
    val earmarkedReserveCents: Long = 0L,
    @Deprecated("Bucket-level reserve semantics were removed.")
    val unassignedReserveCents: Long = netReserveCents,
    val cycleStartDate: LocalDate,
    val cycleEndDateExclusive: LocalDate,
    val allocatedToFundsCents: Long = 0L,
    val totalFundBalanceCents: Long = 0L
)

data class BucketSummaryState(
    val bucket: BudgetBucket,
    val allocatedThisCycleCents: Long,
    val spentThisCycleCents: Long,
    val remainingThisCycleCents: Long,
    val overspentCents: Long,
    val budgetState: BudgetState? = null,
    @Deprecated("Bucket-level reserve semantics were removed.")
    val earmarkedBalanceCents: Long = 0L
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
    val fundStates: List<FundState> = emptyList(),
    val bucketSummaries: List<BucketSummaryState>,
    val selectedBucketOverview: SelectedBucketOverview,
    val pendingCycleCloseoutState: PendingCycleCloseoutState?,
    val timelineLockState: TimelineLockState
)
