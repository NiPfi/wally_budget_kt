package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.CycleOverviewDao
import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.dao.MonthlyHistoryDao
import net.loeu.wallybudget.data.local.entity.BudgetPolicyEntity
import net.loeu.wallybudget.data.local.db.TransactionRunner
import net.loeu.wallybudget.data.local.entity.ExpenseEntity
import net.loeu.wallybudget.data.local.entity.MonthlyHistoryEntity
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.data.local.querymodel.ExpenseDayTotalRow
import net.loeu.wallybudget.data.time.CurrentDateProvider
import net.loeu.wallybudget.domain.model.UserSettings
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

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

    override suspend fun ensureIdentity(): UserSettings {
        if (mutableUserSettings.value.installDeviceId.isBlank()) {
            mutableUserSettings.value = mutableUserSettings.value.copy(
                installDeviceId = "test-install-id",
                settingsRecordUuid = UUID.randomUUID().toString(),
                settingsUpdatedAtEpochMs = 1L,
                settingsModClock = "0000000000001-0000-test-install-id",
                settingsLastModifiedByInstallId = "test-install-id"
            )
        }
        return mutableUserSettings.value
    }

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

    override suspend fun restoreFromSnapshot(settings: UserSettings, onboardingCompleted: Boolean) {
        mutableUserSettings.value = settings.copy(isOnboardingCompleted = onboardingCompleted)
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

    override fun observeInRange(
        startDateInclusive: String,
        endDateExclusive: String
    ): Flow<List<ExpenseEntity>> {
        return allExpensesFlow.map { all ->
            all.filter {
                it.deletedAtEpochMs == null &&
                    it.expenseDate >= startDateInclusive &&
                    it.expenseDate < endDateExclusive
            }
        }
    }

    override suspend fun totalSpentInRange(startDateInclusive: String, endDateExclusive: String): Long? {
        return expenses
            .filter {
                it.deletedAtEpochMs == null &&
                    it.expenseDate >= startDateInclusive &&
                    it.expenseDate < endDateExclusive
            }
            .sumOf { it.amountCents }
    }

    override suspend fun countInRange(startDateInclusive: String, endDateExclusive: String): Int {
        return expenses.count {
            it.deletedAtEpochMs == null &&
                it.expenseDate >= startDateInclusive &&
                it.expenseDate < endDateExclusive
        }
    }

    override fun observeSince(sinceDateInclusive: String): Flow<List<ExpenseEntity>> {
        return allExpensesFlow.map { all ->
            all.filter { it.expenseDate >= sinceDateInclusive }
                .filter { it.deletedAtEpochMs == null }
                .sortedWith(compareBy<ExpenseEntity> { it.expenseDate }.thenBy { it.timestamp }.thenBy { it.id })
        }
    }

    override fun observeAllOrderedDesc(): Flow<List<ExpenseEntity>> = allExpensesFlow.map { all ->
        all.filter { it.deletedAtEpochMs == null }
    }

    override suspend fun findLatestExpenseDate(): String? =
        expenses.filter { it.deletedAtEpochMs == null }.maxOfOrNull { it.expenseDate }

    override fun observeLatestExpenseDate(): Flow<String?> =
        allExpensesFlow.map { entries ->
            entries.filter { it.deletedAtEpochMs == null }.maxOfOrNull { it.expenseDate }
        }

    override suspend fun getAllForSnapshot(): List<ExpenseEntity> = expenses.toList()

    override suspend fun countAll(): Int = expenses.size

    override suspend fun deleteAll() {
        expenses.clear()
        refresh()
    }

    override suspend fun findByRecordUuid(recordUuid: String): ExpenseEntity? {
        return expenses.firstOrNull { it.recordUuid == recordUuid }
    }

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

    override suspend fun getAll(): List<MonthlyHistoryEntity> = history.toList()

    override suspend fun deleteAll() {
        history.clear()
        refresh()
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

internal class FakeBudgetPolicyDao(
    initialPolicies: List<BudgetPolicyEntity> = emptyList()
) : BudgetPolicyDao {
    private val policies = initialPolicies.toMutableList()
    private val policyFlow = MutableStateFlow(sortedPolicies())
    private var nextId = (policies.maxOfOrNull { it.id } ?: 0L) + 1L

    override suspend fun insert(entity: BudgetPolicyEntity): Long {
        val inserted = if (entity.id == 0L) entity.copy(id = nextId++) else entity
        policies.removeAll { it.id == inserted.id || it.policyUuid == inserted.policyUuid }
        policies += inserted
        refresh()
        return inserted.id
    }

    override suspend fun update(policy: BudgetPolicyEntity) {
        policies.replaceAll { existing ->
            if (existing.id == policy.id || existing.policyUuid == policy.policyUuid) policy else existing
        }
        refresh()
    }

    override fun observeActivePolicies(): Flow<List<BudgetPolicyEntity>> = policyFlow.map { current ->
        current.filter { it.deletedAtEpochMs == null }
    }

    override suspend fun findActivePolicyForCycle(cycleStartDate: String): BudgetPolicyEntity? {
        return policies
            .filter { it.deletedAtEpochMs == null && it.cycleStartDate == cycleStartDate }
            .maxByOrNull { it.updatedAtEpochMs }
    }

    override suspend fun findByPolicyUuid(policyUuid: String): BudgetPolicyEntity? {
        return policies
            .filter { it.policyUuid == policyUuid }
            .maxByOrNull { it.updatedAtEpochMs }
    }

    override suspend fun getAllForSnapshot(): List<BudgetPolicyEntity> = policies.toList()

    override suspend fun countAll(): Int = policies.size

    override suspend fun deleteAll() {
        policies.clear()
        refresh()
    }

    val currentPolicies: List<BudgetPolicyEntity>
        get() = policyFlow.value

    private fun refresh() {
        policyFlow.value = sortedPolicies()
    }

    private fun sortedPolicies(): List<BudgetPolicyEntity> {
        return policies.sortedBy { it.cycleStartDate }
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
    val timestamp = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return ExpenseEntity(
        id = id,
        recordUuid = "expense-$id",
        amountCents = amountCents,
        description = description,
        timestamp = timestamp,
        expenseDate = date.toString(),
        originInstallId = "test-install-id",
        lastModifiedByInstallId = "test-install-id",
        createdAtEpochMs = timestamp,
        updatedAtEpochMs = timestamp,
        deletedAtEpochMs = null,
        modClock = "%013d-%04d-%s".format(timestamp, 0, "test-install-id")
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

internal fun budgetPolicyEntity(
    id: Long,
    cycleStart: LocalDate,
    cycleEndExclusive: LocalDate,
    budgetAmountCents: Long = 100_000L,
    paydayDayOfMonth: Int = 25
): BudgetPolicyEntity {
    val timestamp = cycleStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return BudgetPolicyEntity(
        id = id,
        policyUuid = "policy-$id",
        cycleStartDate = cycleStart.toString(),
        cycleEndDateExclusive = cycleEndExclusive.toString(),
        budgetAmountCents = budgetAmountCents,
        paydayDayOfMonth = paydayDayOfMonth,
        originInstallId = "test-install-id",
        lastModifiedByInstallId = "test-install-id",
        createdAtEpochMs = timestamp,
        updatedAtEpochMs = timestamp,
        deletedAtEpochMs = null,
        modClock = "%013d-%04d-%s".format(timestamp, 0, "test-install-id")
    )
}
