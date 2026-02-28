package net.loeu.wallybudget.domain.service

import net.loeu.wallybudget.data.model.BudgetState
import net.loeu.wallybudget.data.model.MonthlyHistory
import net.loeu.wallybudget.data.model.SpendingForecast
import net.loeu.wallybudget.data.model.UserSettings
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private const val FORECAST_SENSITIVITY_MIN = 20
private const val FORECAST_SENSITIVITY_MAX = 90

/**
 * Service for budget-related business logic and calculations.
 * Separated from data access layer (repository) for better separation of concerns.
 */
class BudgetCalculationService {

    /**
     * Calculate current budget state given current spending and settings
     */
    fun calculateBudgetState(
        settings: UserSettings,
        now: LocalDate,
        totalSpentThisCycleCents: Long,
        spentTodayCents: Long,
        cumulativeSavingsCents: Long
    ): BudgetState {
        val cycleStart = getCycleStartDate(now, settings.paydayDate)
        val cycleEnd = getNextCycleStartDate(now, settings.paydayDate)

        // Days remaining in cycle including today (e.g. last day => 1)
        val daysRemainingInCycle = ChronoUnit.DAYS.between(now, cycleEnd).toInt().coerceAtLeast(1)

        // Calculate today's budget by spreading prior over/underspend across remaining days
        val daysInCycle = ChronoUnit.DAYS.between(cycleStart, cycleEnd).toInt().coerceAtLeast(1)
        val baseDailyAllocationCents = (settings.monthlyBudgetCents.toDouble() / daysInCycle).roundToLong()

        val daysBeforeToday = ChronoUnit.DAYS.between(cycleStart, now).toInt().coerceAtLeast(0)
        val allocatedBeforeTodayCents = ((settings.monthlyBudgetCents.toDouble() * daysBeforeToday) / daysInCycle).roundToLong()
        val spentBeforeTodayCents = (totalSpentThisCycleCents - spentTodayCents).coerceAtLeast(0L)

        val cycleVarianceBeforeTodayCents = allocatedBeforeTodayCents - spentBeforeTodayCents
        val futureDaysAfterToday = ChronoUnit.DAYS.between(now.plusDays(1), cycleEnd).toInt().coerceAtLeast(0)
        val remainingDaysForAdjustment = futureDaysAfterToday + 1 // include today; on final day this is exactly 1
        val distributedAdjustmentCents = (cycleVarianceBeforeTodayCents.toDouble() / remainingDaysForAdjustment).roundToLong()
        val effectiveDailyBudgetCents = baseDailyAllocationCents + distributedAdjustmentCents

        return BudgetState(
            monthlyBudgetCents = settings.monthlyBudgetCents,
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
        val effectivePayday = minOf(paydayDate, now.lengthOfMonth())

        return if (now.dayOfMonth >= effectivePayday) {
            // Current cycle started this month
            now.withDayOfMonth(effectivePayday)
        } else {
            // Current cycle started last month
            val lastMonth = now.minusMonths(1)
            val lastMonthPayday = minOf(paydayDate, lastMonth.lengthOfMonth())
            lastMonth.withDayOfMonth(lastMonthPayday)
        }
    }

    /**
     * Get the start date of the next budget cycle
     */
    fun getNextCycleStartDate(now: LocalDate, paydayDate: Int): LocalDate {
        val effectivePayday = minOf(paydayDate, now.lengthOfMonth())

        return if (now.dayOfMonth >= effectivePayday) {
            // Next cycle starts next month
            val nextMonth = now.plusMonths(1)
            val nextMonthPayday = minOf(paydayDate, nextMonth.lengthOfMonth())
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
     * Forecast spending outcome for the active cycle using current pace and historical habits.
     */
    fun calculateSpendingForecast(
        budgetState: BudgetState,
        now: LocalDate,
        monthlyHistory: List<MonthlyHistory>,
        forecastSensitivityPercent: Int
    ): SpendingForecast {
        val cycleStart = budgetState.cycleStartDate
        val cycleEnd = getNextCycleStartDate(now, budgetState.paydayDate)

        val totalCycleDays = ChronoUnit.DAYS.between(cycleStart, cycleEnd).toInt().coerceAtLeast(1)
        val elapsedCycleDays = (ChronoUnit.DAYS.between(cycleStart, now).toInt().coerceAtLeast(0) + 1)
            .coerceAtMost(totalCycleDays)

        val currentDailyPaceCents = (budgetState.totalSpentThisCycleCents.toDouble() / elapsedCycleDays).roundToLong()

        val historyForHabits = monthlyHistory
            .filter { it.budgetAmountCents > 0L }
            .take(6)

        val historicalSpendRatio = if (historyForHabits.isNotEmpty()) {
            historyForHabits
                .map { it.totalSpentCents.toDouble() / it.budgetAmountCents }
                .average()
        } else {
            1.0
        }

        val sensitivity = (
            forecastSensitivityPercent.coerceIn(
                FORECAST_SENSITIVITY_MIN,
                FORECAST_SENSITIVITY_MAX
            ) / 100.0
        )
        val blendedHistoricalMultiplier = 1.0 + ((historicalSpendRatio - 1.0) * sensitivity)
        val historicalAdjustmentMultiplier = blendedHistoricalMultiplier.coerceIn(0.75, 1.5)
        val projectedTotalSpentCents =
            (currentDailyPaceCents.toDouble() * totalCycleDays * historicalAdjustmentMultiplier).roundToLong()
        val estimatedEndCycleRemainingCents = budgetState.monthlyBudgetCents - projectedTotalSpentCents

        return SpendingForecast(
            estimatedEndCycleRemainingCents = estimatedEndCycleRemainingCents,
            projectedTotalSpentCents = projectedTotalSpentCents,
            projectedDailySpendCents = currentDailyPaceCents,
            historicalAdjustmentPercent = ((historicalAdjustmentMultiplier - 1.0) * 100.0).roundToInt(),
            historyCyclesUsed = historyForHabits.size
        )
    }

    /**
     * Check if a new cycle should start based on current date and last reset
     *
     * @param now Current date
     * @param paydayDate Day of month for payday (1-31)
     * @param lastResetDate Date when the last reset was performed (null if never reset)
     * @return true if a reset should be performed
     */
    fun shouldPerformReset(now: LocalDate, paydayDate: Int, lastResetDate: LocalDate?): Boolean {
        if (lastResetDate == null) {
            // No reset has been performed yet - don't perform reset until the first cycle completes
            return false
        }

        val cycleStart = getCycleStartDate(now, paydayDate)
        // Perform reset if the current cycle start is after the last reset date
        return cycleStart.isAfter(lastResetDate)
    }
}

