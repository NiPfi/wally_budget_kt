package net.loeu.wallybudget.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import net.loeu.wallybudget.data.local.ExpenseDao
import net.loeu.wallybudget.data.local.MonthlyHistoryDao
import net.loeu.wallybudget.data.local.UserPreferencesManager
import net.loeu.wallybudget.data.model.BudgetState
import net.loeu.wallybudget.data.model.Expense
import net.loeu.wallybudget.data.model.MonthlyHistory
import net.loeu.wallybudget.data.model.UserSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

class BudgetRepository(
    private val expenseDao: ExpenseDao,
    private val monthlyHistoryDao: MonthlyHistoryDao,
    private val userPreferencesManager: UserPreferencesManager
) {

    val userSettings: Flow<UserSettings> = userPreferencesManager.userSettings

    /**
     * Get current budget state as a Flow
     */
    fun getBudgetState(): Flow<BudgetState> {
        return combine(
            userSettings,
            expenseDao.getAllExpenses(),
            currentDateFlow()
        ) { settings, _, today ->
            calculateBudgetState(settings, today)
        }
    }

    /**
     * Calculate current budget state
     */
    private suspend fun calculateBudgetState(settings: UserSettings, now: LocalDate): BudgetState {
        val cycleStart = getCycleStartDate(now, settings.paydayDate)
        val cycleEnd = getNextCycleStartDate(now, settings.paydayDate)

        val startTime = cycleStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endTime = cycleEnd.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val totalSpentCents = expenseDao.getTotalSpentInRange(startTime, endTime) ?: 0L

        // Calculate spent today
        val todayStart = now.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val todayEnd = now.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val spentTodayCents = expenseDao.getTotalSpentInRange(todayStart, todayEnd) ?: 0L

        // Calculate days remaining in cycle
        val daysRemaining = ChronoUnit.DAYS.between(now, cycleEnd).toInt()

        // Calculate today's budget with carry-over from previous days
        val daysInCycle = ChronoUnit.DAYS.between(cycleStart, cycleEnd).toInt().coerceAtLeast(1)
        val baseDailyBudgetCents = (settings.monthlyBudgetCents.toDouble() / daysInCycle).roundToLong()
        val daysBeforeToday = ChronoUnit.DAYS.between(cycleStart, now).toInt().coerceAtLeast(0)
        val allocatedBeforeTodayCents = ((settings.monthlyBudgetCents.toDouble() * daysBeforeToday) / daysInCycle).roundToLong()
        val spentBeforeTodayCents = (totalSpentCents - spentTodayCents).coerceAtLeast(0L)
        val carryOverCents = allocatedBeforeTodayCents - spentBeforeTodayCents
        val todayBudgetCents = baseDailyBudgetCents + carryOverCents

        // Get cumulative savings
        val cumulativeSavingsCents = monthlyHistoryDao.getCumulativeSavings() ?: 0L

        return BudgetState(
            monthlyBudgetCents = settings.monthlyBudgetCents,
            totalSpentThisCycleCents = totalSpentCents,
            dailyBudgetCents = baseDailyBudgetCents,
            spentTodayCents = spentTodayCents,
            remainingTodayCents = todayBudgetCents - spentTodayCents,
            daysRemainingInCycle = daysRemaining,
            cumulativeSavingsCents = cumulativeSavingsCents,
            paydayDate = settings.paydayDate,
            cycleStartDate = cycleStart
        )
    }

    /**
     * Get the start date of the current budget cycle
     */
    private fun getCycleStartDate(now: LocalDate, paydayDate: Int): LocalDate {
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
    private fun getNextCycleStartDate(now: LocalDate, paydayDate: Int): LocalDate {
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
     * Add a new expense
     */
    suspend fun addExpense(expense: Expense): Long {
        return expenseDao.insert(expense)
    }

    /**
     * Update an existing expense
     */
    suspend fun updateExpense(expense: Expense) {
        expenseDao.update(expense)
    }

    /**
     * Delete an expense
     */
    suspend fun deleteExpense(expense: Expense) {
        expenseDao.delete(expense)
    }

    /**
     * Get expenses for a date range
     */
    fun getExpensesInRange(startTime: Long, endTime: Long): Flow<List<Expense>> {
        return expenseDao.getExpensesByDateRange(startTime, endTime)
    }

    /**
     * Get all expenses for the current cycle
     */
    fun getCurrentCycleExpenses(): Flow<List<Expense>> {
        val now = LocalDate.now()
        val settings = userPreferencesManager.userSettings
        // This is a simplified version - in practice you'd combine with settings flow
        return expenseDao.getAllExpenses()
    }

    /**
     * Get expenses for today
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getTodayExpenses(): Flow<List<Expense>> {
        return currentDateFlow().flatMapLatest { now ->
            val todayStart = now.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val todayEnd = now.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            expenseDao.getExpensesByDateRange(todayStart, todayEnd)
        }
    }

    /**
     * Get expenses in the current cycle before today
     */
    fun getPreviousCycleExpenses(): Flow<List<Expense>> {
        return combine(userSettings, expenseDao.getAllExpenses(), currentDateFlow()) { settings, expenses, now ->
            val cycleStart = getCycleStartDate(now, settings.paydayDate)

            val cycleStartTimestamp = cycleStart
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            val todayStartTimestamp = now
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            expenses.filter { expense ->
                expense.timestamp in cycleStartTimestamp until todayStartTimestamp
            }
        }
    }

    /**
     * Update user settings
     */
    suspend fun updateSettings(settings: UserSettings) {
        userPreferencesManager.updateSettings(settings)
    }

    /**
     * Update monthly budget
     */
    suspend fun updateMonthlyBudget(amountCents: Long) {
        userPreferencesManager.updateMonthlyBudget(amountCents)
    }

    /**
     * Update payday date
     */
    suspend fun updatePaydayDate(day: Int) {
        userPreferencesManager.updatePaydayDate(day)
    }

    /**
     * Complete onboarding
     */
    suspend fun completeOnboarding(
        monthlyBudgetCents: Long,
        paydayDate: Int,
        cycleStartDate: LocalDate,
        previousExpensesCents: Long
    ) {
        userPreferencesManager.updateMonthlyBudget(monthlyBudgetCents)
        userPreferencesManager.updatePaydayDate(paydayDate)
        userPreferencesManager.updateLastResetTimestamp(
            cycleStartDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )

        if (previousExpensesCents > 0L) {
            val seedExpense = Expense(
                amountCents = previousExpensesCents,
                description = "Previous cycle expenses",
                timestamp = cycleStartDate.atTime(12, 0)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            )
            expenseDao.insert(seedExpense)
        }

        userPreferencesManager.completeOnboarding()
    }

    /**
     * Check if a new cycle should start and perform monthly reset if needed
     */
    suspend fun checkAndPerformMonthlyReset(settings: UserSettings) {
        val now = LocalDate.now()
        val cycleStart = getCycleStartDate(now, settings.paydayDate)
        val lastResetDate = if (settings.lastResetTimestamp > 0) {
            Instant.ofEpochMilli(settings.lastResetTimestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        } else {
            null
        }

        // If cycle start is after last reset, we need to perform a reset
        if (lastResetDate == null || cycleStart.isAfter(lastResetDate)) {
            performMonthlyReset(settings, cycleStart)
        }
    }

    /**
     * Perform monthly reset: archive current cycle and prepare for new one
     */
    private suspend fun performMonthlyReset(settings: UserSettings, cycleStart: LocalDate) {
        val previousCycleStart = cycleStart.minusMonths(1)
        val previousCycleEnd = cycleStart

        // Get total spent in previous cycle
        val startTime = previousCycleStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endTime = previousCycleEnd.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val totalSpentCents = expenseDao.getTotalSpentInRange(startTime, endTime) ?: 0L

        // Calculate surplus/deficit
        val surplusCents = settings.monthlyBudgetCents - totalSpentCents

        val expenseCount = expenseDao.getExpenseCountInRange(startTime, endTime)

        if (expenseCount > 0) {
            val history = MonthlyHistory(
                year = previousCycleStart.year,
                month = previousCycleStart.monthValue,
                budgetAmountCents = settings.monthlyBudgetCents,
                totalSpentCents = totalSpentCents,
                surplusCents = surplusCents,
                endTimestamp = endTime
            )
            monthlyHistoryDao.insert(history)
        }

        // Update last reset timestamp
        userPreferencesManager.updateLastResetTimestamp(cycleStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
    }

    /**
     * Get monthly history
     */
    fun getMonthlyHistory(): Flow<List<MonthlyHistory>> {
        return monthlyHistoryDao.getAllHistory().map { history ->
            history
                .filter { it.totalSpentCents > 0L }
                .sortedByDescending { it.endTimestamp }
        }
    }

    /**
     * Emits current date immediately and again at each local midnight.
     */
    fun observeCurrentDate(): Flow<LocalDate> = currentDateFlow()

    private fun currentDateFlow(): Flow<LocalDate> = flow {
        while (true) {
            val now = LocalDateTime.now()
            val today = now.toLocalDate()
            emit(today)

            val nextMidnight = today.plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            val nowMillis = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val delayMillis = (nextMidnight - nowMillis).coerceAtLeast(1L)
            delay(delayMillis)
        }
    }

    /**
     * Get cumulative savings
     */
    suspend fun getCumulativeSavings(): Long {
        return monthlyHistoryDao.getCumulativeSavings() ?: 0L
    }
}

