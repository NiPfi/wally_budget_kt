package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.dao.MonthlyHistoryDao
import net.loeu.wallybudget.data.local.entity.toDomainModel as policyToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.domain.model.BudgetPolicy
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.ExpenseCycleSection
import net.loeu.wallybudget.domain.model.ExpenseDaySection
import net.loeu.wallybudget.domain.model.HomeOverviewState
import net.loeu.wallybudget.domain.model.HistoryState
import net.loeu.wallybudget.domain.model.MonthlyHistory
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
    private val userSettingsStore: UserSettingsStore,
    private val cycleScheduleResolver: CycleScheduleResolver
) {
    operator fun invoke(homeOverviewFlow: Flow<HomeOverviewState>): Flow<HistoryState> {
        val allExpenses = expenseDao.observeAllOrderedDesc().map { expenses ->
            expenses.map { it.toDomainModel() }
        }
        val history = monthlyHistoryDao.observeAll().map { entries ->
            entries.map { it.toDomainModel() }
        }
        val budgetPolicies = budgetPolicyDao.observeActivePolicies().map { entries ->
            entries.map { it.policyToDomainModel() }
        }

        return combine(
            homeOverviewFlow,
            allExpenses,
            history,
            budgetPolicies,
            userSettingsStore.userSettings
        ) { homeOverviewState, expenses, historyEntries, budgetPolicies, settings ->
            val monthlyHistory = historyEntries
                .sortedByDescending { it.endTimestamp }
            val dayTotals = expenses.sumByDate()

            HistoryState(
                monthlyHistory = monthlyHistory,
                historySections = buildHistorySections(
                    monthlyHistory = monthlyHistory,
                    allExpenses = expenses,
                    dayTotals = dayTotals,
                    budgetState = homeOverviewState.budgetState,
                    today = homeOverviewState.effectiveCurrentDate,
                    settings = settings,
                    budgetPolicies = budgetPolicies,
                    isEditable = !homeOverviewState.timelineLockState.isLocked
                )
            )
        }.distinctUntilChanged()
    }

    private fun buildHistorySections(
        monthlyHistory: List<MonthlyHistory>,
        allExpenses: List<Expense>,
        dayTotals: Map<LocalDate, Long>,
        budgetState: BudgetState,
        today: LocalDate,
        settings: net.loeu.wallybudget.domain.model.UserSettings,
        budgetPolicies: List<BudgetPolicy>,
        isEditable: Boolean
    ): List<ExpenseCycleSection> {
        val sections = mutableListOf<ExpenseCycleSection>()
        val currentCycleStart = budgetState.cycleStartDate
        val expensesByDate = allExpenses.groupByDate()
        val activeCycleDaySections = buildContinuousDaySections(
            start = currentCycleStart,
            endInclusive = today,
            expensesByDate = expensesByDate,
            dayTotals = dayTotals,
            remainingBudgetForDay = { totalSpent -> budgetState.dailyBudgetCents - totalSpent },
            isEditable = isEditable,
            today = today
        )
        sections += buildCurrentCycleSection(
            budgetState = budgetState,
            currentCycleStart = currentCycleStart,
            today = today,
            activeCycleDaySections = activeCycleDaySections,
            isEditable = isEditable
        )

        val futureSections = buildFutureExpenseSections(
            allExpenses = allExpenses,
            dayTotals = dayTotals,
            today = today,
            settings = settings,
            budgetPolicies = budgetPolicies
        )
        if (futureSections.isNotEmpty()) {
            sections += futureSections
        }

        sections += buildCompletedHistorySections(monthlyHistory, currentCycleStart, expensesByDate, dayTotals)

        return sections
    }

    private fun buildCurrentCycleSection(
        budgetState: BudgetState,
        currentCycleStart: LocalDate,
        today: LocalDate,
        activeCycleDaySections: List<ExpenseDaySection>,
        isEditable: Boolean
    ): ExpenseCycleSection {
        return ExpenseCycleSection(
            cycleStartDate = currentCycleStart,
            cycleEndDateExclusive = today.plusDays(1),
            title = "Current cycle",
            budgetAmountCents = budgetState.monthlyBudgetCents,
            totalSpentCents = budgetState.totalSpentThisCycleCents,
            surplusCents = budgetState.remainingCycleCents,
            daySections = activeCycleDaySections,
            isActiveCycle = true,
            isReadOnly = !isEditable,
            isCompletedCycle = false
        )
    }

    private fun buildFutureExpenseSections(
        allExpenses: List<Expense>,
        dayTotals: Map<LocalDate, Long>,
        today: LocalDate,
        settings: net.loeu.wallybudget.domain.model.UserSettings,
        budgetPolicies: List<BudgetPolicy>
    ): List<ExpenseCycleSection> {
        val futureExpenses = allExpenses.filter { it.recordedDate().isAfter(today) }
        if (futureExpenses.isEmpty()) return emptyList()

        return futureExpenses
            .groupBy { expense ->
                cycleScheduleResolver.resolvePolicyForDate(
                    date = expense.recordedDate(),
                    settings = settings,
                    policies = budgetPolicies
                )
            }
            .toList()
            .sortedByDescending { (policy, _) -> policy.cycleStart }
            .map { (policy, expensesForPolicy) ->
                val futureDaySections = buildReadOnlyDaySections(expensesForPolicy, dayTotals)
                val futureTotalSpent = futureDaySections.sumOf { it.totalSpentCents }
                ExpenseCycleSection(
                    cycleStartDate = policy.cycleStart,
                    cycleEndDateExclusive = policy.cycleEndExclusive,
                    title = "Upcoming ${policy.cycleStart} - ${policy.cycleEndExclusive.minusDays(1)}",
                    budgetAmountCents = policy.budgetAmountCents,
                    totalSpentCents = futureTotalSpent,
                    surplusCents = policy.budgetAmountCents - futureTotalSpent,
                    daySections = futureDaySections,
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
        dayTotals: Map<LocalDate, Long>
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
                val daySections = buildReadOnlyDaySections(cycleExpenses, dayTotals)

                ExpenseCycleSection(
                    cycleStartDate = monthlyEntry.getCycleStart(),
                    cycleEndDateExclusive = monthlyEntry.getCycleEnd(),
                    title = monthlyEntry.getDisplayName(),
                    budgetAmountCents = monthlyEntry.budgetAmountCents,
                    totalSpentCents = monthlyEntry.totalSpentCents,
                    surplusCents = monthlyEntry.surplusCents,
                    daySections = daySections,
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
