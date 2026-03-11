package net.loeu.wallybudget.domain.service

import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.MonthlyHistory
import net.loeu.wallybudget.domain.model.SpendingForecast
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.model.sumByDate
import net.loeu.wallybudget.domain.config.ForecastConfig
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

data class CycleDateRange(
    val start: LocalDate,
    val endExclusive: LocalDate
)

/**
 * Service for budget-related business logic and calculations.
 * Separated from data access layer (repository) for better separation of concerns.
 */
class BudgetCalculationService(
    private val forecastCalculator: SpendingForecastCalculator = SpendingForecastCalculator()
) {

    private fun normalizePaydayDate(paydayDate: Int): Int = paydayDate.coerceIn(1, 31)

    /**
     * Calculate current budget state given current spending and settings
     */
    fun calculateBudgetState(
        settings: UserSettings,
        now: LocalDate,
        totalSpentThisCycleCents: Long,
        spentTodayCents: Long,
        cumulativeSavingsCents: Long,
        cycleBudgetAmountCents: Long = settings.monthlyBudgetCents
    ): BudgetState {
        val cycleStart = getCycleStartDate(now, settings.paydayDate)
        val cycleEnd = getNextCycleStartDate(now, settings.paydayDate)

        // Days remaining in cycle including today (e.g. last day => 1)
        // Uses exclusive cycleEnd (invariant), so between(today, nextCycleStart) correctly includes today.
        val daysRemainingInCycle = ChronoUnit.DAYS.between(now, cycleEnd).toInt().coerceAtLeast(1)

        // Calculate today's budget by spreading prior over/underspend across remaining days
        // Note: cycleEnd is EXCLUSIVE (see MonthlyHistory invariant)
        val daysInCycle = ChronoUnit.DAYS.between(cycleStart, cycleEnd).toInt().coerceAtLeast(1)
        val baseDailyAllocationCents =
            (cycleBudgetAmountCents.toDouble() / daysInCycle).roundToLong()

        val daysBeforeToday = ChronoUnit.DAYS.between(cycleStart, now).toInt().coerceAtLeast(0)
        val allocatedBeforeTodayCents =
            ((cycleBudgetAmountCents.toDouble() * daysBeforeToday) / daysInCycle)
                .roundToLong()
        val spentBeforeTodayCents = (totalSpentThisCycleCents - spentTodayCents).coerceAtLeast(0L)

        val cycleVarianceBeforeTodayCents = allocatedBeforeTodayCents - spentBeforeTodayCents
        val futureDaysAfterToday =
            ChronoUnit.DAYS.between(now.plusDays(1), cycleEnd).toInt().coerceAtLeast(0)
        val remainingDaysForAdjustment = futureDaysAfterToday + 1 // include today; on final day this is exactly 1
        val distributedAdjustmentCents =
            (cycleVarianceBeforeTodayCents.toDouble() / remainingDaysForAdjustment).roundToLong()
        val effectiveDailyBudgetCents = baseDailyAllocationCents + distributedAdjustmentCents

        return BudgetState(
            monthlyBudgetCents = cycleBudgetAmountCents,
            totalSpentThisCycleCents = totalSpentThisCycleCents,
            dailyBudgetCents = baseDailyAllocationCents,
            spentTodayCents = spentTodayCents,
            remainingTodayCents = effectiveDailyBudgetCents - spentTodayCents,
            daysRemainingInCycle = daysRemainingInCycle,
            cumulativeSavingsCents = cumulativeSavingsCents,
            paydayDate = settings.paydayDate,
            cycleStartDate = cycleStart
        )
    }

    /**
     * Get the start date of the current budget cycle
     */
    fun getCycleStartDate(now: LocalDate, paydayDate: Int): LocalDate {
        val normalizedPayday = normalizePaydayDate(paydayDate)
        val effectivePayday = minOf(normalizedPayday, now.lengthOfMonth())

        return if (now.dayOfMonth >= effectivePayday) {
            // Current cycle started this month
            now.withDayOfMonth(effectivePayday)
        } else {
            // Current cycle started last month
            val lastMonth = now.minusMonths(1)
            val lastMonthPayday = minOf(normalizedPayday, lastMonth.lengthOfMonth())
            lastMonth.withDayOfMonth(lastMonthPayday)
        }
    }

    /**
     * Get the start date of the next budget cycle (the exclusive end date of the current cycle)
     */
    fun getNextCycleStartDate(now: LocalDate, paydayDate: Int): LocalDate {
        val normalizedPayday = normalizePaydayDate(paydayDate)
        val effectivePayday = minOf(normalizedPayday, now.lengthOfMonth())

        return if (now.dayOfMonth >= effectivePayday) {
            // Next cycle starts next month
            val nextMonth = now.plusMonths(1)
            val nextMonthPayday = minOf(normalizedPayday, nextMonth.lengthOfMonth())
            nextMonth.withDayOfMonth(nextMonthPayday)
        } else {
            // Next cycle starts this month
            now.withDayOfMonth(effectivePayday)
        }
    }

    /**
     * Calculate surplus/deficit for a completed cycle
     */
    fun calculateSurplus(monthlyBudgetCents: Long, totalSpentCents: Long): Long {
        return monthlyBudgetCents - totalSpentCents
    }

    /**
     * Get the portion of the current cycle that has actually elapsed, including today.
     * Future dates in the same cycle must not affect the active cycle budget state.
     */
    fun getCurrentCycleProgressRange(now: LocalDate, paydayDate: Int): CycleDateRange {
        val cycleStart = getCycleStartDate(now, paydayDate)
        val cycleEnd = getNextCycleStartDate(now, paydayDate)
        return CycleDateRange(
            start = cycleStart,
            endExclusive = minOf(now.plusDays(1), cycleEnd)
        )
    }

    /**
     * Forecast spending outcome for the active cycle using the enhanced algorithm.
     */
    fun calculateSpendingForecast(
        budgetState: BudgetState,
        now: LocalDate,
        monthlyHistory: List<MonthlyHistory>,
        recentExpenses: List<Expense>
    ): SpendingForecast {
        val cycleEnd = getNextCycleStartDate(now, budgetState.paydayDate)
        // daysInCycle uses exclusive cycleEnd (see MonthlyHistory invariant), 
        // so between(start, end) is correct (e.g. Jan 1 to Feb 1 = 31 days)
        val daysInCycle = ChronoUnit.DAYS.between(budgetState.cycleStartDate, cycleEnd).toInt().coerceAtLeast(1)

        val expensesByDay = recentExpenses.sumByDate()

        val currentCycleDailyExpenses = buildDailyExpenseSeries(
            expensesByDay = expensesByDay,
            start = budgetState.cycleStartDate,
            endExclusive = now.plusDays(1)
        )

        val historicalWindowStart = now.minusDays(ForecastConfig.HISTORICAL_DAYS_LOOKBACK.toLong())
        val historicalSeriesStart = findKnownHistoryStart(
            historicalWindowStart = historicalWindowStart,
            currentCycleStart = budgetState.cycleStartDate,
            expenseDates = expensesByDay.keys,
            monthlyHistory = monthlyHistory
        )
        val historicalRealDailyExpenses = historicalSeriesStart?.let { start ->
            // Reconstruct historical days explicitly so sparse cycles keep their zero-spend days,
            // but do not invent "zero history" before the first known tracked cycle/day.
            buildDailyExpenseSeries(
                expensesByDay = expensesByDay,
                start = start,
                endExclusive = budgetState.cycleStartDate
            )
        } ?: emptyList()

        val supplementaryHistoricalExpenses = monthlyHistory
            .sortedBy { it.getCycleStart() }
            .mapNotNull { history ->
                if (history.getCycleEnd() <= (historicalSeriesStart ?: historicalWindowStart)) {
                    // Uses MonthlyHistory.getDayCount() which encapsulates the exclusive end date invariant.
                    val days = history.getDayCount()
                    val dailyAmount = (history.totalSpentCents.toDouble() / days).roundToLong()
                    days to dailyAmount
                } else {
                    null
                }
            }
            .flatMap { (days, dailyAmount) ->
                List(days) { dailyAmount }
            }

        val allHistoricalExpenses = supplementaryHistoricalExpenses + historicalRealDailyExpenses
        val completedCycleDailyAverages = monthlyHistory
            .asSequence()
            .filter { it.getCycleEnd() <= budgetState.cycleStartDate }
            .sortedBy { it.getCycleEnd() }
            .map { history ->
                (history.totalSpentCents.toDouble() / history.getDayCount()).roundToLong()
            }
            .toList()

        return forecastCalculator.forecastMonthlySpending(
            budgetState = budgetState,
            allHistoricalExpenses = allHistoricalExpenses,
            currentCycleExpenses = currentCycleDailyExpenses,
            completedCycleDailyAverages = completedCycleDailyAverages,
            daysInMonth = daysInCycle
        )
    }

    private fun findKnownHistoryStart(
        historicalWindowStart: LocalDate,
        currentCycleStart: LocalDate,
        expenseDates: Set<LocalDate>,
        monthlyHistory: List<MonthlyHistory>
    ): LocalDate? {
        val earliestExpenseDate = expenseDates
            .filter { it.isBefore(currentCycleStart) && !it.isBefore(historicalWindowStart) }
            .minOrNull()

        val earliestHistoryCycleStart = monthlyHistory
            .asSequence()
            .filter { it.getCycleStart().isBefore(currentCycleStart) }
            .filter { it.getCycleEnd().isAfter(historicalWindowStart) }
            .map { maxOf(it.getCycleStart(), historicalWindowStart) }
            .minOrNull()

        val candidate = listOfNotNull(earliestExpenseDate, earliestHistoryCycleStart).minOrNull()
            ?: return null

        return minOf(candidate, currentCycleStart)
    }

    private fun buildDailyExpenseSeries(
        expensesByDay: Map<LocalDate, Long>,
        start: LocalDate,
        endExclusive: LocalDate
    ): List<Long> {
        if (!start.isBefore(endExclusive)) return emptyList()

        val dailyExpenses = mutableListOf<Long>()
        var cursor = start
        while (cursor.isBefore(endExclusive)) {
            dailyExpenses += expensesByDay[cursor] ?: 0L
            cursor = cursor.plusDays(1)
        }
        return dailyExpenses
    }

    /**
     * Check if a new cycle should start based on current date and last reset
     */
    fun shouldPerformReset(now: LocalDate, paydayDate: Int, lastResetDate: LocalDate?): Boolean {
        if (lastResetDate == null) return false
        val cycleStart = getCycleStartDate(now, paydayDate)
        return cycleStart.isAfter(lastResetDate)
    }
}
