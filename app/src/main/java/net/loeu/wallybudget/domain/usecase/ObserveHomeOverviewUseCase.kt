package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import net.loeu.wallybudget.data.local.dao.BucketAllocationAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationPolicyDao
import net.loeu.wallybudget.data.local.dao.BucketMonthlyHistoryDao
import net.loeu.wallybudget.data.local.dao.BudgetBucketDao
import net.loeu.wallybudget.data.local.dao.BudgetAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.CycleOverviewDao
import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.entity.toDomainModel as bucketAdjustmentToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel as bucketHistoryToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel as bucketPolicyToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel as bucketToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel as adjustmentToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel as policyToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel
import net.loeu.wallybudget.domain.model.BucketAllocationAdjustment
import net.loeu.wallybudget.domain.model.BucketAllocationPolicy
import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.BudgetAdjustment
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.data.time.CurrentDateProvider
import net.loeu.wallybudget.domain.model.BudgetPolicy
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.HomeOverviewState
import net.loeu.wallybudget.domain.model.MonthlyHistory
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.model.groupByDate
import net.loeu.wallybudget.domain.model.sumByDate
import net.loeu.wallybudget.domain.model.toMonthlyHistory
import net.loeu.wallybudget.domain.service.BucketAllocationResolver
import net.loeu.wallybudget.domain.service.BudgetAdjustmentResolver
import net.loeu.wallybudget.domain.service.CycleDateRange
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import net.loeu.wallybudget.domain.service.ResolvedCyclePolicy
import net.loeu.wallybudget.domain.usecase.internal.buildContinuousDaySections
import net.loeu.wallybudget.domain.usecase.internal.buildPendingCycleCloseoutState
import net.loeu.wallybudget.domain.usecase.internal.buildTimelineLockState
import net.loeu.wallybudget.domain.usecase.internal.effectiveCurrentDate
import net.loeu.wallybudget.domain.usecase.internal.filterByRange
import net.loeu.wallybudget.domain.usecase.internal.lastResetDateOrNull
import net.loeu.wallybudget.domain.usecase.internal.pendingCycleRangeOrNull
import net.loeu.wallybudget.domain.usecase.internal.toDayTotalsMap
import java.time.LocalDate

private data class CurrentHomeOverviewInputs(
    val settings: UserSettings,
    val today: LocalDate,
    val currentExpenses: List<Expense>,
    val historyEntries: List<MonthlyHistory>,
    val currentDayTotals: Map<LocalDate, Long>,
    val budgetPolicies: List<BudgetPolicy>,
    val currentPolicy: ResolvedCyclePolicy,
    val currentAdjustments: List<BucketAllocationAdjustment>
)

private data class EffectiveHomeDateInputs(
    val settings: UserSettings,
    val today: LocalDate
)

private fun observeLatestExpenseDate(expenseDao: ExpenseDao): Flow<LocalDate?> {
    return expenseDao.observeLatestExpenseDate().map { date ->
        date?.let(LocalDate::parse)
    }
}

private fun observePendingCycle(userSettings: Flow<UserSettings>) = userSettings
    .map { settings -> settings.pendingCycleRangeOrNull() }
    .distinctUntilChanged()

private fun observeSelectedBucketUuid(userSettings: Flow<UserSettings>) = userSettings
    .map { settings ->
        settings.selectedBucketUuid ?: settings.primaryBucketUuid ?: DEFAULT_SPENDING_BUCKET_UUID
    }
    .distinctUntilChanged()

private fun observeSelectedBucket(
    selectedBucketUuid: Flow<String>,
    budgetBucketDao: BudgetBucketDao
): Flow<BudgetBucket?> {
    val activeBuckets = budgetBucketDao.observeAllActive().map { entries ->
        entries.map { it.bucketToDomainModel() }
    }
    return combine(selectedBucketUuid, activeBuckets) { bucketUuid, buckets ->
        buckets.firstOrNull { it.bucketUuid == bucketUuid }
    }.distinctUntilChanged()
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun observeSelectedBucketHistory(
    selectedBucketUuid: Flow<String>,
    bucketMonthlyHistoryDao: BucketMonthlyHistoryDao
): Flow<List<MonthlyHistory>> {
    return selectedBucketUuid.flatMapLatest { bucketUuid ->
        bucketMonthlyHistoryDao.observeForBucket(bucketUuid).map { entries ->
            entries.map { it.bucketHistoryToDomainModel().toMonthlyHistory() }
        }
    }
}

private fun observePortfolioPolicies(budgetPolicyDao: BudgetPolicyDao): Flow<List<BudgetPolicy>> {
    return budgetPolicyDao.observeActivePolicies().map { entries ->
        entries.map { it.policyToDomainModel() }
    }
}

private fun observeBucketPolicies(
    bucketAllocationPolicyDao: BucketAllocationPolicyDao
): Flow<List<BucketAllocationPolicy>> {
    return bucketAllocationPolicyDao.observeActivePolicies().map { entries ->
        entries.map { it.bucketPolicyToDomainModel() }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun observeCurrentBucketAdjustments(
    selectedBucketUuid: Flow<String>,
    currentPolicy: Flow<ResolvedCyclePolicy>,
    bucketAllocationAdjustmentDao: BucketAllocationAdjustmentDao
): Flow<List<BucketAllocationAdjustment>> {
    return combine(selectedBucketUuid, currentPolicy) { bucketUuid, policy ->
        bucketUuid to policy.cycleStart.toString()
    }
        .distinctUntilChanged()
        .flatMapLatest { (bucketUuid, cycleStart) ->
            bucketAllocationAdjustmentDao.observeActiveForCycle(bucketUuid, cycleStart)
                .map { entries -> entries.map { it.bucketAdjustmentToDomainModel() } }
        }
}

private fun combineHomeState(
    currentInputs: Flow<CurrentHomeOverviewInputs>,
    pendingCycleExpenses: Flow<List<Expense>>,
    pendingCycleDayTotals: Flow<Map<LocalDate, Long>>,
    pendingCycleAdjustments: Flow<List<BudgetAdjustment>>,
    latestExpenseDate: Flow<LocalDate?>,
    buildState: (
        CurrentHomeOverviewInputs,
        List<Expense>,
        Map<LocalDate, Long>,
        List<BudgetAdjustment>,
        LocalDate?
    ) -> HomeOverviewState
): Flow<HomeOverviewState> {
    return combine(
        currentInputs,
        pendingCycleExpenses,
        pendingCycleDayTotals,
        pendingCycleAdjustments,
        latestExpenseDate
    ) { currentInputsValue, pendingExpenses, pendingDayTotals, pendingAdjustments, latestRecordedExpenseDate ->
        buildState(
            currentInputsValue,
            pendingExpenses,
            pendingDayTotals,
            pendingAdjustments,
            latestRecordedExpenseDate
        )
    }.distinctUntilChanged()
}

class ObserveHomeOverviewUseCase(
    private val expenseDao: ExpenseDao,
    private val cycleOverviewDao: CycleOverviewDao,
    private val budgetAdjustmentDao: BudgetAdjustmentDao,
    private val budgetPolicyDao: BudgetPolicyDao,
    private val budgetBucketDao: BudgetBucketDao,
    private val bucketAllocationPolicyDao: BucketAllocationPolicyDao,
    private val bucketAllocationAdjustmentDao: BucketAllocationAdjustmentDao,
    private val bucketMonthlyHistoryDao: BucketMonthlyHistoryDao,
    private val userSettingsStore: UserSettingsStore,
    private val currentDateProvider: CurrentDateProvider,
    private val budgetCalculationService: BudgetCalculationService,
    private val cycleScheduleResolver: CycleScheduleResolver,
    private val budgetAdjustmentResolver: BudgetAdjustmentResolver,
    private val bucketAllocationResolver: BucketAllocationResolver
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<HomeOverviewState> {
        val userSettings = userSettingsStore.userSettings
        val effectiveInputs = observeEffectiveInputs(userSettings)
        val pendingCycle = observePendingCycle(userSettings)
        val selectedBucketUuid = observeSelectedBucketUuid(userSettings)
        val selectedBucket = observeSelectedBucket(selectedBucketUuid, budgetBucketDao)
        val history = observeSelectedBucketHistory(selectedBucketUuid, bucketMonthlyHistoryDao)
        val budgetPolicies = observePortfolioPolicies(budgetPolicyDao)
        val bucketPolicies = observeBucketPolicies(bucketAllocationPolicyDao)
        val currentPolicy = combine(
            effectiveInputs,
            selectedBucket,
            bucketPolicies
        ) { inputs, bucket, policies ->
            resolveCurrentBucketPolicy(inputs, bucket, policies)
        }.distinctUntilChanged()
        val currentCycleRange = observeCurrentCycleRange(currentPolicy)
        val currentAdjustments = observeCurrentBucketAdjustments(
            selectedBucketUuid = selectedBucketUuid,
            currentPolicy = currentPolicy,
            bucketAllocationAdjustmentDao = bucketAllocationAdjustmentDao
        )
        val currentInputs = observeCurrentInputs(
            userSettings = userSettings,
            effectiveInputs = effectiveInputs,
            selectedBucket = selectedBucket,
            selectedBucketUuid = selectedBucketUuid,
            history = history,
            currentCycleRange = currentCycleRange,
            budgetPolicies = budgetPolicies,
            currentPolicy = currentPolicy,
            currentAdjustments = currentAdjustments
        )

        return combineHomeState(
            currentInputs,
            observePendingCycleExpenses(pendingCycle),
            observePendingCycleDayTotals(pendingCycle),
            observePendingCycleAdjustments(pendingCycle),
            observeLatestExpenseDate(expenseDao),
            buildState = ::buildHomeOverviewState
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeCurrentInputs(
        userSettings: Flow<UserSettings>,
        effectiveInputs: Flow<EffectiveHomeDateInputs>,
        selectedBucket: Flow<BudgetBucket?>,
        selectedBucketUuid: Flow<String>,
        history: Flow<List<MonthlyHistory>>,
        currentCycleRange: Flow<CycleDateRange>,
        budgetPolicies: Flow<List<BudgetPolicy>>,
        currentPolicy: Flow<ResolvedCyclePolicy>,
        currentAdjustments: Flow<List<BucketAllocationAdjustment>>
    ): Flow<CurrentHomeOverviewInputs> {
        val currentExpenses = observeExpensesInRange(currentCycleRange)
        val bucketSelection = combine(
            selectedBucketUuid,
            selectedBucket
        ) { selectedBucketUuidValue, selectedBucketValue ->
            selectedBucketUuidValue to selectedBucketValue
        }
        val baseInputs = combine(
            userSettings,
            effectiveInputs.map { it.today }.distinctUntilChanged(),
            currentExpenses,
            history,
            bucketSelection
        ) { settings, today, cycleExpenses, historyEntries, bucketSelectionValue ->
            val selectedBucketUuidValue = bucketSelectionValue.first
            val selectedBucketValue = bucketSelectionValue.second
            val bucketExpenses = cycleExpenses.filter { it.bucketUuid == selectedBucketUuidValue }
            CurrentHomeOverviewInputs(
                settings = settings,
                today = today,
                currentExpenses = bucketExpenses,
                historyEntries = historyEntries,
                currentDayTotals = bucketExpenses.sumByDate(),
                budgetPolicies = emptyList(),
                currentPolicy = ResolvedCyclePolicy(
                    cycleStart = today,
                    cycleEndExclusive = today.plusDays(1),
                    budgetAmountCents = selectedBucketValue?.defaultAllocatedAmountCents
                        ?: settings.monthlyBudgetCents,
                    paydayDayOfMonth = settings.paydayDate
                ),
                currentAdjustments = emptyList()
            )
        }
        val policyInputs = combine(
            budgetPolicies,
            currentPolicy,
            currentAdjustments
        ) { portfolioPolicies, resolvedPolicy, adjustments ->
            resolvedPolicy to adjustments to portfolioPolicies
        }
        return combine(baseInputs, policyInputs) { inputs, policyInputsValue ->
            inputs.copy(
                budgetPolicies = policyInputsValue.second,
                currentPolicy = policyInputsValue.first.first,
                currentAdjustments = policyInputsValue.first.second
            )
        }
    }

    private fun observeEffectiveInputs(
        userSettings: Flow<UserSettings>
    ): Flow<EffectiveHomeDateInputs> {
        return combine(
            userSettings,
            currentDateProvider.observeCurrentDate()
        ) { settings, observedDate ->
            EffectiveHomeDateInputs(
                settings = settings,
                today = effectiveCurrentDate(settings, observedDate)
            )
        }.distinctUntilChanged()
    }

    private fun observeCurrentCycleRange(
        currentPolicy: Flow<ResolvedCyclePolicy>
    ): Flow<CycleDateRange> {
        return combine(
            currentPolicy,
            currentDateProvider.observeCurrentDate(),
            userSettingsStore.userSettings
        ) { resolvedPolicy, observedDate, settings ->
            val effectiveDate = effectiveCurrentDate(settings, observedDate)
            CycleDateRange(
                start = resolvedPolicy.cycleStart,
                endExclusive = minOf(effectiveDate.plusDays(1), resolvedPolicy.cycleEndExclusive)
            )
        }.distinctUntilChanged()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeExpensesInRange(
        range: Flow<CycleDateRange>
    ): Flow<List<Expense>> {
        return range.flatMapLatest { cycleRange ->
            expenseDao.observeInRange(
                startDateInclusive = cycleRange.start.toString(),
                endDateExclusive = cycleRange.endExclusive.toString()
            ).map { expenses -> expenses.map { it.toDomainModel() } }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observePendingCycleExpenses(
        pendingCycle: Flow<net.loeu.wallybudget.domain.usecase.internal.CycleRange?>
    ): Flow<List<Expense>> {
        return pendingCycle.flatMapLatest { range ->
            if (range == null) {
                flowOf(emptyList())
            } else {
                expenseDao.observeInRange(
                    startDateInclusive = range.start.toString(),
                    endDateExclusive = range.endExclusive.toString()
                ).map { expenses -> expenses.map { it.toDomainModel() } }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observePendingCycleDayTotals(
        pendingCycle: Flow<net.loeu.wallybudget.domain.usecase.internal.CycleRange?>
    ): Flow<Map<LocalDate, Long>> {
        return pendingCycle.flatMapLatest { range ->
            if (range == null) {
                flowOf(emptyMap())
            } else {
                cycleOverviewDao.observeDayTotalsInRange(
                    startDateInclusive = range.start.toString(),
                    endDateExclusive = range.endExclusive.toString()
                ).map { rows -> rows.toDayTotalsMap() }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observePendingCycleAdjustments(
        pendingCycle: Flow<net.loeu.wallybudget.domain.usecase.internal.CycleRange?>
    ): Flow<List<BudgetAdjustment>> {
        return pendingCycle.flatMapLatest { range ->
            if (range == null) {
                flowOf(emptyList())
            } else {
                budgetAdjustmentDao.observeActiveForCycle(range.start.toString())
                    .map { entries -> entries.map { it.adjustmentToDomainModel() } }
            }
        }
    }

    private fun buildHomeOverviewState(
        currentInputs: CurrentHomeOverviewInputs,
        pendingExpenses: List<Expense>,
        pendingDayTotals: Map<LocalDate, Long>,
        pendingAdjustments: List<BudgetAdjustment>,
        latestRecordedExpenseDate: LocalDate?
    ): HomeOverviewState {
        val todayExpenses = currentInputs.currentExpenses.filterByRange(
            start = currentInputs.today,
            endExclusive = currentInputs.today.plusDays(1)
        )
        val totalSpentThisCycleCents = currentInputs.currentExpenses.sumOf { it.amountCents }
        val spentTodayCents = todayExpenses.sumOf { it.amountCents }
        val cumulativeSavingsCents = currentInputs.historyEntries
            .filter { !it.getCycleEnd().isAfter(currentInputs.currentPolicy.cycleStart) }
            .sumOf { it.surplusCents }
        val resolvedBucketAllocation = bucketAllocationResolver.resolveBucketAllocation(
            cycleStart = currentInputs.currentPolicy.cycleStart,
            cycleEndExclusive = currentInputs.currentPolicy.cycleEndExclusive,
            baseAllocatedAmountCents = currentInputs.currentPolicy.budgetAmountCents,
            adjustments = currentInputs.currentAdjustments,
            today = currentInputs.today
        )
        val budgetState = budgetCalculationService.calculateBudgetStateForResolvedCycle(
            now = currentInputs.today,
            cycleStart = currentInputs.currentPolicy.cycleStart,
            cycleEndExclusive = currentInputs.currentPolicy.cycleEndExclusive,
            cycleBudgetAmountCents = resolvedBucketAllocation.effectiveCycleAllocationCents,
            plannedTodayBudgetCents = resolvedBucketAllocation.plannedTodayAllocationCents,
            allocatedBeforeTodayCents = resolvedBucketAllocation.allocatedBeforeDateCents,
            totalSpentThisCycleCents = totalSpentThisCycleCents,
            spentTodayCents = spentTodayCents,
            cumulativeSavingsCents = cumulativeSavingsCents,
            paydayDate = currentInputs.currentPolicy.paydayDayOfMonth
        )
        val timelineLockState = buildTimelineLockState(
            effectiveCurrentDate = currentInputs.today,
            currentCycleStart = currentInputs.currentPolicy.cycleStart,
            lastResetDate = currentInputs.settings.lastResetDateOrNull(),
            latestExpenseDate = latestRecordedExpenseDate,
        )
        val activeCycleSections = buildContinuousDaySections(
            start = budgetState.cycleStartDate,
            endInclusive = currentInputs.today,
            expensesByDate = currentInputs.currentExpenses.groupByDate(),
            dayTotals = currentInputs.currentDayTotals,
            remainingBudgetForDay = { totalSpent -> budgetState.dailyBudgetCents - totalSpent },
            isEditable = !timelineLockState.isLocked,
            today = currentInputs.today
        )

        return HomeOverviewState(
            effectiveCurrentDate = currentInputs.today,
            budgetState = budgetState,
            todayExpenses = todayExpenses,
            activeCycleExpenseSections = activeCycleSections,
            pendingCycleCloseoutState = buildPendingCycleCloseoutState(
                settings = currentInputs.settings,
                expenses = pendingExpenses,
                dayTotals = pendingDayTotals,
                adjustments = pendingAdjustments,
                resolvedPendingPolicy = resolvePendingPolicy(currentInputs),
                budgetAdjustmentResolver = budgetAdjustmentResolver
            ),
            timelineLockState = timelineLockState
        )
    }

    private fun resolvePendingPolicy(
        currentInputs: CurrentHomeOverviewInputs
    ): ResolvedCyclePolicy? {
        return currentInputs.settings.pendingCycleRangeOrNull()?.let { pendingCycle ->
            cycleScheduleResolver.policyForCycleStart(
                cycleStart = pendingCycle.start,
                settings = currentInputs.settings,
                policies = currentInputs.budgetPolicies
            )
        }
    }

    private fun resolveCurrentBucketPolicy(
        inputs: EffectiveHomeDateInputs,
        bucket: BudgetBucket?,
        policies: List<BucketAllocationPolicy>
    ): ResolvedCyclePolicy {
        val portfolioPolicy = cycleScheduleResolver.resolvePolicyForDate(
            date = inputs.today,
            settings = inputs.settings,
            policies = emptyList()
        )
        val selectedBucket = bucket ?: return portfolioPolicy.copy(
            budgetAmountCents = inputs.settings.monthlyBudgetCents
        )
        val persistedPolicy = policies
            .filter { it.deletedAtEpochMs == null }
            .firstOrNull { policy ->
                policy.bucketUuid == selectedBucket.bucketUuid &&
                    !inputs.today.isBefore(policy.cycleStart()) &&
                    inputs.today.isBefore(policy.cycleEndExclusive())
            }
        return if (persistedPolicy != null) {
            ResolvedCyclePolicy(
                cycleStart = persistedPolicy.cycleStart(),
                cycleEndExclusive = persistedPolicy.cycleEndExclusive(),
                budgetAmountCents = persistedPolicy.allocatedAmountCents,
                paydayDayOfMonth = inputs.settings.paydayDate
            )
        } else {
            portfolioPolicy.copy(budgetAmountCents = selectedBucket.defaultAllocatedAmountCents)
        }
    }
}
