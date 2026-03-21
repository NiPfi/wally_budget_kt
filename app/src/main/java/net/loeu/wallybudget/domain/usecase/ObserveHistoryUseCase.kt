@file:Suppress("LongMethod")

package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import net.loeu.wallybudget.data.local.dao.BucketAllocationPolicyDao
import net.loeu.wallybudget.data.local.dao.BucketMonthlyHistoryDao
import net.loeu.wallybudget.data.local.dao.BudgetBucketDao
import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.dao.MonthlyHistoryDao
import net.loeu.wallybudget.data.local.entity.toDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel as bucketHistoryToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel as bucketPolicyToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel as bucketToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel as policyToDomainModel
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.BudgetPolicy
import net.loeu.wallybudget.domain.model.CycleBucketSummary
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.ExpenseCycleSection
import net.loeu.wallybudget.domain.model.ExpenseDaySection
import net.loeu.wallybudget.domain.model.HistoryState
import net.loeu.wallybudget.domain.model.MonthlyHistory
import net.loeu.wallybudget.domain.model.PortfolioOverviewState
import net.loeu.wallybudget.domain.model.groupByDate
import net.loeu.wallybudget.domain.model.recordedDate
import net.loeu.wallybudget.domain.model.sumByDate
import net.loeu.wallybudget.domain.usecase.internal.buildContinuousDaySections
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import java.time.LocalDate

class ObserveHistoryUseCase(
    private val expenseDao: ExpenseDao,
    private val monthlyHistoryDao: MonthlyHistoryDao,
    private val budgetPolicyDao: BudgetPolicyDao,
    private val budgetBucketDao: BudgetBucketDao,
    private val bucketAllocationPolicyDao: BucketAllocationPolicyDao,
    private val bucketMonthlyHistoryDao: BucketMonthlyHistoryDao,
    private val userSettingsStore: UserSettingsStore,
    private val cycleScheduleResolver: CycleScheduleResolver
) {
    operator fun invoke(portfolioOverviewFlow: Flow<PortfolioOverviewState>): Flow<HistoryState> {
        val allExpenses = expenseDao.observeAllOrderedDesc().map { expenses ->
            expenses.map { it.toDomainModel() }
        }
        val history = monthlyHistoryDao.observeAll().map { entries ->
            entries.map { it.toDomainModel() }
        }
        val budgetPolicies = budgetPolicyDao.observeActivePolicies().map { entries ->
            entries.map { it.policyToDomainModel() }
        }
        val allBuckets = budgetBucketDao.observeAll().map { entries ->
            entries.map { it.bucketToDomainModel() }
        }
        val bucketPolicies = bucketAllocationPolicyDao.observeActivePolicies().map { entries ->
            entries.map { it.bucketPolicyToDomainModel() }
        }
        val bucketHistory = bucketMonthlyHistoryDao.observeAll().map { entries ->
            entries.map { it.bucketHistoryToDomainModel() }
        }

        val historySupportingInputs = combine(
            portfolioOverviewFlow,
            allExpenses,
            history,
            budgetPolicies,
            bucketPolicies
        ) { portfolioOverviewState, expenses, historyEntries, budgetPoliciesValue, bucketPoliciesValue ->
            HistorySupportingInputs(
                portfolioOverviewState = portfolioOverviewState,
                expenses = expenses,
                historyEntries = historyEntries,
                budgetPolicies = budgetPoliciesValue,
                bucketPolicies = bucketPoliciesValue
            )
        }

        return combine(
            historySupportingInputs,
            bucketHistory,
            allBuckets,
            userSettingsStore.userSettings
        ) { supportingInputs, bucketHistoryValue, buckets, settings ->
            val portfolioOverviewState = supportingInputs.portfolioOverviewState
            val expenses = supportingInputs.expenses
            val historyEntries = supportingInputs.historyEntries
            val monthlyHistory = historyEntries.sortedByDescending { it.endTimestamp }
            val dayTotals = expenses.sumByDate()
            val bucketNameByUuid = buckets.associate { it.bucketUuid to it.name }

            HistoryState(
                monthlyHistory = monthlyHistory,
                historySections = buildHistorySections(
                    monthlyHistory = monthlyHistory,
                    allExpenses = expenses,
                    dayTotals = dayTotals,
                    portfolioOverviewState = portfolioOverviewState,
                    settings = settings,
                    portfolioPolicies = supportingInputs.budgetPolicies,
                    allBuckets = buckets,
                    bucketPolicies = supportingInputs.bucketPolicies,
                    bucketHistory = bucketHistoryValue
                ),
                bucketNameByUuid = bucketNameByUuid
            )
        }.distinctUntilChanged()
    }

    private fun buildHistorySections(
        monthlyHistory: List<MonthlyHistory>,
        allExpenses: List<Expense>,
        dayTotals: Map<LocalDate, Long>,
        portfolioOverviewState: PortfolioOverviewState,
        settings: net.loeu.wallybudget.domain.model.UserSettings,
        portfolioPolicies: List<BudgetPolicy>,
        allBuckets: List<BudgetBucket>,
        bucketPolicies: List<net.loeu.wallybudget.domain.model.BucketAllocationPolicy>,
        bucketHistory: List<net.loeu.wallybudget.domain.model.BucketMonthlyHistory>
    ): List<ExpenseCycleSection> {
        val sections = mutableListOf<ExpenseCycleSection>()
        val currentCycleStart = portfolioOverviewState.portfolioState.cycleStartDate
        val expensesByDate = allExpenses.groupByDate()
        val currentCycleExpenses = allExpenses.filterByDateRange(
            start = currentCycleStart,
            endExclusive = portfolioOverviewState.effectiveCurrentDate.plusDays(1)
        )
        val bucketNameByUuid = allBuckets.associate { it.bucketUuid to it.name }
        sections += ExpenseCycleSection(
            cycleStartDate = currentCycleStart,
            cycleEndDateExclusive = portfolioOverviewState.effectiveCurrentDate.plusDays(1),
            title = "Current cycle",
            budgetAmountCents = portfolioOverviewState.portfolioState.portfolioTotalBudgetCents,
            totalSpentCents = portfolioOverviewState.portfolioState.totalSpentThisCycleCents,
            surplusCents = portfolioOverviewState.portfolioState.remainingThisCycleCents,
            bucketSummaries = buildCurrentCycleBucketSummaries(
                portfolioOverviewState = portfolioOverviewState,
                currentCycleExpenses = currentCycleExpenses,
                bucketNameByUuid = bucketNameByUuid
            ),
            daySections = buildContinuousDaySections(
                start = currentCycleStart,
                endInclusive = portfolioOverviewState.effectiveCurrentDate,
                expensesByDate = currentCycleExpenses.groupByDate(),
                dayTotals = currentCycleExpenses.sumByDate(),
                remainingBudgetForDay = { null },
                isEditable = !portfolioOverviewState.timelineLockState.isLocked,
                today = portfolioOverviewState.effectiveCurrentDate
            ),
            isActiveCycle = true,
            isReadOnly = portfolioOverviewState.timelineLockState.isLocked,
            isCompletedCycle = false
        )

        val futureSections = buildFutureExpenseSections(
            allExpenses = allExpenses,
            dayTotals = dayTotals,
            today = portfolioOverviewState.effectiveCurrentDate,
            settings = settings,
            portfolioPolicies = portfolioPolicies,
            allBuckets = allBuckets,
            bucketPolicies = bucketPolicies
        )
        if (futureSections.isNotEmpty()) {
            sections += futureSections
        }

        sections += buildCompletedHistorySections(
            monthlyHistory = monthlyHistory,
            currentCycleStart = currentCycleStart,
            expensesByDate = expensesByDate,
            dayTotals = dayTotals,
            bucketHistory = bucketHistory,
            bucketNameByUuid = allBuckets.associate { it.bucketUuid to it.name }
        )

        return sections
    }

    private fun buildCurrentCycleBucketSummaries(
        portfolioOverviewState: PortfolioOverviewState,
        currentCycleExpenses: List<Expense>,
        bucketNameByUuid: Map<String, String>
    ): List<CycleBucketSummary> {
        val activeSummaries = portfolioOverviewState.bucketSummaries.associateBy { it.bucket.bucketUuid }
        val resolvedSummaries = activeSummaries.values.map { summary ->
            CycleBucketSummary(
                bucketUuid = summary.bucket.bucketUuid,
                bucketName = summary.bucket.name,
                spentCents = summary.spentThisCycleCents,
                remainingCents = summary.remainingThisCycleCents,
                overspentCents = summary.overspentCents
            )
        }
        val orphanedExpenseSummaries = currentCycleExpenses
            .groupBy { it.bucketUuid }
            .filterKeys { it !in activeSummaries }
            .map { (bucketUuid, expenses) ->
                val spentCents = expenses.sumOf { it.amountCents }
                CycleBucketSummary(
                    bucketUuid = bucketUuid,
                    bucketName = bucketNameByUuid[bucketUuid] ?: "Deleted bucket",
                    spentCents = spentCents,
                    remainingCents = -spentCents,
                    overspentCents = spentCents
                )
            }
        return (resolvedSummaries + orphanedExpenseSummaries)
            .sortedBy { it.bucketName.lowercase() }
    }

    private fun buildFutureExpenseSections(
        allExpenses: List<Expense>,
        dayTotals: Map<LocalDate, Long>,
        today: LocalDate,
        settings: net.loeu.wallybudget.domain.model.UserSettings,
        portfolioPolicies: List<BudgetPolicy>,
        allBuckets: List<BudgetBucket>,
        bucketPolicies: List<net.loeu.wallybudget.domain.model.BucketAllocationPolicy>
    ): List<ExpenseCycleSection> {
        val futureExpenses = allExpenses.filter { it.recordedDate().isAfter(today) }
        if (futureExpenses.isEmpty()) return emptyList()

        return futureExpenses
            .groupBy { expense ->
                cycleScheduleResolver.resolvePolicyForDate(
                    date = expense.recordedDate(),
                    settings = settings,
                    policies = portfolioPolicies
                )
            }
            .toList()
            .sortedByDescending { (policy, _) -> policy.cycleStart }
            .map { (policy, expensesForPolicy) ->
                val cycleBucketSummaries = expensesForPolicy
                    .groupBy { it.bucketUuid }
                    .map { (bucketUuid, bucketExpenses) ->
                        val spent = bucketExpenses.sumOf { it.amountCents }
                        val bucket = allBuckets.firstOrNull { it.bucketUuid == bucketUuid }
                        val allocated = bucketPolicies.firstOrNull {
                            it.bucketUuid == bucketUuid &&
                                it.deletedAtEpochMs == null &&
                                it.cycleStart() == policy.cycleStart &&
                                it.cycleEndExclusive() == policy.cycleEndExclusive
                        }?.allocatedAmountCents ?: bucket?.defaultAllocatedAmountCents ?: 0L
                        val remaining = allocated - spent
                        CycleBucketSummary(
                            bucketUuid = bucketUuid,
                            bucketName = bucket?.name ?: "Unknown bucket",
                            spentCents = spent,
                            remainingCents = remaining,
                            overspentCents = (-remaining).coerceAtLeast(0L)
                        )
                    }
                    .sortedBy { it.bucketName.lowercase() }
                ExpenseCycleSection(
                    cycleStartDate = policy.cycleStart,
                    cycleEndDateExclusive = policy.cycleEndExclusive,
                    title = "Upcoming ${policy.cycleStart} - ${policy.cycleEndExclusive.minusDays(1)}",
                    budgetAmountCents = policy.budgetAmountCents,
                    totalSpentCents = expensesForPolicy.sumOf { it.amountCents },
                    surplusCents = policy.budgetAmountCents - expensesForPolicy.sumOf { it.amountCents },
                    bucketSummaries = cycleBucketSummaries,
                    daySections = buildReadOnlyDaySections(expensesForPolicy, dayTotals),
                    isActiveCycle = false,
                    isReadOnly = true,
                    isCompletedCycle = false
                )
            }
    }

    private fun buildCompletedHistorySections(
        monthlyHistory: List<MonthlyHistory>,
        currentCycleStart: LocalDate,
        expensesByDate: Map<LocalDate, List<Expense>>,
        dayTotals: Map<LocalDate, Long>,
        bucketHistory: List<net.loeu.wallybudget.domain.model.BucketMonthlyHistory>,
        bucketNameByUuid: Map<String, String>
    ): List<ExpenseCycleSection> {
        return monthlyHistory
            .filterNot { it.getCycleStart() == currentCycleStart }
            .sortedByDescending { it.endTimestamp }
            .map { monthlyEntry ->
                val cycleExpenses = expensesByDate
                    .filterKeys { date ->
                        !date.isBefore(monthlyEntry.getCycleStart()) &&
                            date.isBefore(monthlyEntry.getCycleEnd())
                    }
                    .values
                    .flatten()
                val cycleBucketSummaries = bucketHistory
                    .filter { it.cycleStartDate == monthlyEntry.cycleStartDate }
                    .map { historyEntry ->
                        CycleBucketSummary(
                            bucketUuid = historyEntry.bucketUuid,
                            bucketName = bucketNameByUuid[historyEntry.bucketUuid] ?: "Unknown bucket",
                            spentCents = historyEntry.totalSpentCents,
                            remainingCents = historyEntry.surplusCents,
                            overspentCents = (-historyEntry.surplusCents).coerceAtLeast(0L)
                        )
                    }
                    .sortedBy { it.bucketName.lowercase() }

                ExpenseCycleSection(
                    cycleStartDate = monthlyEntry.getCycleStart(),
                    cycleEndDateExclusive = monthlyEntry.getCycleEnd(),
                    title = monthlyEntry.getDisplayName(),
                    budgetAmountCents = monthlyEntry.budgetAmountCents,
                    totalSpentCents = monthlyEntry.totalSpentCents,
                    surplusCents = monthlyEntry.surplusCents,
                    bucketSummaries = cycleBucketSummaries,
                    daySections = buildReadOnlyDaySections(cycleExpenses, dayTotals),
                    isActiveCycle = false,
                    isReadOnly = true,
                    isCompletedCycle = true
                )
            }
    }

    private fun buildReadOnlyDaySections(
        expenses: List<Expense>,
        dayTotals: Map<LocalDate, Long>
    ): List<ExpenseDaySection> {
        return expenses
            .groupByDate()
            .toSortedMap(compareByDescending { it })
            .map { (date, groupedExpenses) ->
                ExpenseDaySection(
                    date = date,
                    expenses = groupedExpenses,
                    totalSpentCents = dayTotals[date] ?: groupedExpenses.sumOf { it.amountCents },
                    remainingForDayCents = null,
                    isEditable = false
                )
            }
    }
}

private data class HistorySupportingInputs(
    val portfolioOverviewState: PortfolioOverviewState,
    val expenses: List<Expense>,
    val historyEntries: List<MonthlyHistory>,
    val budgetPolicies: List<BudgetPolicy>,
    val bucketPolicies: List<net.loeu.wallybudget.domain.model.BucketAllocationPolicy>
)

private fun List<Expense>.filterByDateRange(
    start: LocalDate,
    endExclusive: LocalDate
): List<Expense> {
    return filter { expense ->
        val expenseDate = expense.recordedDate()
        !expenseDate.isBefore(start) && expenseDate.isBefore(endExclusive)
    }
}
