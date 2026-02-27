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
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class BudgetRepository(
    private val expenseDao: ExpenseDao,
    private val monthlyHistoryDao: MonthlyHistoryDao,
    private val userPreferencesManager: UserPreferencesManager,
    private val budgetCalculationService: BudgetCalculationService = BudgetCalculationService()
) {

    val userSettings: Flow<UserSettings> = userPreferencesManager.userSettings

    /**
     * Get current budget state as a Flow
     */
    fun getBudgetState(): Flow<BudgetState> {
        return combine(
            userSettings,
            expenseDao.observeExpenseCount(),
            currentDateFlow()
        ) { settings, _, today ->
            val cycleStart = budgetCalculationService.getCycleStartDate(today, settings.paydayDate)
            val cycleEnd = budgetCalculationService.getNextCycleStartDate(today, settings.paydayDate)

            val startTime = cycleStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endTime = cycleEnd.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val totalSpentCents = expenseDao.getTotalSpentInRange(startTime, endTime) ?: 0L

            // Calculate spent today
            val todayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val todayEnd = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val spentTodayCents = expenseDao.getTotalSpentInRange(todayStart, todayEnd) ?: 0L

            // Get cumulative savings
            val cumulativeSavingsCents = monthlyHistoryDao.getCumulativeSavings() ?: 0L

            budgetCalculationService.calculateBudgetState(
                settings = settings,
                now = today,
                totalSpentThisCycleCents = totalSpentCents,
                spentTodayCents = spentTodayCents,
                cumulativeSavingsCents = cumulativeSavingsCents
            )
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
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getCurrentCycleExpenses(): Flow<List<Expense>> {
        return combine(userSettings, currentDateFlow()) { settings, now ->
            val cycleStart = budgetCalculationService.getCycleStartDate(now, settings.paydayDate)
            val cycleEnd = budgetCalculationService.getNextCycleStartDate(now, settings.paydayDate)
            val startTime = cycleStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endTime = cycleEnd.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            startTime to endTime
        }.flatMapLatest { (startTime, endTime) ->
            expenseDao.getExpensesByDateRange(startTime, endTime)
        }
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
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getPreviousCycleExpenses(): Flow<List<Expense>> {
        return combine(userSettings, currentDateFlow()) { settings, now ->
            val cycleStart = budgetCalculationService.getCycleStartDate(now, settings.paydayDate)
            val cycleEnd = budgetCalculationService.getNextCycleStartDate(now, settings.paydayDate)

            val cycleStartTimestamp = cycleStart
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            val cycleEndTimestamp = cycleEnd
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            val todayStartTimestamp = now
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            Triple(cycleStartTimestamp, cycleEndTimestamp, todayStartTimestamp)
        }.flatMapLatest { (cycleStartTimestamp, cycleEndTimestamp, todayStartTimestamp) ->
            expenseDao.getExpensesByDateRangeBeforeTime(
                startTime = cycleStartTimestamp,
                endTime = cycleEndTimestamp,
                beforeTime = todayStartTimestamp
            )
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
        val lastResetDate = if (settings.lastResetTimestamp > 0) {
            Instant.ofEpochMilli(settings.lastResetTimestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        } else {
            null
        } ?: return

        val currentCycleStart = budgetCalculationService.getCycleStartDate(now, settings.paydayDate)
        if (!budgetCalculationService.shouldPerformReset(now, settings.paydayDate, lastResetDate)) {
            return
        }

        var cycleToArchiveStart = lastResetDate
        while (cycleToArchiveStart.isBefore(currentCycleStart)) {
            val cycleToArchiveEnd = budgetCalculationService.getNextCycleStartDate(cycleToArchiveStart, settings.paydayDate)
            performMonthlyReset(settings, cycleToArchiveStart, cycleToArchiveEnd)
            cycleToArchiveStart = cycleToArchiveEnd
        }

        userPreferencesManager.updateLastResetTimestamp(
            currentCycleStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
    }

    /**
     * Perform monthly reset: archive current cycle and prepare for new one
     */
    private suspend fun performMonthlyReset(
        settings: UserSettings,
        cycleStart: LocalDate,
        cycleEnd: LocalDate
    ) {
        val startTime = cycleStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endTime = cycleEnd.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val totalSpentCents = expenseDao.getTotalSpentInRange(startTime, endTime) ?: 0L

        // Calculate surplus/deficit using service
        val surplusCents = budgetCalculationService.calculateSurplus(settings.monthlyBudgetCents, totalSpentCents)

        val expenseCount = expenseDao.getExpenseCountInRange(startTime, endTime)

        if (expenseCount > 0) {
            val history = MonthlyHistory(
                cycleStartDate = cycleStart.toString(), // ISO format: YYYY-MM-DD
                budgetAmountCents = settings.monthlyBudgetCents,
                totalSpentCents = totalSpentCents,
                surplusCents = surplusCents,
                cycleEndDate = cycleEnd.toString(), // ISO format: YYYY-MM-DD
                endTimestamp = endTime
            )
            monthlyHistoryDao.insert(history)
        }
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

