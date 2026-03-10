package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import net.loeu.wallybudget.data.local.dao.CycleOverviewDao
import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.dao.MonthlyHistoryDao
import net.loeu.wallybudget.data.local.entity.toDomainModel
import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.ExpenseCycleSection
import net.loeu.wallybudget.domain.model.ExpenseDaySection
import net.loeu.wallybudget.domain.model.HomeOverviewState
import net.loeu.wallybudget.domain.model.HistoryState
import net.loeu.wallybudget.domain.model.MonthlyHistory
import net.loeu.wallybudget.domain.model.groupByDate
import net.loeu.wallybudget.domain.model.recordedDate
import net.loeu.wallybudget.domain.usecase.internal.buildContinuousDaySections
import net.loeu.wallybudget.domain.usecase.internal.toDayTotalsMap
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import java.time.LocalDate

class ObserveHistoryUseCase(
    private val expenseDao: ExpenseDao,
    private val monthlyHistoryDao: MonthlyHistoryDao,
    private val cycleOverviewDao: CycleOverviewDao,
    private val budgetCalculationService: BudgetCalculationService
) {
    operator fun invoke(homeOverviewFlow: Flow<HomeOverviewState>): Flow<HistoryState> {
        val allExpenses = expenseDao.observeAllOrderedDesc().map { expenses ->
            expenses.map { it.toDomainModel() }
        }
        val history = monthlyHistoryDao.observeAll().map { entries ->
            entries.map { it.toDomainModel() }
        }
        val allDayTotals = cycleOverviewDao.observeAllDayTotals().map { rows ->
            rows.toDayTotalsMap()
        }

        return combine(
            homeOverviewFlow,
            allExpenses,
            history,
            allDayTotals
        ) { homeOverviewState, expenses, historyEntries, dayTotals ->
            val monthlyHistory = historyEntries
                .sortedByDescending { it.endTimestamp }

            HistoryState(
                monthlyHistory = monthlyHistory,
                historySections = buildHistorySections(
                    monthlyHistory = monthlyHistory,
                    allExpenses = expenses,
                    dayTotals = dayTotals,
                    budgetState = homeOverviewState.budgetState,
                    today = homeOverviewState.effectiveCurrentDate,
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
        isEditable: Boolean
    ): List<ExpenseCycleSection> {
        val sections = mutableListOf<ExpenseCycleSection>()
        val currentCycleStart = budgetState.cycleStartDate
        val (expensesByCycleStart, futureExpenses) = bucketExpensesByCycleStart(
            allExpenses = allExpenses,
            paydayDate = budgetState.paydayDate,
            today = today
        )
        val activeCycleExpenses = expensesByCycleStart[currentCycleStart].orEmpty()
        val activeCycleDaySections = buildContinuousDaySections(
            start = currentCycleStart,
            endInclusive = today,
            expensesByDate = activeCycleExpenses.groupByDate(),
            dayTotals = dayTotals,
            remainingBudgetForDay = { totalSpent -> budgetState.dailyBudgetCents - totalSpent },
            isEditable = isEditable,
            today = today
        )

        sections += ExpenseCycleSection(
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

        if (futureExpenses.isNotEmpty()) {
            val futureDaySections = futureExpenses
                .groupByDate()
                .toSortedMap(compareByDescending { it })
                .map { (date, expenses) ->
                    ExpenseDaySection(
                        date = date,
                        expenses = expenses,
                        totalSpentCents = dayTotals[date] ?: expenses.sumOf { it.amountCents },
                        remainingForDayCents = null,
                        isEditable = false
                    )
                }
            val futureStart = futureDaySections.last().date
            val futureEndExclusive = futureDaySections.first().date.plusDays(1)
            val futureTotalSpent = futureDaySections.sumOf { it.totalSpentCents }
            sections += ExpenseCycleSection(
                cycleStartDate = futureStart,
                cycleEndDateExclusive = futureEndExclusive,
                title = "Future-dated expenses",
                budgetAmountCents = budgetState.monthlyBudgetCents,
                totalSpentCents = futureTotalSpent,
                surplusCents = budgetState.monthlyBudgetCents - futureTotalSpent,
                daySections = futureDaySections,
                isActiveCycle = false,
                isReadOnly = true,
                isCompletedCycle = false
            )
        }

        sections += monthlyHistory
            .filterNot { it.getCycleStart() == currentCycleStart }
            .sortedByDescending { it.endTimestamp }
            .map { monthlyEntry ->
                val cycleExpenses = expensesByCycleStart[monthlyEntry.getCycleStart()].orEmpty()
                val daySections = cycleExpenses
                    .groupByDate()
                    .toSortedMap(compareByDescending { it })
                    .map { (date, expenses) ->
                        ExpenseDaySection(
                            date = date,
                            expenses = expenses,
                            totalSpentCents = dayTotals[date] ?: expenses.sumOf { it.amountCents },
                            remainingForDayCents = null,
                            isEditable = false
                        )
                    }

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

        return sections
    }

    private fun bucketExpensesByCycleStart(
        allExpenses: List<Expense>,
        paydayDate: Int,
        today: LocalDate
    ): Pair<Map<LocalDate, List<Expense>>, List<Expense>> {
        val expensesByCycleStart = linkedMapOf<LocalDate, MutableList<Expense>>()
        val futureExpenses = mutableListOf<Expense>()

        allExpenses.forEach { expense ->
            val recordedDate = expense.recordedDate()
            if (recordedDate.isAfter(today)) {
                futureExpenses += expense
                return@forEach
            }

            val cycleStart = budgetCalculationService.getCycleStartDate(
                now = recordedDate,
                paydayDate = paydayDate
            )
            expensesByCycleStart.getOrPut(cycleStart) { mutableListOf() } += expense
        }

        return expensesByCycleStart to futureExpenses
    }
}
