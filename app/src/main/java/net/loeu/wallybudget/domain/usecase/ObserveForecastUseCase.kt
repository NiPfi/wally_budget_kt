package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import net.loeu.wallybudget.data.local.dao.BudgetAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.dao.MonthlyHistoryDao
import net.loeu.wallybudget.data.local.entity.toDomainModel as adjustmentToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel as policyToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.data.time.CurrentDateProvider
import net.loeu.wallybudget.domain.config.ForecastConfig
import net.loeu.wallybudget.domain.model.BudgetAdjustment
import net.loeu.wallybudget.domain.model.BudgetPolicy
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.MonthlyHistory
import net.loeu.wallybudget.domain.model.SpendingForecast
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BudgetAdjustmentResolver
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import net.loeu.wallybudget.domain.service.ResolvedCyclePolicy
import net.loeu.wallybudget.domain.usecase.internal.buildBudgetState
import net.loeu.wallybudget.domain.usecase.internal.effectiveCurrentDate
import net.loeu.wallybudget.domain.usecase.internal.filterByRange
import java.time.LocalDate

private data class EffectiveForecastInputs(
    val settings: UserSettings,
    val today: LocalDate
)

class ObserveForecastUseCase(
    private val budgetPolicyDao: BudgetPolicyDao,
    private val budgetAdjustmentDao: BudgetAdjustmentDao,
    private val expenseDao: ExpenseDao,
    private val monthlyHistoryDao: MonthlyHistoryDao,
    private val userSettingsStore: UserSettingsStore,
    private val currentDateProvider: CurrentDateProvider,
    private val budgetCalculationService: BudgetCalculationService,
    private val cycleScheduleResolver: CycleScheduleResolver,
    private val budgetAdjustmentResolver: BudgetAdjustmentResolver
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<SpendingForecast> {
        val userSettings = userSettingsStore.userSettings
        val effectiveInputs = observeEffectiveInputs(userSettings)
        val effectiveDate = effectiveInputs
            .map { inputs -> inputs.today }
            .distinctUntilChanged()
        val history = monthlyHistoryDao.observeAll().map { entries ->
            entries.map { it.toDomainModel() }
        }
        val budgetPolicies = budgetPolicyDao.observeActivePolicies().map { entries ->
            entries.map { it.policyToDomainModel() }
        }
        val currentPolicy = observeCurrentPolicy(effectiveInputs, budgetPolicies)
        val currentAdjustments = currentPolicy
            .map { it.cycleStart.toString() }
            .distinctUntilChanged()
            .flatMapLatest { cycleStart ->
                budgetAdjustmentDao.observeActiveForCycle(cycleStart)
                    .map { entries -> entries.map { it.adjustmentToDomainModel() } }
            }
        val recentExpenses = observeRecentExpenses(effectiveDate)

        return combine(
            effectiveInputs,
            history,
            recentExpenses,
            currentPolicy,
            currentAdjustments
        ) { inputs, historyEntries, recentExpenseEntries, resolvedCurrentPolicy, adjustments ->
            buildSpendingForecast(
                inputs = inputs,
                historyEntries = historyEntries,
                recentExpenseEntries = recentExpenseEntries,
                currentPolicy = resolvedCurrentPolicy,
                adjustments = adjustments
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
        budgetPolicies: Flow<List<BudgetPolicy>>
    ): Flow<ResolvedCyclePolicy> {
        return combine(effectiveInputs, budgetPolicies) { inputs, policies ->
            cycleScheduleResolver.resolvePolicyForDate(
                date = inputs.today,
                settings = inputs.settings,
                policies = policies
            )
        }.distinctUntilChanged()
    }

    private fun buildSpendingForecast(
        inputs: EffectiveForecastInputs,
        historyEntries: List<MonthlyHistory>,
        recentExpenseEntries: List<Expense>,
        currentPolicy: ResolvedCyclePolicy,
        adjustments: List<BudgetAdjustment>
    ): SpendingForecast {
        val currentCycleExpenses = recentExpenseEntries.filterByRange(
            start = currentPolicy.cycleStart,
            endExclusive = minOf(inputs.today.plusDays(1), currentPolicy.cycleEndExclusive)
        )
        val todayExpenses = currentCycleExpenses.filterByRange(
            start = inputs.today,
            endExclusive = inputs.today.plusDays(1)
        )
        val totalSpentThisCycleCents = currentCycleExpenses.sumOf { it.amountCents }
        val spentTodayCents = todayExpenses.sumOf { it.amountCents }
        val budgetState = buildBudgetState(
            today = inputs.today,
            history = historyEntries,
            totalSpentThisCycleCents = totalSpentThisCycleCents,
            spentTodayCents = spentTodayCents,
            cyclePolicy = currentPolicy,
            adjustments = adjustments,
            budgetAdjustmentResolver = budgetAdjustmentResolver,
            budgetCalculationService = budgetCalculationService
        )
        return budgetCalculationService.calculateSpendingForecast(
            budgetState = budgetState,
            now = inputs.today,
            monthlyHistory = historyEntries,
            recentExpenses = recentExpenseEntries
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
