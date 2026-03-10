package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import net.loeu.wallybudget.data.local.dao.CycleOverviewDao
import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.dao.MonthlyHistoryDao
import net.loeu.wallybudget.data.local.db.TransactionRunner
import net.loeu.wallybudget.data.local.entity.ExpenseEntity
import net.loeu.wallybudget.data.local.entity.MonthlyHistoryEntity
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.data.local.querymodel.ExpenseDayTotalRow
import net.loeu.wallybudget.data.time.CurrentDateProvider
import net.loeu.wallybudget.domain.model.UserSettings
import java.time.LocalDate
import java.time.ZoneId

internal class FakeUserSettingsStore(
    initialSettings: UserSettings = UserSettings()
) : UserSettingsStore {
    private val mutableUserSettings = MutableStateFlow(initialSettings)

    override val userSettings: Flow<UserSettings> = mutableUserSettings

    var completedOnboarding = false
    var clearPendingCount = 0

    fun setSettings(settings: UserSettings) {
        mutableUserSettings.value = settings
    }

    val currentSettings: UserSettings
        get() = mutableUserSettings.value

    override suspend fun updateMonthlyBudget(amountCents: Long) {
        mutableUserSettings.value = mutableUserSettings.value.copy(monthlyBudgetCents = amountCents)
    }

    override suspend fun updatePaydayDate(day: Int) {
        mutableUserSettings.value = mutableUserSettings.value.copy(paydayDate = day)
    }

    override suspend fun updateLastResetTimestamp(timestamp: Long) {
        mutableUserSettings.value = mutableUserSettings.value.copy(lastResetTimestamp = timestamp)
    }

    override suspend fun updateLastSeenDate(date: LocalDate) {
        mutableUserSettings.value = mutableUserSettings.value.copy(lastSeenDate = date.toString())
    }

    override suspend fun completeOnboarding() {
        completedOnboarding = true
        mutableUserSettings.value = mutableUserSettings.value.copy(isOnboardingCompleted = true)
    }

    override suspend fun setPendingCycle(
        cycleStartDate: LocalDate,
        cycleEndDateExclusive: LocalDate,
        detectedAtTimestamp: Long
    ) {
        mutableUserSettings.value = mutableUserSettings.value.copy(
            pendingCycleStartDate = cycleStartDate.toString(),
            pendingCycleEndDateExclusive = cycleEndDateExclusive.toString(),
            pendingCycleDetectedAtTimestamp = detectedAtTimestamp
        )
    }

    override suspend fun clearPendingCycle() {
        clearPendingCount += 1
        mutableUserSettings.value = mutableUserSettings.value.copy(
            pendingCycleStartDate = null,
            pendingCycleEndDateExclusive = null,
            pendingCycleDetectedAtTimestamp = 0L
        )
    }
}

internal class FakeTransactionRunner : TransactionRunner {
    var transactionCount = 0

    override suspend fun <T> inTransaction(block: suspend () -> T): T {
        transactionCount += 1
        return block()
    }
}

internal class FakeCurrentDateProvider(
    initialDate: LocalDate
) : CurrentDateProvider {
    private val currentDateFlow = MutableStateFlow(initialDate)

    override fun currentDate(): LocalDate = currentDateFlow.value

    override fun observeCurrentDate(): Flow<LocalDate> = currentDateFlow

    fun setCurrentDate(date: LocalDate) {
        currentDateFlow.value = date
    }
}

internal class FakeExpenseDao(
    initialExpenses: List<ExpenseEntity> = emptyList()
) : ExpenseDao {
    private val expenses = initialExpenses.toMutableList()
    private val allExpensesFlow = MutableStateFlow(sortedExpenses())
    private var nextId = (expenses.maxOfOrNull { it.id } ?: 0L) + 1L

    override suspend fun insert(entity: ExpenseEntity): Long {
        val inserted = if (entity.id == 0L) entity.copy(id = nextId++) else entity
        expenses.removeAll { it.id == inserted.id }
        expenses += inserted
        refresh()
        return inserted.id
    }

    override suspend fun update(expense: ExpenseEntity) {
        expenses.replaceAll { existing ->
            if (existing.id == expense.id) expense else existing
        }
        refresh()
    }

    override suspend fun delete(expense: ExpenseEntity) {
        expenses.removeAll { it.id == expense.id }
        refresh()
    }

    override fun observeCount(): Flow<Int> = allExpensesFlow.map { it.size }

    override fun observeInRange(
        startDateInclusive: String,
        endDateExclusive: String
    ): Flow<List<ExpenseEntity>> {
        return allExpensesFlow.map { all ->
            all.filter { it.expenseDate >= startDateInclusive && it.expenseDate < endDateExclusive }
        }
    }

    override suspend fun totalSpentInRange(startDateInclusive: String, endDateExclusive: String): Long? {
        return expenses
            .filter { it.expenseDate >= startDateInclusive && it.expenseDate < endDateExclusive }
            .sumOf { it.amountCents }
    }

    override suspend fun countInRange(startDateInclusive: String, endDateExclusive: String): Int {
        return expenses.count { it.expenseDate >= startDateInclusive && it.expenseDate < endDateExclusive }
    }

    override suspend fun findById(expenseId: Long): ExpenseEntity? = expenses.firstOrNull { it.id == expenseId }

    override suspend fun deleteInRange(startDateInclusive: String, endDateExclusive: String) {
        expenses.removeAll { it.expenseDate >= startDateInclusive && it.expenseDate < endDateExclusive }
        refresh()
    }

    override fun observeSince(sinceDateInclusive: String): Flow<List<ExpenseEntity>> {
        return allExpensesFlow.map { all ->
            all.filter { it.expenseDate >= sinceDateInclusive }
                .sortedWith(compareBy<ExpenseEntity> { it.expenseDate }.thenBy { it.timestamp }.thenBy { it.id })
        }
    }

    override fun observeAllOrderedDesc(): Flow<List<ExpenseEntity>> = allExpensesFlow

    override suspend fun findLatestExpenseDate(): String? =
        expenses.maxOfOrNull { it.expenseDate }

    override fun observeLatestExpenseDate(): Flow<String?> =
        allExpensesFlow.map { entries -> entries.maxOfOrNull { it.expenseDate } }

    private fun refresh() {
        allExpensesFlow.value = sortedExpenses()
    }

    private fun sortedExpenses(): List<ExpenseEntity> {
        return expenses.sortedWith(
            compareByDescending<ExpenseEntity> { it.expenseDate }
                .thenByDescending { it.timestamp }
                .thenByDescending { it.id }
        )
    }
}

internal class FakeMonthlyHistoryDao(
    initialHistory: List<MonthlyHistoryEntity> = emptyList()
) : MonthlyHistoryDao {
    private val history = initialHistory.toMutableList()
    private val historyFlow = MutableStateFlow(sortedHistory())

    override suspend fun insert(entity: MonthlyHistoryEntity): Long {
        history.removeAll { it.cycleStartDate == entity.cycleStartDate }
        history += entity
        refresh()
        return 1L
    }

    override fun observeAll(): Flow<List<MonthlyHistoryEntity>> = historyFlow

    override suspend fun findByCycleStart(cycleStartDate: String): MonthlyHistoryEntity? {
        return history.firstOrNull { it.cycleStartDate == cycleStartDate }
    }

    val currentHistory: List<MonthlyHistoryEntity>
        get() = historyFlow.value

    private fun refresh() {
        historyFlow.value = sortedHistory()
    }

    private fun sortedHistory(): List<MonthlyHistoryEntity> {
        return history.sortedByDescending { it.endTimestamp }
    }
}

internal class FakeCycleOverviewDao(
    private val expenseDao: FakeExpenseDao
) : CycleOverviewDao {
    override fun observeDayTotalsInRange(
        startDateInclusive: String,
        endDateExclusive: String
    ): Flow<List<ExpenseDayTotalRow>> {
        return expenseDao.observeAllOrderedDesc().map { expenses ->
            expenses
                .filter { it.expenseDate >= startDateInclusive && it.expenseDate < endDateExclusive }
                .groupBy { it.expenseDate }
                .toSortedMap(compareByDescending { it })
                .map { (date, entries) ->
                    ExpenseDayTotalRow(
                        expenseDate = date,
                        totalSpentCents = entries.sumOf { it.amountCents }
                    )
                }
        }
    }
}

internal fun expenseEntityOn(
    id: Long,
    date: LocalDate,
    amountCents: Long,
    description: String = "Expense"
): ExpenseEntity {
    return ExpenseEntity(
        id = id,
        amountCents = amountCents,
        description = description,
        timestamp = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        expenseDate = date.toString()
    )
}

internal fun historyEntity(
    cycleStart: LocalDate,
    cycleEndExclusive: LocalDate,
    totalSpentCents: Long,
    budgetAmountCents: Long = 100_000L
): MonthlyHistoryEntity {
    return MonthlyHistoryEntity(
        cycleStartDate = cycleStart.toString(),
        budgetAmountCents = budgetAmountCents,
        totalSpentCents = totalSpentCents,
        surplusCents = budgetAmountCents - totalSpentCents,
        cycleEndDate = cycleEndExclusive.toString(),
        endTimestamp = cycleEndExclusive.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    )
}
