package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationPolicyDao
import net.loeu.wallybudget.data.local.dao.BucketCycleBaselineDao
import net.loeu.wallybudget.data.local.dao.BucketMonthlyHistoryDao
import net.loeu.wallybudget.data.local.dao.BucketTransferDao
import net.loeu.wallybudget.data.local.dao.BudgetBucketDao
import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.entity.toDomainModel as bucketBaselineToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel as bucketHistoryToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel as bucketToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel as policyToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.data.time.CurrentDateProvider
import net.loeu.wallybudget.domain.config.ForecastConfig
import net.loeu.wallybudget.domain.model.BucketAllocationAdjustment
import net.loeu.wallybudget.domain.model.BucketAllocationPolicy
import net.loeu.wallybudget.domain.model.BucketCycleBaseline
import net.loeu.wallybudget.domain.model.BudgetPolicy
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.MonthlyHistory
import net.loeu.wallybudget.domain.model.SpendingForecast
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BucketAllocationResolver
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import net.loeu.wallybudget.domain.service.CurrentCycleBucketAllocationResolver
import net.loeu.wallybudget.domain.service.ResolvedCyclePolicy
import net.loeu.wallybudget.domain.usecase.internal.effectiveCurrentDate
import net.loeu.wallybudget.domain.usecase.internal.filterByRange
import net.loeu.wallybudget.domain.usecase.internal.resolveSelectedOpenBucket
import java.time.LocalDate

private data class EffectiveForecastInputs(
    val settings: UserSettings,
    val today: LocalDate
)

private data class ForecastComposedInputs(
    val bucket: net.loeu.wallybudget.domain.model.BudgetBucket?,
    val historyEntries: List<MonthlyHistory>,
    val recentExpenseEntries: List<Expense>,
    val resolvedCurrentPolicy: ResolvedCyclePolicy,
    val adjustments: List<BucketAllocationAdjustment>
)

private data class CurrentPolicyBucketState(
    val portfolioPolicies: List<BudgetPolicy>,
    val currentBucketPolicies: List<BucketAllocationPolicy>,
    val currentBaselines: List<BucketCycleBaseline>,
    val currentTransfers: List<net.loeu.wallybudget.domain.model.BucketTransfer>
)

class ObserveForecastUseCase(
    private val budgetPolicyDao: BudgetPolicyDao,
    private val budgetBucketDao: BudgetBucketDao,
    @Suppress("UNUSED_PARAMETER")
    private val bucketAllocationPolicyDao: BucketAllocationPolicyDao? = null,
    @Suppress("UNUSED_PARAMETER")
    private val bucketAllocationAdjustmentDao:
        net.loeu.wallybudget.data.local.dao.BucketAllocationAdjustmentDao? = null,
    private val bucketCycleBaselineDao: BucketCycleBaselineDao? = null,
    private val bucketTransferDao: BucketTransferDao? = null,
    private val bucketMonthlyHistoryDao: BucketMonthlyHistoryDao,
    private val expenseDao: ExpenseDao,
    private val userSettingsStore: UserSettingsStore,
    private val currentDateProvider: CurrentDateProvider,
    private val budgetCalculationService: BudgetCalculationService,
    private val cycleScheduleResolver: CycleScheduleResolver,
    private val bucketAllocationResolver: BucketAllocationResolver,
    private val currentCycleBucketAllocationResolver: CurrentCycleBucketAllocationResolver =
        CurrentCycleBucketAllocationResolver()
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Suppress("LongMethod")
    operator fun invoke(): Flow<SpendingForecast?> {
        val userSettings = userSettingsStore.userSettings
        val effectiveInputs = observeEffectiveInputs(userSettings)
        val effectiveDate = effectiveInputs
            .map { inputs -> inputs.today }
            .distinctUntilChanged()
        val selectedBucketUuid = userSettings
            .map { settings ->
                settings.selectedBucketUuid ?: DEFAULT_SPENDING_BUCKET_UUID
            }
            .distinctUntilChanged()
        val buckets = budgetBucketDao.observeAllActive().map { entries ->
            entries.map { it.bucketToDomainModel() }
        }
        val selectedBucket = combine(selectedBucketUuid, buckets) { bucketUuid, bucketEntries ->
            resolveSelectedOpenBucket(bucketUuid, bucketEntries)
        }.distinctUntilChanged()
        val resolvedSelectedBucketUuid = selectedBucket
            .map { bucket -> bucket?.bucketUuid ?: DEFAULT_SPENDING_BUCKET_UUID }
            .distinctUntilChanged()
        val history = resolvedSelectedBucketUuid.flatMapLatest { bucketUuid ->
            bucketMonthlyHistoryDao.observeForBucket(bucketUuid).map { entries ->
                entries.map { it.bucketHistoryToDomainModel() }.map { entry ->
                    MonthlyHistory(
                        cycleStartDate = entry.cycleStartDate,
                        budgetAmountCents = entry.budgetAmountCents,
                        totalSpentCents = entry.totalSpentCents,
                        surplusCents = entry.surplusCents,
                        cycleEndDate = entry.cycleEndDate,
                        endTimestamp = entry.endTimestamp
                    )
                }
            }
        }
        val budgetPolicies = budgetPolicyDao.observeActivePolicies().map { entries ->
            entries.map { it.policyToDomainModel() }
        }
        val bucketPolicies = bucketAllocationPolicyDao?.observeActivePolicies()?.map { entries ->
            entries.map { it.toDomainModel() }
        } ?: flowOf(emptyList())
        val portfolioCurrentPolicy = observePortfolioCurrentPolicy(effectiveInputs, budgetPolicies)
        val currentBaselines = observeCurrentBaselines(currentPolicy = portfolioCurrentPolicy)
        val currentTransfers = observeCurrentTransfers(currentPolicy = portfolioCurrentPolicy)
        val currentPolicy = observeCurrentPolicy(
            effectiveInputs = effectiveInputs,
            selectedBucket = selectedBucket,
            budgetPolicies = budgetPolicies,
            bucketPolicies = bucketPolicies,
            baselines = currentBaselines,
            transfers = currentTransfers
        )
        val recentExpenses = observeRecentExpenses(effectiveDate)
        val currentAdjustments = flowOf(emptyList<BucketAllocationAdjustment>())

        return combine(
            combine(
                selectedBucket,
                history,
                recentExpenses,
                currentPolicy,
                currentAdjustments
            ) { bucket, historyEntries, recentExpenseEntries, resolvedCurrentPolicy, adjustments ->
                ForecastComposedInputs(
                    bucket = bucket,
                    historyEntries = historyEntries,
                    recentExpenseEntries = recentExpenseEntries,
                    resolvedCurrentPolicy = resolvedCurrentPolicy,
                    adjustments = adjustments
                )
            },
            effectiveInputs
        ) { composedInputs, inputs ->
            buildSpendingForecast(
                inputs = inputs,
                bucketUuid = composedInputs.bucket?.bucketUuid ?: DEFAULT_SPENDING_BUCKET_UUID,
                historyEntries = composedInputs.historyEntries,
                recentExpenseEntries = composedInputs.recentExpenseEntries,
                currentPolicy = composedInputs.resolvedCurrentPolicy,
                adjustments = composedInputs.adjustments
            )
        }.distinctUntilChanged()
    }

    private fun observePortfolioCurrentPolicy(
        effectiveInputs: Flow<EffectiveForecastInputs>,
        budgetPolicies: Flow<List<BudgetPolicy>>
    ): Flow<ResolvedCyclePolicy> {
        return combine(effectiveInputs, budgetPolicies) { inputs, portfolioPolicies ->
            cycleScheduleResolver.resolvePolicyForDate(
                date = inputs.today,
                settings = inputs.settings,
                policies = portfolioPolicies
            )
        }.distinctUntilChanged()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeCurrentBaselines(
        currentPolicy: Flow<ResolvedCyclePolicy>
    ): Flow<List<BucketCycleBaseline>> {
        val baselineDao = bucketCycleBaselineDao ?: return flowOf(emptyList())
        return currentPolicy
            .map { it.cycleStart.toString() }
            .distinctUntilChanged()
            .flatMapLatest { cycleStart ->
                baselineDao.observeActiveForCycle(cycleStart).map { entries ->
                    entries.map { it.bucketBaselineToDomainModel() }
                }
            }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeCurrentTransfers(
        currentPolicy: Flow<ResolvedCyclePolicy>
    ): Flow<List<net.loeu.wallybudget.domain.model.BucketTransfer>> {
        val transferDao = bucketTransferDao ?: return flowOf(emptyList())
        return currentPolicy
            .map { it.cycleStart.toString() }
            .distinctUntilChanged()
            .flatMapLatest { cycleStart ->
                transferDao.observeForCycle(cycleStart).map { entries ->
                    entries.map { it.toDomainModel() }
                }
            }
    }

    private fun observeCurrentPolicy(
        effectiveInputs: Flow<EffectiveForecastInputs>,
        selectedBucket: Flow<net.loeu.wallybudget.domain.model.BudgetBucket?>,
        budgetPolicies: Flow<List<BudgetPolicy>>,
        bucketPolicies: Flow<List<BucketAllocationPolicy>>,
        baselines: Flow<List<BucketCycleBaseline>>,
        transfers: Flow<List<net.loeu.wallybudget.domain.model.BucketTransfer>>
    ): Flow<ResolvedCyclePolicy> {
        val currentBucketState = combine(budgetPolicies, bucketPolicies, baselines, transfers) {
                portfolioPolicies,
                currentBucketPolicies,
                currentBaselines,
                currentTransfers ->
            CurrentPolicyBucketState(
                portfolioPolicies = portfolioPolicies,
                currentBucketPolicies = currentBucketPolicies,
                currentBaselines = currentBaselines,
                currentTransfers = currentTransfers
            )
        }
        return combine(effectiveInputs, selectedBucket, currentBucketState) { inputs, bucket, state ->
            val portfolioPolicy = cycleScheduleResolver.resolvePolicyForDate(
                date = inputs.today,
                settings = inputs.settings,
                policies = state.portfolioPolicies
            )
            val selectedBucketValue = bucket ?: return@combine portfolioPolicy.copy(budgetAmountCents = 0L)
            val allocation = currentCycleBucketAllocationResolver.resolve(
                bucketUuid = selectedBucketValue.bucketUuid,
                cycleStart = portfolioPolicy.cycleStart,
                fallbackAllocationCents = selectedBucketValue.defaultAllocatedAmountCents,
                baselines = state.currentBaselines,
                legacyPolicies = state.currentBucketPolicies,
                transfers = state.currentTransfers
            ).effectiveAllocationCents
            portfolioPolicy.copy(budgetAmountCents = allocation)
        }.distinctUntilChanged()
    }

    private fun observeEffectiveInputs(
        userSettings: Flow<UserSettings>
    ): Flow<EffectiveForecastInputs> {
        return combine(
            userSettings,
            currentDateProvider.observeCurrentDate()
        ) { settings, observedDate ->
            EffectiveForecastInputs(
                settings = settings,
                today = effectiveCurrentDate(settings, observedDate)
            )
        }.distinctUntilChanged()
    }

    private fun buildSpendingForecast(
        inputs: EffectiveForecastInputs,
        bucketUuid: String,
        historyEntries: List<MonthlyHistory>,
        recentExpenseEntries: List<Expense>,
        currentPolicy: ResolvedCyclePolicy,
        adjustments: List<BucketAllocationAdjustment>
    ): SpendingForecast? {
        val bucketExpenses = recentExpenseEntries.filter { it.bucketUuid == bucketUuid }
        val currentCycleExpenses = bucketExpenses.filterByRange(
            start = currentPolicy.cycleStart,
            endExclusive = minOf(inputs.today.plusDays(1), currentPolicy.cycleEndExclusive)
        )
        val todayExpenses = currentCycleExpenses.filterByRange(
            start = inputs.today,
            endExclusive = inputs.today.plusDays(1)
        )
        val totalSpentThisCycleCents = currentCycleExpenses.sumOf { it.amountCents }
        val spentTodayCents = todayExpenses.sumOf { it.amountCents }
        val resolvedBucketAllocation = bucketAllocationResolver.resolveBucketAllocation(
            cycleStart = currentPolicy.cycleStart,
            cycleEndExclusive = currentPolicy.cycleEndExclusive,
            baseAllocatedAmountCents = currentPolicy.budgetAmountCents,
            adjustments = adjustments,
            today = inputs.today
        )
        val budgetState = budgetCalculationService.calculateBudgetStateForResolvedCycle(
            now = inputs.today,
            cycleStart = currentPolicy.cycleStart,
            cycleEndExclusive = currentPolicy.cycleEndExclusive,
            cycleBudgetAmountCents = resolvedBucketAllocation.effectiveCycleAllocationCents,
            plannedTodayBudgetCents = resolvedBucketAllocation.plannedTodayAllocationCents,
            allocatedBeforeTodayCents = resolvedBucketAllocation.allocatedBeforeDateCents,
            totalSpentThisCycleCents = totalSpentThisCycleCents,
            spentTodayCents = spentTodayCents,
            paydayDate = currentPolicy.paydayDayOfMonth
        )
        return budgetCalculationService.calculateSpendingForecast(
            budgetState = budgetState,
            now = inputs.today,
            monthlyHistory = historyEntries,
            recentExpenses = bucketExpenses
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeRecentExpenses(effectiveDate: Flow<LocalDate>): Flow<List<Expense>> {
        return effectiveDate
            .map { now -> now.minusDays(ForecastConfig.HISTORICAL_DAYS_LOOKBACK.toLong()).toString() }
            .distinctUntilChanged()
            .flatMapLatest(expenseDao::observeSince)
            .map { expenses -> expenses.map { it.toDomainModel() } }
    }
}
