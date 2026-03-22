@file:Suppress("LongMethod", "TooManyFunctions", "MaxLineLength")

package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import net.loeu.wallybudget.data.local.dao.BucketAllocationAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationPolicyDao
import net.loeu.wallybudget.data.local.dao.BucketMonthlyHistoryDao
import net.loeu.wallybudget.data.local.dao.BudgetAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BudgetBucketDao
import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.CycleOverviewDao
import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.dao.FundDao
import net.loeu.wallybudget.data.local.entity.toDomainModel
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.data.time.CurrentDateProvider
import net.loeu.wallybudget.domain.model.BudgetAdjustment
import net.loeu.wallybudget.domain.model.BucketAllocationAdjustment
import net.loeu.wallybudget.domain.model.BucketAllocationPolicy
import net.loeu.wallybudget.domain.model.BucketMonthlyHistory
import net.loeu.wallybudget.domain.model.BucketSummaryState
import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.BudgetPolicy
import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.ExpenseDaySection
import net.loeu.wallybudget.domain.model.Fund
import net.loeu.wallybudget.domain.model.MonthlyHistory
import net.loeu.wallybudget.domain.model.PortfolioOverviewState
import net.loeu.wallybudget.domain.model.SelectedBucketOverview
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.model.groupByDate
import net.loeu.wallybudget.domain.model.sumByDate
import net.loeu.wallybudget.domain.model.toMonthlyHistory
import net.loeu.wallybudget.domain.service.BucketAllocationResolver
import net.loeu.wallybudget.domain.service.BudgetAdjustmentResolver
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.CycleDateRange
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import net.loeu.wallybudget.domain.service.PortfolioCalculationService
import net.loeu.wallybudget.domain.service.ResolvedCyclePolicy
import net.loeu.wallybudget.domain.usecase.internal.CycleRange
import net.loeu.wallybudget.domain.usecase.internal.buildContinuousDaySections
import net.loeu.wallybudget.domain.usecase.internal.buildPendingCycleCloseoutState
import net.loeu.wallybudget.domain.usecase.internal.buildTimelineLockState
import net.loeu.wallybudget.domain.usecase.internal.effectiveCurrentDate
import net.loeu.wallybudget.domain.usecase.internal.filterByRange
import net.loeu.wallybudget.domain.usecase.internal.lastResetDateOrNull
import net.loeu.wallybudget.domain.usecase.internal.pendingCycleRangeOrNull
import net.loeu.wallybudget.domain.usecase.internal.resolveSelectedOpenBucket
import net.loeu.wallybudget.domain.usecase.internal.toDayTotalsMap
import java.time.LocalDate

private data class EffectivePortfolioInputs(
    val settings: UserSettings,
    val today: LocalDate
)

private data class CurrentPortfolioInputs(
    val settings: UserSettings,
    val today: LocalDate,
    val activeBuckets: List<BudgetBucket>,
    val selectedBucket: BudgetBucket?,
    val selectedBucketUuid: String,
    val portfolioPolicy: ResolvedCyclePolicy,
    val portfolioAdjustments: List<BudgetAdjustment>,
    val bucketPolicies: List<BucketAllocationPolicy>,
    val bucketAdjustments: List<BucketAllocationAdjustment>,
    val funds: List<Fund>,
    val currentExpenses: List<Expense>,
    val allBucketHistory: List<BucketMonthlyHistory>
)

private fun observeLatestExpenseDate(expenseDao: ExpenseDao): Flow<LocalDate?> {
    return expenseDao.observeLatestExpenseDate().map { date -> date?.let(LocalDate::parse) }
}

private fun observePendingCycle(userSettings: Flow<UserSettings>) = userSettings
    .map { settings -> settings.pendingCycleRangeOrNull() }
    .distinctUntilChanged()

private fun observeSelectedBucketUuid(userSettings: Flow<UserSettings>) = userSettings
    .map { settings ->
        settings.selectedBucketUuid ?: DEFAULT_SPENDING_BUCKET_UUID
    }
    .distinctUntilChanged()

private fun observeActiveBuckets(budgetBucketDao: BudgetBucketDao): Flow<List<BudgetBucket>> {
    return budgetBucketDao.observeAllActive().map { entries -> entries.map { it.toDomainModel() } }
}

private fun observeAllBucketHistory(bucketMonthlyHistoryDao: BucketMonthlyHistoryDao): Flow<List<BucketMonthlyHistory>> {
    return bucketMonthlyHistoryDao.observeAll().map { entries ->
        entries.map { it.toDomainModel() }
    }
}

private fun observeActiveFunds(fundDao: FundDao): Flow<List<Fund>> {
    return fundDao.observeAllActive().map { entries -> entries.map { it.toDomainModel() } }
}

private fun observePortfolioPolicies(budgetPolicyDao: BudgetPolicyDao): Flow<List<BudgetPolicy>> {
    return budgetPolicyDao.observeActivePolicies().map { entries ->
        entries.map { it.toDomainModel() }
    }
}

private fun observeBucketPolicies(bucketAllocationPolicyDao: BucketAllocationPolicyDao): Flow<List<BucketAllocationPolicy>> {
    return bucketAllocationPolicyDao.observeActivePolicies().map { entries ->
        entries.map { it.toDomainModel() }
    }
}

private fun observeAllBucketAdjustments(
    bucketAllocationAdjustmentDao: BucketAllocationAdjustmentDao
): Flow<List<BucketAllocationAdjustment>> {
    return bucketAllocationAdjustmentDao.observeAllActive().map { entries ->
        entries.map { it.toDomainModel() }
    }
}

private fun observePortfolioEffectiveInputs(
    userSettings: Flow<UserSettings>,
    currentDateProvider: CurrentDateProvider
): Flow<EffectivePortfolioInputs> {
    return combine(userSettings, currentDateProvider.observeCurrentDate()) { settings, observedDate ->
        EffectivePortfolioInputs(
            settings = settings,
            today = effectiveCurrentDate(settings, observedDate)
        )
    }.distinctUntilChanged()
}

private fun observePortfolioCurrentPolicy(
    effectiveInputs: Flow<EffectivePortfolioInputs>,
    budgetPolicies: Flow<List<BudgetPolicy>>,
    cycleScheduleResolver: CycleScheduleResolver
): Flow<ResolvedCyclePolicy> {
    return combine(effectiveInputs, budgetPolicies) { inputs, policies ->
        cycleScheduleResolver.resolvePolicyForDate(
            date = inputs.today,
            settings = inputs.settings,
            policies = policies
        )
    }.distinctUntilChanged()
}

private fun observePortfolioCurrentCycleRange(
    currentPolicy: Flow<ResolvedCyclePolicy>,
    userSettingsStore: UserSettingsStore,
    currentDateProvider: CurrentDateProvider
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
    expenseDao: ExpenseDao,
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
    expenseDao: ExpenseDao,
    pendingCycle: Flow<CycleRange?>
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
    cycleOverviewDao: CycleOverviewDao,
    pendingCycle: Flow<CycleRange?>
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
    budgetAdjustmentDao: BudgetAdjustmentDao,
    pendingCycle: Flow<CycleRange?>
): Flow<List<BudgetAdjustment>> {
    return pendingCycle.flatMapLatest { range ->
        if (range == null) {
            flowOf(emptyList())
        } else {
            budgetAdjustmentDao.observeActiveForCycle(range.start.toString())
                .map { entries -> entries.map { it.toDomainModel() } }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun observeCurrentPortfolioAdjustments(
    budgetAdjustmentDao: BudgetAdjustmentDao,
    currentPolicy: Flow<ResolvedCyclePolicy>
): Flow<List<BudgetAdjustment>> {
    return currentPolicy
        .map { it.cycleStart.toString() }
        .distinctUntilChanged()
        .flatMapLatest { cycleStart ->
            budgetAdjustmentDao.observeActiveForCycle(cycleStart)
                .map { entries -> entries.map { it.toDomainModel() } }
        }
}

private fun resolvedSelectedBucket(
    buckets: List<BudgetBucket>,
    selectedBucketUuid: String
): BudgetBucket? {
    return resolveSelectedOpenBucket(selectedBucketUuid, buckets)
}

private fun resolveCurrentBucketPolicy(
    bucket: BudgetBucket,
    portfolioPolicy: ResolvedCyclePolicy,
    paydayDayOfMonth: Int,
    bucketPolicies: List<BucketAllocationPolicy>
): ResolvedCyclePolicy {
    val persistedPolicy = bucketPolicies
        .filter { it.deletedAtEpochMs == null }
        .firstOrNull { policy ->
            policy.bucketUuid == bucket.bucketUuid &&
                policy.cycleStart() == portfolioPolicy.cycleStart &&
                policy.cycleEndExclusive() == portfolioPolicy.cycleEndExclusive
        }
    return if (persistedPolicy != null) {
        ResolvedCyclePolicy(
            cycleStart = persistedPolicy.cycleStart(),
            cycleEndExclusive = persistedPolicy.cycleEndExclusive(),
            budgetAmountCents = persistedPolicy.allocatedAmountCents,
            paydayDayOfMonth = paydayDayOfMonth
        )
    } else {
        portfolioPolicy.copy(budgetAmountCents = bucket.defaultAllocatedAmountCents)
    }
}

private fun buildBucketSummaryState(
    bucket: BudgetBucket,
    portfolioPolicy: ResolvedCyclePolicy,
    today: LocalDate,
    paydayDayOfMonth: Int,
    bucketPolicies: List<BucketAllocationPolicy>,
    bucketAdjustments: List<BucketAllocationAdjustment>,
    currentExpenses: List<Expense>,
    bucketAllocationResolver: BucketAllocationResolver,
    budgetCalculationService: BudgetCalculationService
): BucketSummaryState {
    val resolvedPolicy = resolveCurrentBucketPolicy(
        bucket = bucket,
        portfolioPolicy = portfolioPolicy,
        paydayDayOfMonth = paydayDayOfMonth,
        bucketPolicies = bucketPolicies
    )
    val cycleAdjustments = bucketAdjustments
        .filter { it.bucketUuid == bucket.bucketUuid }
        .filter { it.cycleStart() == portfolioPolicy.cycleStart }
    val resolvedAllocation = bucketAllocationResolver.resolveBucketAllocation(
        cycleStart = resolvedPolicy.cycleStart,
        cycleEndExclusive = resolvedPolicy.cycleEndExclusive,
        baseAllocatedAmountCents = resolvedPolicy.budgetAmountCents,
        adjustments = cycleAdjustments,
        today = today
    )
    val bucketCycleExpenses = currentExpenses.filter { it.bucketUuid == bucket.bucketUuid }
    val spentThisCycleCents = bucketCycleExpenses.sumOf { it.amountCents }
    val remainingThisCycleCents = resolvedAllocation.effectiveCycleAllocationCents - spentThisCycleCents
    val overspentCents = (-remainingThisCycleCents).coerceAtLeast(0L)
    val todayExpenses = bucketCycleExpenses.filterByRange(today, today.plusDays(1))
    val budgetState = budgetCalculationService.calculateBudgetStateForResolvedCycle(
        now = today,
        cycleStart = resolvedPolicy.cycleStart,
        cycleEndExclusive = resolvedPolicy.cycleEndExclusive,
        cycleBudgetAmountCents = resolvedAllocation.effectiveCycleAllocationCents,
        plannedTodayBudgetCents = resolvedAllocation.plannedTodayAllocationCents,
        allocatedBeforeTodayCents = resolvedAllocation.allocatedBeforeDateCents,
        totalSpentThisCycleCents = spentThisCycleCents,
        spentTodayCents = todayExpenses.sumOf { it.amountCents },
        paydayDate = paydayDayOfMonth
    )

    return BucketSummaryState(
        bucket = bucket,
        allocatedThisCycleCents = resolvedAllocation.effectiveCycleAllocationCents,
        spentThisCycleCents = spentThisCycleCents,
        remainingThisCycleCents = remainingThisCycleCents,
        overspentCents = overspentCents,
        budgetState = budgetState
    )
}

private fun buildSelectedBucketOverview(
    selectedBucket: BudgetBucket,
    selectedBucketSummary: BucketSummaryState,
    currentCycleStart: LocalDate,
    today: LocalDate,
    currentExpenses: List<Expense>
): SelectedBucketOverview {
    val bucketExpenses = currentExpenses.filter { it.bucketUuid == selectedBucket.bucketUuid }
    val todayExpenses = bucketExpenses.filterByRange(today, today.plusDays(1))
    val dayTotals = bucketExpenses.sumByDate()
    val activeCycleExpenseSections = buildContinuousDaySections(
        start = selectedBucketSummary.budgetState?.cycleStartDate ?: currentCycleStart,
        endInclusive = today,
        expensesByDate = bucketExpenses.groupByDate(),
        dayTotals = dayTotals,
        remainingBudgetForDay = { totalSpent ->
            selectedBucketSummary.budgetState?.let { budgetState -> budgetState.dailyBudgetCents - totalSpent }
        },
        isEditable = true,
        today = today
    )
    return SelectedBucketOverview(
        bucket = selectedBucket,
        summary = selectedBucketSummary,
        budgetState = selectedBucketSummary.budgetState,
        todayExpenses = todayExpenses,
        activeCycleExpenseSections = activeCycleExpenseSections,
        spendingForecast = null
    )
}

class ObserveHomeOverviewUseCase(
    private val expenseDao: ExpenseDao,
    private val cycleOverviewDao: CycleOverviewDao,
    private val budgetAdjustmentDao: BudgetAdjustmentDao,
    private val budgetPolicyDao: BudgetPolicyDao,
    private val budgetBucketDao: BudgetBucketDao,
    private val fundDao: FundDao,
    private val bucketAllocationPolicyDao: BucketAllocationPolicyDao,
    private val bucketAllocationAdjustmentDao: BucketAllocationAdjustmentDao,
    private val bucketMonthlyHistoryDao: BucketMonthlyHistoryDao,
    private val userSettingsStore: UserSettingsStore,
    private val currentDateProvider: CurrentDateProvider,
    private val budgetCalculationService: BudgetCalculationService,
    private val cycleScheduleResolver: CycleScheduleResolver,
    private val budgetAdjustmentResolver: BudgetAdjustmentResolver,
    private val bucketAllocationResolver: BucketAllocationResolver,
    private val portfolioCalculationService: PortfolioCalculationService
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<PortfolioOverviewState> {
        val userSettings = userSettingsStore.userSettings
        val effectiveInputs = observePortfolioEffectiveInputs(userSettings, currentDateProvider)
        val pendingCycle = observePendingCycle(userSettings)
        val selectedBucketUuid = observeSelectedBucketUuid(userSettings)
        val activeBuckets = observeActiveBuckets(budgetBucketDao)
        val activeFunds = observeActiveFunds(fundDao)
        val bucketPolicies = observeBucketPolicies(bucketAllocationPolicyDao)
        val bucketAdjustments = observeAllBucketAdjustments(bucketAllocationAdjustmentDao)
        val allBucketHistory = observeAllBucketHistory(bucketMonthlyHistoryDao)
        val budgetPolicies = observePortfolioPolicies(budgetPolicyDao)
        val portfolioPolicy = observePortfolioCurrentPolicy(
            effectiveInputs = effectiveInputs,
            budgetPolicies = budgetPolicies,
            cycleScheduleResolver = cycleScheduleResolver
        )
        val portfolioCurrentAdjustments = observeCurrentPortfolioAdjustments(
            budgetAdjustmentDao = budgetAdjustmentDao,
            currentPolicy = portfolioPolicy
        )
        val currentCycleRange = observePortfolioCurrentCycleRange(
            currentPolicy = portfolioPolicy,
            userSettingsStore = userSettingsStore,
            currentDateProvider = currentDateProvider
        )
        val currentExpenses = observeExpensesInRange(expenseDao, currentCycleRange)

        val portfolioMutationInputs = combine(
            portfolioPolicy,
            portfolioCurrentAdjustments,
            bucketPolicies,
            bucketAdjustments
        ) { portfolioPolicyValue, portfolioAdjustmentsValue, bucketPoliciesValue, bucketAdjustmentsValue ->
            PortfolioMutationInputs(
                portfolioPolicy = portfolioPolicyValue,
                portfolioAdjustments = portfolioAdjustmentsValue,
                bucketPolicies = bucketPoliciesValue,
                bucketAdjustments = bucketAdjustmentsValue
            )
        }
        val portfolioCurrentSupportingInputs = combine(
            selectedBucketUuid,
            currentExpenses,
            allBucketHistory,
            portfolioMutationInputs
        ) { selectedBucketUuidValue, currentExpensesValue, allBucketHistoryValue, mutationInputs ->
            PortfolioCurrentSupportingInputs(
                selectedBucketUuid = selectedBucketUuidValue,
                currentExpenses = currentExpensesValue,
                allBucketHistory = allBucketHistoryValue,
                mutationInputs = mutationInputs
            )
        }
        val currentInputs = combine(
            effectiveInputs,
            activeBuckets,
            activeFunds,
            portfolioCurrentSupportingInputs
        ) { inputs, buckets, funds, supportingInputs ->
            CurrentPortfolioInputs(
                settings = inputs.settings,
                today = inputs.today,
                activeBuckets = buckets,
                selectedBucket = resolvedSelectedBucket(buckets, supportingInputs.selectedBucketUuid),
                selectedBucketUuid = supportingInputs.selectedBucketUuid,
                portfolioPolicy = supportingInputs.mutationInputs.portfolioPolicy,
                portfolioAdjustments = supportingInputs.mutationInputs.portfolioAdjustments,
                bucketPolicies = supportingInputs.mutationInputs.bucketPolicies,
                bucketAdjustments = supportingInputs.mutationInputs.bucketAdjustments,
                funds = funds,
                currentExpenses = supportingInputs.currentExpenses,
                allBucketHistory = supportingInputs.allBucketHistory
            )
        }

        val pendingInputs = combine(
            currentInputs,
            observePendingCycleExpenses(expenseDao, pendingCycle),
            observePendingCycleDayTotals(cycleOverviewDao, pendingCycle),
            observePendingCycleAdjustments(budgetAdjustmentDao, pendingCycle)
        ) { inputs, pendingExpenses, pendingDayTotals, pendingAdjustments ->
            PendingOverviewInputs(
                currentInputs = inputs,
                pendingExpenses = pendingExpenses,
                pendingDayTotals = pendingDayTotals,
                pendingAdjustments = pendingAdjustments
            )
        }

        return combine(
            pendingInputs,
            budgetPolicies,
            observeLatestExpenseDate(expenseDao)
        ) { pendingInputsValue, portfolioPolicies, latestExpenseDate ->
            buildPortfolioOverviewState(
                inputs = pendingInputsValue.currentInputs,
                pendingExpenses = pendingInputsValue.pendingExpenses,
                pendingDayTotals = pendingInputsValue.pendingDayTotals,
                pendingAdjustments = pendingInputsValue.pendingAdjustments,
                portfolioPolicies = portfolioPolicies,
                latestRecordedExpenseDate = latestExpenseDate
            )
        }.distinctUntilChanged()
    }

    private fun buildPortfolioOverviewState(
        inputs: CurrentPortfolioInputs,
        pendingExpenses: List<Expense>,
        pendingDayTotals: Map<LocalDate, Long>,
        pendingAdjustments: List<BudgetAdjustment>,
        portfolioPolicies: List<BudgetPolicy>,
        latestRecordedExpenseDate: LocalDate?
    ): PortfolioOverviewState {
        val bucketSummaries = inputs.activeBuckets.map { bucket ->
            buildBucketSummaryState(
                bucket = bucket,
                portfolioPolicy = inputs.portfolioPolicy,
                today = inputs.today,
                paydayDayOfMonth = inputs.settings.paydayDate,
                bucketPolicies = inputs.bucketPolicies,
                bucketAdjustments = inputs.bucketAdjustments,
                currentExpenses = inputs.currentExpenses,
                bucketAllocationResolver = bucketAllocationResolver,
                budgetCalculationService = budgetCalculationService
            )
        }
        // Display-only fallback when no bucket exists yet (e.g. during onboarding).
        // Never persisted — the empty modClock is intentional since this object only
        // drives the UI until EnsureDefaultBucketStateUseCase creates the real bucket.
        val selectedBucket = inputs.selectedBucket ?: inputs.activeBuckets.firstOrNull() ?: BudgetBucket(
            bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
            name = "Spending money",
            defaultAllocatedAmountCents = inputs.settings.monthlyBudgetCents,
            sortOrder = 0,
            originInstallId = inputs.settings.installDeviceId,
            lastModifiedByInstallId = inputs.settings.installDeviceId,
            createdAtEpochMs = 0L,
            updatedAtEpochMs = 0L,
            modClock = ""
        )
        val selectedBucketSummary = bucketSummaries.firstOrNull { it.bucket.bucketUuid == selectedBucket.bucketUuid }
            ?: buildBucketSummaryState(
                bucket = selectedBucket,
                portfolioPolicy = inputs.portfolioPolicy,
                today = inputs.today,
                paydayDayOfMonth = inputs.settings.paydayDate,
                bucketPolicies = inputs.bucketPolicies,
                bucketAdjustments = inputs.bucketAdjustments,
                currentExpenses = inputs.currentExpenses,
                bucketAllocationResolver = bucketAllocationResolver,
                budgetCalculationService = budgetCalculationService
            )
        val selectedBucketOverview = buildSelectedBucketOverview(
            selectedBucket = selectedBucket,
            selectedBucketSummary = selectedBucketSummary,
            currentCycleStart = inputs.portfolioPolicy.cycleStart,
            today = inputs.today,
            currentExpenses = inputs.currentExpenses
        )
        val portfolioBudgetAmountCents = budgetAdjustmentResolver.resolveEffectiveCycleBudgetAmount(
            cycleStart = inputs.portfolioPolicy.cycleStart,
            cycleEndExclusive = inputs.portfolioPolicy.cycleEndExclusive,
            baseMonthlyBudgetCents = inputs.portfolioPolicy.budgetAmountCents,
            adjustments = inputs.portfolioAdjustments
        )
        val portfolioState = portfolioCalculationService.calculatePortfolioState(
            portfolioTotalBudgetCents = portfolioBudgetAmountCents,
            bucketSummaries = bucketSummaries,
            funds = inputs.funds,
            totalSpentThisCycleCents = inputs.currentExpenses.sumOf { it.amountCents },
            bucketHistory = inputs.allBucketHistory,
            cycleStartDate = inputs.portfolioPolicy.cycleStart,
            cycleEndDateExclusive = inputs.portfolioPolicy.cycleEndExclusive
        )
        val timelineLockState = buildTimelineLockState(
            effectiveCurrentDate = inputs.today,
            currentCycleStart = inputs.portfolioPolicy.cycleStart,
            lastResetDate = inputs.settings.lastResetDateOrNull(),
            latestExpenseDate = latestRecordedExpenseDate
        )
        val pendingCycleCloseoutState = buildPendingCycleCloseoutState(
            settings = inputs.settings,
            expenses = pendingExpenses,
            dayTotals = pendingDayTotals,
            adjustments = pendingAdjustments,
            resolvedPendingPolicy = inputs.settings.pendingCycleRangeOrNull()?.let { pendingCycle ->
                cycleScheduleResolver.policyForCycleStart(
                    cycleStart = pendingCycle.start,
                    settings = inputs.settings,
                    policies = portfolioPolicies
                )
            },
            budgetAdjustmentResolver = budgetAdjustmentResolver
        )
        return PortfolioOverviewState(
            effectiveCurrentDate = inputs.today,
            portfolioState = portfolioState,
            funds = inputs.funds,
            bucketSummaries = bucketSummaries,
            selectedBucketOverview = selectedBucketOverview,
            pendingCycleCloseoutState = pendingCycleCloseoutState,
            timelineLockState = timelineLockState
        )
    }
}

private data class PortfolioMutationInputs(
    val portfolioPolicy: ResolvedCyclePolicy,
    val portfolioAdjustments: List<BudgetAdjustment>,
    val bucketPolicies: List<BucketAllocationPolicy>,
    val bucketAdjustments: List<BucketAllocationAdjustment>
)

private data class PendingOverviewInputs(
    val currentInputs: CurrentPortfolioInputs,
    val pendingExpenses: List<Expense>,
    val pendingDayTotals: Map<LocalDate, Long>,
    val pendingAdjustments: List<BudgetAdjustment>
)

private data class PortfolioCurrentSupportingInputs(
    val selectedBucketUuid: String,
    val currentExpenses: List<Expense>,
    val allBucketHistory: List<BucketMonthlyHistory>,
    val mutationInputs: PortfolioMutationInputs
)
