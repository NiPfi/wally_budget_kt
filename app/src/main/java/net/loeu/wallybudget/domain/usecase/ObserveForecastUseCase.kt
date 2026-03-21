package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationPolicyDao
import net.loeu.wallybudget.data.local.dao.BucketMonthlyHistoryDao
import net.loeu.wallybudget.data.local.dao.BudgetBucketDao
import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.entity.toDomainModel as bucketAdjustmentToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel as bucketHistoryToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel as bucketPolicyToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel as bucketToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel as policyToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.data.time.CurrentDateProvider
import net.loeu.wallybudget.domain.config.ForecastConfig
import net.loeu.wallybudget.domain.model.BucketAllocationAdjustment
import net.loeu.wallybudget.domain.model.BucketAllocationPolicy
import net.loeu.wallybudget.domain.model.BucketTrackingMode
import net.loeu.wallybudget.domain.model.BudgetPolicy
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.MonthlyHistory
import net.loeu.wallybudget.domain.model.SpendingForecast
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BucketAllocationResolver
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
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

class ObserveForecastUseCase(
    private val budgetPolicyDao: BudgetPolicyDao,
    private val budgetBucketDao: BudgetBucketDao,
    private val bucketAllocationPolicyDao: BucketAllocationPolicyDao,
    private val bucketAllocationAdjustmentDao: BucketAllocationAdjustmentDao,
    private val bucketMonthlyHistoryDao: BucketMonthlyHistoryDao,
    private val expenseDao: ExpenseDao,
    private val userSettingsStore: UserSettingsStore,
    private val currentDateProvider: CurrentDateProvider,
    private val budgetCalculationService: BudgetCalculationService,
    private val cycleScheduleResolver: CycleScheduleResolver,
    private val bucketAllocationResolver: BucketAllocationResolver
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
        val bucketPolicies = bucketAllocationPolicyDao.observeActivePolicies().map { entries ->
            entries.map { it.bucketPolicyToDomainModel() }
        }
        val currentPolicy = observeCurrentPolicy(effectiveInputs, selectedBucket, budgetPolicies, bucketPolicies)
        val currentAdjustments = combine(resolvedSelectedBucketUuid, currentPolicy) { bucketUuid, policy ->
            bucketUuid to policy.cycleStart.toString()
        }
            .distinctUntilChanged()
            .flatMapLatest { (bucketUuid, cycleStart) ->
                bucketAllocationAdjustmentDao.observeActiveForCycle(bucketUuid, cycleStart)
                    .map { entries -> entries.map { it.bucketAdjustmentToDomainModel() } }
            }
        val recentExpenses = observeRecentExpenses(effectiveDate)

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
                bucketTrackingMode = composedInputs.bucket?.trackingMode,
                bucketUuid = composedInputs.bucket?.bucketUuid ?: DEFAULT_SPENDING_BUCKET_UUID,
                historyEntries = composedInputs.historyEntries,
                recentExpenseEntries = composedInputs.recentExpenseEntries,
                currentPolicy = composedInputs.resolvedCurrentPolicy,
                adjustments = composedInputs.adjustments
            )
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

    private fun observeCurrentPolicy(
        effectiveInputs: Flow<EffectiveForecastInputs>,
        selectedBucket: Flow<net.loeu.wallybudget.domain.model.BudgetBucket?>,
        budgetPolicies: Flow<List<BudgetPolicy>>,
        bucketPolicies: Flow<List<BucketAllocationPolicy>>
    ): Flow<ResolvedCyclePolicy> {
        return combine(
            effectiveInputs,
            selectedBucket,
            budgetPolicies,
            bucketPolicies
        ) { inputs, bucket, portfolioPolicies, policies ->
            val portfolioPolicy = cycleScheduleResolver.resolvePolicyForDate(
                date = inputs.today,
                settings = inputs.settings,
                policies = portfolioPolicies
            )
            val selectedBucketValue = bucket ?: return@combine portfolioPolicy.copy(budgetAmountCents = 0L)
            val persistedPolicy = policies
                .filter { it.deletedAtEpochMs == null }
                .firstOrNull { policy ->
                    policy.bucketUuid == selectedBucketValue.bucketUuid &&
                        policy.cycleStart() == portfolioPolicy.cycleStart &&
                        policy.cycleEndExclusive() == portfolioPolicy.cycleEndExclusive
                }
            if (persistedPolicy != null) {
                ResolvedCyclePolicy(
                    cycleStart = persistedPolicy.cycleStart(),
                    cycleEndExclusive = persistedPolicy.cycleEndExclusive(),
                    budgetAmountCents = persistedPolicy.allocatedAmountCents,
                    paydayDayOfMonth = inputs.settings.paydayDate
                )
            } else {
                portfolioPolicy.copy(budgetAmountCents = selectedBucketValue.defaultAllocatedAmountCents)
            }
        }.distinctUntilChanged()
    }

    private fun buildSpendingForecast(
        inputs: EffectiveForecastInputs,
        bucketTrackingMode: BucketTrackingMode?,
        bucketUuid: String,
        historyEntries: List<MonthlyHistory>,
        recentExpenseEntries: List<Expense>,
        currentPolicy: ResolvedCyclePolicy,
        adjustments: List<BucketAllocationAdjustment>
    ): SpendingForecast? {
        if (bucketTrackingMode != BucketTrackingMode.DAILY_TARGET) {
            return null
        }
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
        val cumulativeSavingsCents = historyEntries
            .filter { !it.getCycleEnd().isAfter(currentPolicy.cycleStart) }
            .sumOf { it.surplusCents }
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
            cumulativeSavingsCents = cumulativeSavingsCents,
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
