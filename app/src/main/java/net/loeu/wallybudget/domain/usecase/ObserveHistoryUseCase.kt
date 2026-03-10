package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import net.loeu.wallybudget.data.local.dao.CycleOverviewDao
import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.dao.MonthlyHistoryDao
import net.loeu.wallybudget.data.local.entity.toDomainModel
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.data.time.CurrentDateProvider
import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.ExpenseCycleSection
import net.loeu.wallybudget.domain.model.ExpenseDaySection
import net.loeu.wallybudget.domain.model.HistoryState
import net.loeu.wallybudget.domain.model.MonthlyHistory
import net.loeu.wallybudget.domain.model.recordedDate
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.usecase.internal.buildBudgetState
import net.loeu.wallybudget.domain.usecase.internal.buildContinuousDaySections
import net.loeu.wallybudget.domain.usecase.internal.buildTimelineLockState
import net.loeu.wallybudget.domain.usecase.internal.effectiveCurrentDate
import net.loeu.wallybudget.domain.usecase.internal.filterByRange
import net.loeu.wallybudget.domain.usecase.internal.groupByLocalDate
import net.loeu.wallybudget.domain.usecase.internal.toDayTotalsMap
import java.time.LocalDate

class ObserveHistoryUseCase(
    private val expenseDao: ExpenseDao,
    private val monthlyHistoryDao: MonthlyHistoryDao,
    private val cycleOverviewDao: CycleOverviewDao,
    private val userSettingsStore: UserSettingsStore,
    private val currentDateProvider: CurrentDateProvider,
    private val budgetCalculationService: BudgetCalculationService
) {
    operator fun invoke(): Flow<HistoryState> {
        val userSettings = userSettingsStore.userSettings
        val effectiveDate = combine(
            userSettings,
            currentDateProvider.observeCurrentDate()
        ) { settings, observedDate ->
            effectiveCurrentDate(settings, observedDate)
        }.distinctUntilChanged()
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
            userSettings,
            effectiveDate,
            allExpenses,
            history,
            allDayTotals
        ) { settings, today, expenses, historyEntries, dayTotals ->
            val budgetState = buildBudgetState(
                settings = settings,
                today = today,
                allExpenses = expenses,
                history = historyEntries,
                budgetCalculationService = budgetCalculationService
            )
            val timelineLockState = buildTimelineLockState(
                settings = settings,
                effectiveCurrentDate = today,
                latestExpenseDate = expenses.firstOrNull()?.recordedDate(),
                budgetCalculationService = budgetCalculationService
            )
            val monthlyHistory = historyEntries
                .filter { it.totalSpentCents > 0L }
                .sortedByDescending { it.endTimestamp }

            HistoryState(
                monthlyHistory = monthlyHistory,
                historySections = buildHistorySections(
                    monthlyHistory = monthlyHistory,
                    allExpenses = expenses,
                    dayTotals = dayTotals,
                    budgetState = budgetState,
                    today = today,
                    isEditable = !timelineLockState.isLocked
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
        val activeCycleExpenses = allExpenses.filterByRange(
            start = currentCycleStart,
            endExclusive = today.plusDays(1)
        )
        val activeCycleDaySections = buildContinuousDaySections(
            start = currentCycleStart,
            endInclusive = today,
            expensesByDate = activeCycleExpenses.groupByLocalDate(),
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

        val futureExpenses = allExpenses.filter { it.recordedDate().isAfter(today) }
        if (futureExpenses.isNotEmpty()) {
            val futureDaySections = futureExpenses
                .groupByLocalDate()
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
                val cycleExpenses = allExpenses.filterByRange(
                    start = monthlyEntry.getCycleStart(),
                    endExclusive = monthlyEntry.getCycleEnd()
                )
                val daySections = cycleExpenses
                    .groupByLocalDate()
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
}
