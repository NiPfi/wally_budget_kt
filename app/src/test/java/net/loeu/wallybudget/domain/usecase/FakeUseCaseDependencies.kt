package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.BudgetAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationPolicyDao
import net.loeu.wallybudget.data.local.dao.BucketMonthlyHistoryDao
import net.loeu.wallybudget.data.local.dao.BucketTransferDao
import net.loeu.wallybudget.data.local.dao.BudgetBucketDao
import net.loeu.wallybudget.data.local.dao.CycleOverviewDao
import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.dao.FundDao
import net.loeu.wallybudget.data.local.dao.FundTransactionDao
import net.loeu.wallybudget.data.local.dao.MonthlyHistoryDao
import net.loeu.wallybudget.data.local.entity.BudgetAdjustmentEntity
import net.loeu.wallybudget.data.local.entity.BudgetPolicyEntity
import net.loeu.wallybudget.data.local.entity.BucketAllocationAdjustmentEntity
import net.loeu.wallybudget.data.local.entity.BucketAllocationPolicyEntity
import net.loeu.wallybudget.data.local.entity.BucketMonthlyHistoryEntity
import net.loeu.wallybudget.data.local.entity.BudgetBucketEntity
import net.loeu.wallybudget.data.local.entity.BucketTransferEntity
import net.loeu.wallybudget.data.local.db.TransactionRunner
import net.loeu.wallybudget.data.local.entity.ExpenseEntity
import net.loeu.wallybudget.data.local.entity.FundEntity
import net.loeu.wallybudget.data.local.entity.FundTransactionEntity
import net.loeu.wallybudget.data.local.entity.MonthlyHistoryEntity
import net.loeu.wallybudget.data.local.querymodel.BucketSpendRow
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.data.local.querymodel.ExpenseDayTotalRow
import net.loeu.wallybudget.data.time.CurrentDateProvider
import net.loeu.wallybudget.domain.model.PendingPaydayUndo
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.model.BucketBalanceBehavior
import net.loeu.wallybudget.domain.model.BucketTrackingMode
import net.loeu.wallybudget.domain.model.DEFAULT_FUND_NAME
import net.loeu.wallybudget.domain.model.DEFAULT_FUND_UUID
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_NAME
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

internal class FakeUserSettingsStore(
    initialSettings: UserSettings = UserSettings()
) : UserSettingsStore {
    private val mutableUserSettings = MutableStateFlow(initialSettings)
    private val mutablePendingPaydayUndo = MutableStateFlow<PendingPaydayUndo?>(null)

    override val userSettings: Flow<UserSettings> = mutableUserSettings
    override val pendingPaydayUndo: Flow<PendingPaydayUndo?> = mutablePendingPaydayUndo

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

    override suspend fun updatePortfolioMonthlyBudget(amountCents: Long?) {
        mutableUserSettings.value = mutableUserSettings.value.copy(portfolioMonthlyBudgetCents = amountCents)
    }

    override suspend fun updateCycleSettings(monthlyBudgetCents: Long, paydayDate: Int) {
        mutableUserSettings.value = mutableUserSettings.value.copy(
            monthlyBudgetCents = monthlyBudgetCents,
            paydayDate = paydayDate
        )
    }

    override suspend fun updatePaydayDate(day: Int) {
        mutableUserSettings.value = mutableUserSettings.value.copy(paydayDate = day)
    }

    override suspend fun updateSelectedBucket(selectedBucketUuid: String?) {
        mutableUserSettings.value = mutableUserSettings.value.copy(
            selectedBucketUuid = selectedBucketUuid
        )
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

    override suspend fun savePendingPaydayUndo(pendingPaydayUndo: PendingPaydayUndo) {
        mutablePendingPaydayUndo.value = pendingPaydayUndo
    }

    override suspend fun clearPendingPaydayUndo() {
        mutablePendingPaydayUndo.value = null
    }

    override suspend fun restoreFromSnapshot(settings: UserSettings, onboardingCompleted: Boolean) {
        mutableUserSettings.value = settings.copy(isOnboardingCompleted = onboardingCompleted)
        mutablePendingPaydayUndo.value = null
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

    override suspend fun insert(entities: List<ExpenseEntity>): List<Long> {
        return entities.map { insert(it) }
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

    override suspend fun getInRange(startDateInclusive: String, endDateExclusive: String): List<ExpenseEntity> {
        return expenses.filter {
            it.deletedAtEpochMs == null &&
                it.expenseDate >= startDateInclusive &&
                it.expenseDate < endDateExclusive
        }
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

    override suspend fun totalSpentPerBucketInRange(
        startDateInclusive: String,
        endDateExclusive: String
    ): List<BucketSpendRow> {
        return expenses
            .filter {
                it.deletedAtEpochMs == null &&
                    it.expenseDate >= startDateInclusive &&
                    it.expenseDate < endDateExclusive
            }
            .groupBy { it.bucketUuid.orEmpty() }
            .map { (bucketUuid, entries) ->
                BucketSpendRow(
                    bucketUuid = bucketUuid,
                    totalSpentCents = entries.sumOf { it.amountCents }
                )
            }
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

    override suspend fun insert(entities: List<MonthlyHistoryEntity>): List<Long> {
        return entities.map { insert(it) }
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

    override suspend fun insert(entities: List<BudgetPolicyEntity>): List<Long> {
        return entities.map { insert(it) }
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

internal class FakeBudgetAdjustmentDao(
    initialAdjustments: List<BudgetAdjustmentEntity> = emptyList()
) : BudgetAdjustmentDao {
    private val adjustments = initialAdjustments.toMutableList()
    private val adjustmentFlow = MutableStateFlow(sortedAdjustments())
    private var nextId = (adjustments.maxOfOrNull { it.id } ?: 0L) + 1L

    override suspend fun insert(entity: BudgetAdjustmentEntity): Long {
        val inserted = if (entity.id == 0L) entity.copy(id = nextId++) else entity
        adjustments.removeAll { it.id == inserted.id || it.adjustmentUuid == inserted.adjustmentUuid }
        adjustments += inserted
        refresh()
        return inserted.id
    }

    override suspend fun insert(entities: List<BudgetAdjustmentEntity>): List<Long> {
        return entities.map { insert(it) }
    }

    override suspend fun update(adjustment: BudgetAdjustmentEntity) {
        adjustments.replaceAll { existing ->
            if (existing.id == adjustment.id || existing.adjustmentUuid == adjustment.adjustmentUuid) {
                adjustment
            } else {
                existing
            }
        }
        refresh()
    }

    override fun observeActiveForCycle(cycleStartDate: String): Flow<List<BudgetAdjustmentEntity>> {
        return adjustmentFlow.map { current ->
            current.filter { it.deletedAtEpochMs == null && it.cycleStartDate == cycleStartDate }
        }
    }

    override suspend fun getActiveForCycle(cycleStartDate: String): List<BudgetAdjustmentEntity> {
        return adjustments
            .filter { it.deletedAtEpochMs == null && it.cycleStartDate == cycleStartDate }
            .sortedWith(compareBy<BudgetAdjustmentEntity> { it.effectiveDate }.thenBy { it.updatedAtEpochMs })
    }

    override fun observeAllActive(): Flow<List<BudgetAdjustmentEntity>> {
        return adjustmentFlow.map { current -> current.filter { it.deletedAtEpochMs == null } }
    }

    override suspend fun getAllForSnapshot(): List<BudgetAdjustmentEntity> = adjustments.toList()

    override suspend fun findByAdjustmentUuid(adjustmentUuid: String): BudgetAdjustmentEntity? {
        return adjustments
            .filter { it.adjustmentUuid == adjustmentUuid }
            .maxByOrNull { it.updatedAtEpochMs }
    }

    override suspend fun deleteByAdjustmentUuids(adjustmentUuids: List<String>) {
        adjustments.removeAll { it.adjustmentUuid in adjustmentUuids }
        refresh()
    }

    override suspend fun countAll(): Int = adjustments.size

    override suspend fun deleteAll() {
        adjustments.clear()
        refresh()
    }

    private fun refresh() {
        adjustmentFlow.value = sortedAdjustments()
    }

    private fun sortedAdjustments(): List<BudgetAdjustmentEntity> {
        return adjustments.sortedWith(
            compareBy<BudgetAdjustmentEntity> { it.cycleStartDate }
                .thenBy { it.effectiveDate }
                .thenBy { it.updatedAtEpochMs }
        )
    }
}

internal class FakeBudgetBucketDao(
    initialBuckets: List<BudgetBucketEntity> = emptyList()
) : BudgetBucketDao {
    private val buckets = initialBuckets.toMutableList()
    private val bucketFlow = MutableStateFlow(sortedBuckets())
    private var nextId = (buckets.maxOfOrNull { it.id } ?: 0L) + 1L

    override suspend fun insert(entity: BudgetBucketEntity): Long {
        val inserted = if (entity.id == 0L) entity.copy(id = nextId++) else entity
        buckets.removeAll { it.id == inserted.id || it.bucketUuid == inserted.bucketUuid }
        buckets += inserted
        refresh()
        return inserted.id
    }

    override suspend fun insert(entities: List<BudgetBucketEntity>): List<Long> = entities.map { insert(it) }

    override suspend fun update(bucket: BudgetBucketEntity) {
        buckets.replaceAll { existing ->
            if (existing.id == bucket.id || existing.bucketUuid == bucket.bucketUuid) bucket else existing
        }
        refresh()
    }

    override fun observeAllActive(): Flow<List<BudgetBucketEntity>> = bucketFlow.map { current ->
        current.filter { it.deletedAtEpochMs == null && it.closedAtEpochMs == null }
    }

    override fun observeAll(): Flow<List<BudgetBucketEntity>> = bucketFlow

    override suspend fun getAllActive(): List<BudgetBucketEntity> = buckets
        .filter { it.deletedAtEpochMs == null && it.closedAtEpochMs == null }
        .sortedWith(compareBy<BudgetBucketEntity> { it.sortOrder }.thenBy { it.createdAtEpochMs })

    override suspend fun getAllForSnapshot(): List<BudgetBucketEntity> = sortedBuckets()

    override suspend fun findByBucketUuid(bucketUuid: String): BudgetBucketEntity? = buckets
        .filter { it.bucketUuid == bucketUuid }
        .maxByOrNull { it.updatedAtEpochMs }

    override suspend fun countAll(): Int = buckets.size

    override suspend fun deleteAll() {
        buckets.clear()
        refresh()
    }

    private fun refresh() {
        bucketFlow.value = sortedBuckets()
    }

    private fun sortedBuckets(): List<BudgetBucketEntity> {
        return buckets.sortedWith(compareBy<BudgetBucketEntity> { it.sortOrder }.thenBy { it.createdAtEpochMs })
    }
}

internal class FakeBucketAllocationPolicyDao(
    initialPolicies: List<BucketAllocationPolicyEntity> = emptyList()
) : BucketAllocationPolicyDao {
    private val policies = initialPolicies.toMutableList()
    private val policyFlow = MutableStateFlow(sortedPolicies())
    private var nextId = (policies.maxOfOrNull { it.id } ?: 0L) + 1L

    override suspend fun insert(entity: BucketAllocationPolicyEntity): Long {
        val inserted = if (entity.id == 0L) entity.copy(id = nextId++) else entity
        policies.removeAll { it.id == inserted.id || it.allocationUuid == inserted.allocationUuid }
        policies += inserted
        refresh()
        return inserted.id
    }

    override suspend fun insert(entities: List<BucketAllocationPolicyEntity>): List<Long> = entities.map { insert(it) }

    override suspend fun update(policy: BucketAllocationPolicyEntity) {
        policies.replaceAll { existing ->
            if (existing.id == policy.id || existing.allocationUuid == policy.allocationUuid) policy else existing
        }
        refresh()
    }

    override fun observeActivePolicies(): Flow<List<BucketAllocationPolicyEntity>> = policyFlow.map { current ->
        current.filter { it.deletedAtEpochMs == null }
    }

    override suspend fun findActivePolicyForCycle(
        bucketUuid: String,
        cycleStartDate: String
    ): BucketAllocationPolicyEntity? {
        return policies
            .filter {
                it.deletedAtEpochMs == null &&
                    it.bucketUuid == bucketUuid &&
                    it.cycleStartDate == cycleStartDate
            }
            .maxByOrNull { it.updatedAtEpochMs }
    }

    override suspend fun findByAllocationUuid(allocationUuid: String): BucketAllocationPolicyEntity? = policies
        .filter { it.allocationUuid == allocationUuid }
        .maxByOrNull { it.updatedAtEpochMs }

    override suspend fun getAllForSnapshot(): List<BucketAllocationPolicyEntity> = sortedPolicies()

    override suspend fun countAll(): Int = policies.size

    override suspend fun deleteAll() {
        policies.clear()
        refresh()
    }

    private fun refresh() {
        policyFlow.value = sortedPolicies()
    }

    private fun sortedPolicies(): List<BucketAllocationPolicyEntity> {
        return policies.sortedWith(
            compareBy<BucketAllocationPolicyEntity> { it.cycleStartDate }
                .thenBy { it.updatedAtEpochMs }
        )
    }
}

internal class FakeBucketAllocationAdjustmentDao(
    initialAdjustments: List<BucketAllocationAdjustmentEntity> = emptyList()
) : BucketAllocationAdjustmentDao {
    private val adjustments = initialAdjustments.toMutableList()
    private val adjustmentFlow = MutableStateFlow(sortedAdjustments())
    private var nextId = (adjustments.maxOfOrNull { it.id } ?: 0L) + 1L

    override suspend fun insert(entity: BucketAllocationAdjustmentEntity): Long {
        val inserted = if (entity.id == 0L) entity.copy(id = nextId++) else entity
        adjustments.removeAll { it.id == inserted.id || it.adjustmentUuid == inserted.adjustmentUuid }
        adjustments += inserted
        refresh()
        return inserted.id
    }

    override suspend fun insert(entities: List<BucketAllocationAdjustmentEntity>): List<Long> =
        entities.map { insert(it) }

    override suspend fun update(adjustment: BucketAllocationAdjustmentEntity) {
        adjustments.replaceAll { existing ->
            if (existing.id == adjustment.id || existing.adjustmentUuid == adjustment.adjustmentUuid) {
                adjustment
            } else {
                existing
            }
        }
        refresh()
    }

    override fun observeActiveForCycle(
        bucketUuid: String,
        cycleStartDate: String
    ): Flow<List<BucketAllocationAdjustmentEntity>> = adjustmentFlow.map { current ->
        current.filter {
            it.deletedAtEpochMs == null && it.bucketUuid == bucketUuid && it.cycleStartDate == cycleStartDate
        }
    }

    override suspend fun getActiveForCycle(
        bucketUuid: String,
        cycleStartDate: String
    ): List<BucketAllocationAdjustmentEntity> {
        return adjustments.filter {
            it.deletedAtEpochMs == null && it.bucketUuid == bucketUuid && it.cycleStartDate == cycleStartDate
        }.sortedWith(
            compareBy<BucketAllocationAdjustmentEntity> { it.effectiveDate }
                .thenBy { it.updatedAtEpochMs }
        )
    }

    override fun observeAllActive(): Flow<List<BucketAllocationAdjustmentEntity>> = adjustmentFlow.map { current ->
        current.filter { it.deletedAtEpochMs == null }
    }

    override suspend fun getAllForSnapshot(): List<BucketAllocationAdjustmentEntity> = sortedAdjustments()

    override suspend fun findByAdjustmentUuid(adjustmentUuid: String): BucketAllocationAdjustmentEntity? = adjustments
        .filter { it.adjustmentUuid == adjustmentUuid }
        .maxByOrNull { it.updatedAtEpochMs }

    override suspend fun deleteByAdjustmentUuids(adjustmentUuids: List<String>) {
        adjustments.removeAll { it.adjustmentUuid in adjustmentUuids }
        refresh()
    }

    override suspend fun countAll(): Int = adjustments.size

    override suspend fun deleteAll() {
        adjustments.clear()
        refresh()
    }

    private fun refresh() {
        adjustmentFlow.value = sortedAdjustments()
    }

    private fun sortedAdjustments(): List<BucketAllocationAdjustmentEntity> {
        return adjustments.sortedWith(
            compareBy<BucketAllocationAdjustmentEntity> { it.bucketUuid }
                .thenBy { it.cycleStartDate }
                .thenBy { it.effectiveDate }
                .thenBy { it.updatedAtEpochMs }
        )
    }
}

internal class FakeBucketTransferDao(
    initialTransfers: List<BucketTransferEntity> = emptyList()
) : BucketTransferDao {
    private val transfers = initialTransfers.toMutableList()
    private val transferFlow = MutableStateFlow(sortedTransfers())
    private var nextId = (transfers.maxOfOrNull { it.id } ?: 0L) + 1L

    override suspend fun insert(entity: BucketTransferEntity): Long {
        val inserted = if (entity.id == 0L) entity.copy(id = nextId++) else entity
        transfers.removeAll { it.id == inserted.id || it.transferUuid == inserted.transferUuid }
        transfers += inserted
        refresh()
        return inserted.id
    }

    override suspend fun insert(entities: List<BucketTransferEntity>): List<Long> = entities.map { insert(it) }

    override fun observeForCycle(cycleStartDate: String): Flow<List<BucketTransferEntity>> =
        transferFlow.map { current ->
            current.filter { it.deletedAtEpochMs == null && it.cycleStartDate == cycleStartDate }
        }

    override suspend fun getForCycle(cycleStartDate: String): List<BucketTransferEntity> {
        return sortedTransfers().filter { it.deletedAtEpochMs == null && it.cycleStartDate == cycleStartDate }
    }

    override suspend fun getAllForSnapshot(): List<BucketTransferEntity> = sortedTransfers()

    override suspend fun deleteAll() {
        transfers.clear()
        refresh()
    }

    private fun refresh() {
        transferFlow.value = sortedTransfers()
    }

    private fun sortedTransfers(): List<BucketTransferEntity> {
        return transfers.sortedWith(
            compareBy<BucketTransferEntity> { it.cycleStartDate }
                .thenBy { it.effectiveDate }
                .thenBy { it.updatedAtEpochMs }
                .thenBy { it.transferUuid }
        )
    }
}

internal class FakeBucketMonthlyHistoryDao(
    initialHistory: List<BucketMonthlyHistoryEntity> = emptyList()
) : BucketMonthlyHistoryDao {
    private val history = initialHistory.toMutableList()
    private val historyFlow = MutableStateFlow(sortedHistory())

    override suspend fun insert(entity: BucketMonthlyHistoryEntity): Long {
        history.removeAll { it.bucketUuid == entity.bucketUuid && it.cycleStartDate == entity.cycleStartDate }
        history += entity
        refresh()
        return 1L
    }

    override suspend fun insert(entities: List<BucketMonthlyHistoryEntity>): List<Long> = entities.map { insert(it) }

    override fun observeAll(): Flow<List<BucketMonthlyHistoryEntity>> = historyFlow

    override fun observeForBucket(bucketUuid: String): Flow<List<BucketMonthlyHistoryEntity>> =
        historyFlow.map { current ->
            current.filter { it.bucketUuid == bucketUuid }
        }

    override suspend fun findByBucketAndCycleStart(
        bucketUuid: String,
        cycleStartDate: String
    ): BucketMonthlyHistoryEntity? = history.firstOrNull {
        it.bucketUuid == bucketUuid && it.cycleStartDate == cycleStartDate
    }

    override suspend fun getAll(): List<BucketMonthlyHistoryEntity> = sortedHistory()

    override suspend fun getAllForBucket(bucketUuid: String): List<BucketMonthlyHistoryEntity> = sortedHistory()
        .filter { it.bucketUuid == bucketUuid }

    override suspend fun deleteAll() {
        history.clear()
        refresh()
    }

    val currentHistory: List<BucketMonthlyHistoryEntity>
        get() = historyFlow.value

    private fun refresh() {
        historyFlow.value = sortedHistory()
    }

    private fun sortedHistory(): List<BucketMonthlyHistoryEntity> {
        return history.sortedByDescending { it.endTimestamp }
    }
}

internal class FakeFundDao(
    initialFunds: List<FundEntity> = emptyList()
) : FundDao {
    private val funds = initialFunds.toMutableList()
    private val fundFlow = MutableStateFlow(sortedFunds())

    override suspend fun update(fund: FundEntity) {
        funds.replaceAll { existing -> if (existing.uuid == fund.uuid) fund else existing }
        refresh()
    }

    override fun observeAllActive(): Flow<List<FundEntity>> = fundFlow.map { current ->
        current.filter { it.deletedAtEpochMs == null && it.closedAtEpochMs == null }
    }

    override suspend fun getAllForSnapshot(): List<FundEntity> = sortedFunds()

    override suspend fun getAllActive(): List<FundEntity> = sortedFunds()
        .filter { it.deletedAtEpochMs == null && it.closedAtEpochMs == null }

    override suspend fun findByUuid(uuid: String): FundEntity? = funds.firstOrNull { it.uuid == uuid }

    override suspend fun deleteAll() {
        funds.clear()
        refresh()
    }

    override suspend fun insert(entity: FundEntity): Long {
        funds.removeAll { it.uuid == entity.uuid }
        funds += entity
        refresh()
        return 1L
    }

    override suspend fun insert(entities: List<FundEntity>): List<Long> = entities.map { insert(it) }

    val currentFunds: List<FundEntity>
        get() = fundFlow.value

    private fun refresh() {
        fundFlow.value = sortedFunds()
    }

    private fun sortedFunds(): List<FundEntity> {
        return funds.sortedWith(
            compareBy<FundEntity> { it.sortOrder }
                .thenBy { it.createdAtEpochMs }
                .thenBy { it.uuid }
        )
    }
}

internal class FakeFundTransactionDao(
    initialTransactions: List<FundTransactionEntity> = emptyList()
) : FundTransactionDao {
    private val transactions = initialTransactions.toMutableList()

    override suspend fun getAllForSnapshot(): List<FundTransactionEntity> {
        return transactions.sortedWith(compareBy<FundTransactionEntity> { it.dateEpochMs }.thenBy { it.uuid })
    }

    override suspend fun deleteAll() {
        transactions.clear()
    }

    override suspend fun insert(entity: FundTransactionEntity): Long {
        transactions.removeAll { it.uuid == entity.uuid }
        transactions += entity
        return 1L
    }

    override suspend fun insert(entities: List<FundTransactionEntity>): List<Long> = entities.map { insert(it) }

    val currentTransactions: List<FundTransactionEntity>
        get() = transactions.sortedWith(compareBy<FundTransactionEntity> { it.dateEpochMs }.thenBy { it.uuid })
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

internal fun budgetAdjustmentEntity(
    id: Long,
    cycleStart: LocalDate,
    effectiveDate: LocalDate,
    previousMonthlyBudgetCents: Long,
    newMonthlyBudgetCents: Long
): BudgetAdjustmentEntity {
    val timestamp = effectiveDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return BudgetAdjustmentEntity(
        id = id,
        adjustmentUuid = "adjustment-$id",
        cycleStartDate = cycleStart.toString(),
        effectiveDate = effectiveDate.toString(),
        previousMonthlyBudgetCents = previousMonthlyBudgetCents,
        newMonthlyBudgetCents = newMonthlyBudgetCents,
        originInstallId = "test-install-id",
        lastModifiedByInstallId = "test-install-id",
        createdAtEpochMs = timestamp,
        updatedAtEpochMs = timestamp,
        deletedAtEpochMs = null,
        modClock = "%013d-%04d-%s".format(timestamp, 0, "test-install-id")
    )
}

internal fun fundEntity(
    uuid: String = DEFAULT_FUND_UUID,
    name: String = DEFAULT_FUND_NAME,
    balanceCents: Long = 0L,
    allocationPerCycleCents: Long = 0L,
    sortOrder: Int = 0,
    originInstallId: String = "test-install-id",
    lastModifiedByInstallId: String = "test-install-id",
    createdAtEpochMs: Long = 1L,
    updatedAtEpochMs: Long = createdAtEpochMs,
    closedAtEpochMs: Long? = null,
    deletedAtEpochMs: Long? = null,
    modClock: String = "%013d-%04d-%s".format(updatedAtEpochMs, 0, "test-install-id")
): FundEntity {
    return FundEntity(
        uuid = uuid,
        name = name,
        balanceCents = balanceCents,
        allocationPerCycleCents = allocationPerCycleCents,
        targetAmountCents = null,
        sortOrder = sortOrder,
        originInstallId = originInstallId,
        lastModifiedByInstallId = lastModifiedByInstallId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        closedAtEpochMs = closedAtEpochMs,
        deletedAtEpochMs = deletedAtEpochMs,
        modClock = modClock
    )
}

internal fun bucketEntity(
    id: Long = 1L,
    bucketUuid: String = DEFAULT_SPENDING_BUCKET_UUID,
    name: String = DEFAULT_SPENDING_BUCKET_NAME,
    trackingMode: BucketTrackingMode = BucketTrackingMode.DAILY_TARGET,
    balanceBehavior: BucketBalanceBehavior = BucketBalanceBehavior.RETURN_TO_PORTFOLIO,
    defaultAllocatedAmountCents: Long = 0L,
    sortOrder: Int = 0,
    originInstallId: String = "test-install-id",
    lastModifiedByInstallId: String = "test-install-id",
    createdAtEpochMs: Long = 1L,
    updatedAtEpochMs: Long = createdAtEpochMs,
    closedAtEpochMs: Long? = null,
    settledCloseCycleEndDateExclusive: String? = null,
    deletedAtEpochMs: Long? = null,
    modClock: String = "%013d-%04d-%s".format(updatedAtEpochMs, 0, lastModifiedByInstallId)
): BudgetBucketEntity {
    return BudgetBucketEntity(
        id = id,
        bucketUuid = bucketUuid,
        name = name,
        trackingMode = trackingMode,
        balanceBehavior = balanceBehavior,
        defaultAllocatedAmountCents = defaultAllocatedAmountCents,
        sortOrder = sortOrder,
        originInstallId = originInstallId,
        lastModifiedByInstallId = lastModifiedByInstallId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        settledCloseCycleEndDateExclusive = settledCloseCycleEndDateExclusive,
        closedAtEpochMs = closedAtEpochMs,
        deletedAtEpochMs = deletedAtEpochMs,
        modClock = modClock
    )
}

internal fun bucketPolicyEntity(
    id: Long = 1L,
    allocationUuid: String = "bucket-policy-1",
    bucketUuid: String = DEFAULT_SPENDING_BUCKET_UUID,
    cycleStartDate: String = "2026-03-01",
    cycleEndDateExclusive: String = "2026-04-01",
    allocatedAmountCents: Long = 0L,
    originInstallId: String = "test-install-id",
    lastModifiedByInstallId: String = "test-install-id",
    createdAtEpochMs: Long = 1L,
    updatedAtEpochMs: Long = createdAtEpochMs,
    deletedAtEpochMs: Long? = null,
    modClock: String = "%013d-%04d-%s".format(updatedAtEpochMs, 0, lastModifiedByInstallId)
): BucketAllocationPolicyEntity {
    return BucketAllocationPolicyEntity(
        id = id,
        allocationUuid = allocationUuid,
        bucketUuid = bucketUuid,
        cycleStartDate = cycleStartDate,
        cycleEndDateExclusive = cycleEndDateExclusive,
        allocatedAmountCents = allocatedAmountCents,
        originInstallId = originInstallId,
        lastModifiedByInstallId = lastModifiedByInstallId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        deletedAtEpochMs = deletedAtEpochMs,
        modClock = modClock
    )
}
