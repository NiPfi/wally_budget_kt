package net.loeu.wallybudget.data.local.source

import kotlinx.coroutines.flow.Flow
import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.dao.MonthlyHistoryDao
import net.loeu.wallybudget.data.local.entity.Expense
import net.loeu.wallybudget.data.local.entity.MonthlyHistory
import net.loeu.wallybudget.data.local.preferences.UserPreferencesManager
import net.loeu.wallybudget.domain.model.UserSettings
import java.time.LocalDate

class BudgetLocalDataSource(
    private val expenseDao: ExpenseDao,
    private val monthlyHistoryDao: MonthlyHistoryDao,
    private val userPreferencesManager: UserPreferencesManager
) {
    val userSettings: Flow<UserSettings> = userPreferencesManager.userSettings

    fun observeExpenseCount(): Flow<Int> = expenseDao.observeExpenseCount()

    fun getExpensesByDateRange(
        startDateInclusive: String,
        endDateExclusive: String
    ): Flow<List<Expense>> = expenseDao.getExpensesByDateRange(startDateInclusive, endDateExclusive)

    fun getExpensesSince(sinceDateInclusive: String): Flow<List<Expense>> {
        return expenseDao.getExpensesSince(sinceDateInclusive)
    }

    fun getAllExpensesOrderedByTimestampDesc(): Flow<List<Expense>> {
        return expenseDao.getAllExpensesOrderedByTimestampDesc()
    }

    fun observeLatestExpenseDate(): Flow<String?> = expenseDao.observeLatestExpenseDate()

    suspend fun addExpense(expense: Expense): Long = expenseDao.insert(expense)

    suspend fun updateExpense(expense: Expense) {
        expenseDao.update(expense)
    }

    suspend fun deleteExpense(expense: Expense) {
        expenseDao.delete(expense)
    }

    suspend fun getTotalSpentInRange(
        startDateInclusive: String,
        endDateExclusive: String
    ): Long? = expenseDao.getTotalSpentInRange(startDateInclusive, endDateExclusive)

    suspend fun getExpenseCountInRange(
        startDateInclusive: String,
        endDateExclusive: String
    ): Int = expenseDao.getExpenseCountInRange(startDateInclusive, endDateExclusive)

    fun getAllHistory(): Flow<List<MonthlyHistory>> = monthlyHistoryDao.getAllHistory()

    suspend fun getHistoryForCycle(cycleStartDate: String): MonthlyHistory? {
        return monthlyHistoryDao.getHistoryForCycle(cycleStartDate)
    }

    suspend fun insertHistory(history: MonthlyHistory) {
        monthlyHistoryDao.insert(history)
    }

    suspend fun updateMonthlyBudget(amountCents: Long) {
        userPreferencesManager.updateMonthlyBudget(amountCents)
    }

    suspend fun updatePaydayDate(day: Int) {
        userPreferencesManager.updatePaydayDate(day)
    }

    suspend fun updateLastResetTimestamp(timestamp: Long) {
        userPreferencesManager.updateLastResetTimestamp(timestamp)
    }

    suspend fun updateLastSeenDate(date: LocalDate) {
        userPreferencesManager.updateLastSeenDate(date)
    }

    suspend fun completeOnboarding() {
        userPreferencesManager.completeOnboarding()
    }

    suspend fun setPendingCycle(
        cycleStartDate: LocalDate,
        cycleEndDateExclusive: LocalDate,
        detectedAtTimestamp: Long
    ) {
        userPreferencesManager.setPendingCycle(
            cycleStartDate = cycleStartDate,
            cycleEndDateExclusive = cycleEndDateExclusive,
            detectedAtTimestamp = detectedAtTimestamp
        )
    }

    suspend fun clearPendingCycle() {
        userPreferencesManager.clearPendingCycle()
    }
}
