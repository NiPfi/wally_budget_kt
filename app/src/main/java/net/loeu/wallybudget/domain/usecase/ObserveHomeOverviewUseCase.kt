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
import net.loeu.wallybudget.domain.model.HomeOverviewState
import net.loeu.wallybudget.domain.model.PendingCycleCloseoutState
import net.loeu.wallybudget.domain.model.recordedDate
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.usecase.internal.buildBudgetState
import net.loeu.wallybudget.domain.usecase.internal.buildContinuousDaySections
import net.loeu.wallybudget.domain.usecase.internal.buildTimelineLockState
import net.loeu.wallybudget.domain.usecase.internal.buildTrendSummary
import net.loeu.wallybudget.domain.usecase.internal.effectiveCurrentDate
import net.loeu.wallybudget.domain.usecase.internal.filterByRange
import net.loeu.wallybudget.domain.usecase.internal.groupByLocalDate
import net.loeu.wallybudget.domain.usecase.internal.pendingCycleRangeOrNull
import net.loeu.wallybudget.domain.usecase.internal.toDayTotalsMap
import java.time.temporal.ChronoUnit

class ObserveHomeOverviewUseCase(
    private val expenseDao: ExpenseDao,
    private val monthlyHistoryDao: MonthlyHistoryDao,
    private val cycleOverviewDao: CycleOverviewDao,
    private val userSettingsStore: UserSettingsStore,
    private val currentDateProvider: CurrentDateProvider,
    private val budgetCalculationService: BudgetCalculationService
) {
    operator fun invoke(): Flow<HomeOverviewState> {
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
            val todayExpenses = expenses.filterByRange(
                start = today,
                endExclusive = today.plusDays(1)
            )
            val activeCycleExpenses = expenses.filterByRange(
                start = budgetState.cycleStartDate,
                endExclusive = today.plusDays(1)
            )
            val activeCycleSections = buildContinuousDaySections(
                start = budgetState.cycleStartDate,
                endInclusive = today,
                expensesByDate = activeCycleExpenses.groupByLocalDate(),
                dayTotals = dayTotals,
                remainingBudgetForDay = { totalSpent ->
                    budgetState.dailyBudgetCents - totalSpent
                },
                isEditable = !timelineLockState.isLocked,
                today = today
            )

            HomeOverviewState(
                effectiveCurrentDate = today,
                budgetState = budgetState,
                todayExpenses = todayExpenses,
                activeCycleExpenseSections = activeCycleSections,
                pendingCycleCloseoutState = buildPendingCycleCloseoutState(
                    settings = settings,
                    expenses = expenses,
                    dayTotals = dayTotals
                ),
                timelineLockState = timelineLockState
            )
        }.distinctUntilChanged()
    }

    private fun buildPendingCycleCloseoutState(
        settings: net.loeu.wallybudget.domain.model.UserSettings,
        expenses: List<net.loeu.wallybudget.domain.model.Expense>,
        dayTotals: Map<java.time.LocalDate, Long>
    ): PendingCycleCloseoutState? {
        val pendingCycle = settings.pendingCycleRangeOrNull() ?: return null
        val cycleExpenses = expenses.filterByRange(
            start = pendingCycle.start,
            endExclusive = pendingCycle.endExclusive
        )
        val dayCount = ChronoUnit.DAYS.between(pendingCycle.start, pendingCycle.endExclusive)
            .toInt()
            .coerceAtLeast(1)
        val baseDailyBudget = settings.monthlyBudgetCents / dayCount
        val daySections = buildContinuousDaySections(
            start = pendingCycle.start,
            endInclusive = pendingCycle.endExclusive.minusDays(1),
            expensesByDate = cycleExpenses.groupByLocalDate(),
            dayTotals = dayTotals,
            remainingBudgetForDay = { totalSpent -> baseDailyBudget - totalSpent },
            isEditable = true,
            today = null
        )
        val totalSpent = cycleExpenses.sumOf { it.amountCents }
        val biggestExpense = cycleExpenses.maxByOrNull { it.amountCents }
        val highestSpendDay = daySections.maxByOrNull { it.totalSpentCents }?.date
        val topCategory = cycleExpenses
            .filter { it.icon != null }
            .groupBy { it.icon }
            .maxByOrNull { (_, categoryExpenses) -> categoryExpenses.sumOf { it.amountCents } }
            ?.key

        return PendingCycleCloseoutState(
            cycleStartDate = pendingCycle.start,
            cycleEndDateExclusive = pendingCycle.endExclusive,
            budgetAmountCents = settings.monthlyBudgetCents,
            totalSpentCents = totalSpent,
            surplusCents = settings.monthlyBudgetCents - totalSpent,
            averageDailySpendCents = totalSpent / dayCount,
            biggestExpense = biggestExpense,
            highestSpendDay = highestSpendDay,
            topCategory = topCategory,
            trendSummary = buildTrendSummary(daySections),
            daySections = daySections
        )
    }
}
