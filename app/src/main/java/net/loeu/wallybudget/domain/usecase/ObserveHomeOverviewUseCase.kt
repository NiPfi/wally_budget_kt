package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import net.loeu.wallybudget.data.local.dao.BudgetAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.CycleOverviewDao
import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.dao.MonthlyHistoryDao
import net.loeu.wallybudget.data.local.entity.toDomainModel as adjustmentToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel as policyToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel
import net.loeu.wallybudget.domain.model.BudgetAdjustment
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.data.time.CurrentDateProvider
import net.loeu.wallybudget.domain.model.BudgetPolicy
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.HomeOverviewState
import net.loeu.wallybudget.domain.model.MonthlyHistory
import net.loeu.wallybudget.domain.model.PendingCycleCloseoutState
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.model.groupByDate
import net.loeu.wallybudget.domain.service.BudgetAdjustmentResolver
import net.loeu.wallybudget.domain.service.CycleDateRange
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import net.loeu.wallybudget.domain.service.ResolvedCyclePolicy
import net.loeu.wallybudget.domain.usecase.internal.buildBudgetState
import net.loeu.wallybudget.domain.usecase.internal.buildContinuousDaySections
import net.loeu.wallybudget.domain.usecase.internal.buildTimelineLockState
import net.loeu.wallybudget.domain.usecase.internal.buildTrendSummary
import net.loeu.wallybudget.domain.usecase.internal.effectiveCurrentDate
import net.loeu.wallybudget.domain.usecase.internal.filterByRange
import net.loeu.wallybudget.domain.usecase.internal.lastResetDateOrNull
import net.loeu.wallybudget.domain.usecase.internal.pendingCycleRangeOrNull
import net.loeu.wallybudget.domain.usecase.internal.toDayTotalsMap
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private data class CurrentHomeOverviewInputs(
    val settings: UserSettings,
    val today: LocalDate,
    val currentExpenses: List<Expense>,
    val historyEntries: List<MonthlyHistory>,
    val currentDayTotals: Map<LocalDate, Long>,
    val budgetPolicies: List<BudgetPolicy>,
    val currentPolicy: ResolvedCyclePolicy,
    val currentAdjustments: List<BudgetAdjustment>
)

private data class EffectiveHomeDateInputs(
    val settings: UserSettings,
    val today: LocalDate
)

class ObserveHomeOverviewUseCase(
    private val expenseDao: ExpenseDao,
    private val monthlyHistoryDao: MonthlyHistoryDao,
    private val cycleOverviewDao: CycleOverviewDao,
    private val budgetAdjustmentDao: BudgetAdjustmentDao,
    private val budgetPolicyDao: BudgetPolicyDao,
    private val userSettingsStore: UserSettingsStore,
    private val currentDateProvider: CurrentDateProvider,
    private val budgetCalculationService: BudgetCalculationService,
    private val cycleScheduleResolver: CycleScheduleResolver,
    private val budgetAdjustmentResolver: BudgetAdjustmentResolver
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<HomeOverviewState> {
        val userSettings = userSettingsStore.userSettings
        val effectiveInputs = observeEffectiveInputs(userSettings)
        val currentCycleRange = observeCurrentCycleRange(effectiveInputs)
        val pendingCycle = userSettings
            .map { settings -> settings.pendingCycleRangeOrNull() }
            .distinctUntilChanged()
        val pendingCycleExpenses = observePendingCycleExpenses(pendingCycle)
        val pendingCycleDayTotals = observePendingCycleDayTotals(pendingCycle)
        val pendingCycleAdjustments = observePendingCycleAdjustments(pendingCycle)
        val latestExpenseDate = observeLatestExpenseDate()
        val history = monthlyHistoryDao.observeAll().map { entries ->
            entries.map { it.toDomainModel() }
        }
        val budgetPolicies = budgetPolicyDao.observeActivePolicies().map { entries ->
            entries.map { it.policyToDomainModel() }
        }
        val currentPolicy = combine(
            effectiveInputs,
            budgetPolicies
        ) { inputs, policies ->
            cycleScheduleResolver.resolvePolicyForDate(
                date = inputs.today,
                settings = inputs.settings,
                policies = policies
            )
        }.distinctUntilChanged()
        val currentAdjustments = currentPolicy
            .map { it.cycleStart.toString() }
            .distinctUntilChanged()
            .flatMapLatest { cycleStart ->
                budgetAdjustmentDao.observeActiveForCycle(cycleStart)
                    .map { entries -> entries.map { it.adjustmentToDomainModel() } }
            }
        val currentInputs = observeCurrentInputs(
            userSettings = userSettings,
            effectiveInputs = effectiveInputs,
            history = history,
            currentCycleRange = currentCycleRange,
            budgetPolicies = budgetPolicies,
            currentPolicy = currentPolicy,
            currentAdjustments = currentAdjustments
        )

        return combine(
            currentInputs,
            pendingCycleExpenses,
            pendingCycleDayTotals,
            pendingCycleAdjustments,
            latestExpenseDate
        ) { currentInputs, pendingExpenses, pendingDayTotals, pendingAdjustments, latestRecordedExpenseDate ->
            buildHomeOverviewState(
                currentInputs = currentInputs,
                pendingExpenses = pendingExpenses,
                pendingDayTotals = pendingDayTotals,
                pendingAdjustments = pendingAdjustments,
                latestRecordedExpenseDate = latestRecordedExpenseDate
            )
        }.distinctUntilChanged()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeCurrentInputs(
        userSettings: Flow<UserSettings>,
        effectiveInputs: Flow<EffectiveHomeDateInputs>,
        history: Flow<List<MonthlyHistory>>,
        currentCycleRange: Flow<CycleDateRange>,
        budgetPolicies: Flow<List<BudgetPolicy>>,
        currentPolicy: Flow<ResolvedCyclePolicy>,
        currentAdjustments: Flow<List<BudgetAdjustment>>
    ): Flow<CurrentHomeOverviewInputs> {
        val baseInputs = combine(
            userSettings,
            effectiveInputs.map { it.today }.distinctUntilChanged(),
            observeExpensesInRange(currentCycleRange),
            history,
            observeDayTotalsInRange(currentCycleRange)
        ) { settings, today, currentExpenses, historyEntries, currentDayTotals ->
            CurrentHomeOverviewInputs(
                settings = settings,
                today = today,
                currentExpenses = currentExpenses,
                historyEntries = historyEntries,
                currentDayTotals = currentDayTotals,
                budgetPolicies = emptyList(),
                currentPolicy = ResolvedCyclePolicy(
                    cycleStart = today,
                    cycleEndExclusive = today.plusDays(1),
                    budgetAmountCents = settings.monthlyBudgetCents,
                    paydayDayOfMonth = settings.paydayDate
                ),
                currentAdjustments = emptyList()
            )
        }
        val policyInputs = combine(
            budgetPolicies,
            currentPolicy,
            currentAdjustments
        ) { policies, resolvedPolicy, adjustments ->
            Triple(policies, resolvedPolicy, adjustments)
        }
        return combine(baseInputs, policyInputs) { inputs, policyInputsValue ->
            inputs.copy(
                budgetPolicies = policyInputsValue.first,
                currentPolicy = policyInputsValue.second,
                currentAdjustments = policyInputsValue.third
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
        effectiveInputs: Flow<EffectiveHomeDateInputs>
    ): Flow<CycleDateRange> {
        return combine(
            effectiveInputs,
            budgetPolicyDao.observeActivePolicies().map { entries -> entries.map { it.policyToDomainModel() } }
        ) { inputs, policies ->
            val currentPolicy = cycleScheduleResolver.resolvePolicyForDate(
                date = inputs.today,
                settings = inputs.settings,
                policies = policies
            )
            CycleDateRange(
                start = currentPolicy.cycleStart,
                endExclusive = minOf(inputs.today.plusDays(1), currentPolicy.cycleEndExclusive)
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
    private fun observeDayTotalsInRange(
        range: Flow<CycleDateRange>
    ): Flow<Map<LocalDate, Long>> {
        return range.flatMapLatest { cycleRange ->
            cycleOverviewDao.observeDayTotalsInRange(
                startDateInclusive = cycleRange.start.toString(),
                endDateExclusive = cycleRange.endExclusive.toString()
            ).map { rows -> rows.toDayTotalsMap() }
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

    private fun observeLatestExpenseDate(): Flow<LocalDate?> {
        return expenseDao.observeLatestExpenseDate().map { date ->
            date?.let(LocalDate::parse)
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
        val budgetState = buildBudgetState(
            settings = currentInputs.settings,
            today = currentInputs.today,
            history = currentInputs.historyEntries,
            totalSpentThisCycleCents = totalSpentThisCycleCents,
            spentTodayCents = spentTodayCents,
            cyclePolicy = currentInputs.currentPolicy,
            adjustments = currentInputs.currentAdjustments,
            budgetAdjustmentResolver = budgetAdjustmentResolver,
            budgetCalculationService = budgetCalculationService
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
                budgetPolicies = currentInputs.budgetPolicies,
                resolvedPendingPolicy = currentInputs.budgetPolicies
                    .firstOrNull { it.deletedAtEpochMs == null && it.cycleStartDate == currentInputs.settings.pendingCycleStartDate }
                    ?.let {
                        ResolvedCyclePolicy(
                            cycleStart = it.cycleStart(),
                            cycleEndExclusive = it.cycleEndExclusive(),
                            budgetAmountCents = it.budgetAmountCents,
                            paydayDayOfMonth = it.paydayDayOfMonth
                        )
                    }
            ),
            timelineLockState = timelineLockState
        )
    }

    private fun buildPendingCycleCloseoutState(
        settings: UserSettings,
        expenses: List<Expense>,
        dayTotals: Map<LocalDate, Long>,
        adjustments: List<BudgetAdjustment>,
        budgetPolicies: List<BudgetPolicy>,
        resolvedPendingPolicy: ResolvedCyclePolicy?
    ): PendingCycleCloseoutState? {
        val pendingCycle = settings.pendingCycleRangeOrNull() ?: return null
        val cycleExpenses = expenses.filterByRange(
            start = pendingCycle.start,
            endExclusive = pendingCycle.endExclusive
        )
        val dayCount = ChronoUnit.DAYS.between(pendingCycle.start, pendingCycle.endExclusive)
            .toInt()
            .coerceAtLeast(1)
        val baseBudgetAmount = resolvedPendingPolicy?.budgetAmountCents
            ?: budgetPolicies
                .firstOrNull { it.deletedAtEpochMs == null && it.cycleStartDate == pendingCycle.start.toString() }
                ?.budgetAmountCents
            ?: settings.monthlyBudgetCents
        val cycleBudgetAmount = budgetAdjustmentResolver.resolveEffectiveCycleBudgetAmount(
            cycleStart = pendingCycle.start,
            cycleEndExclusive = pendingCycle.endExclusive,
            baseMonthlyBudgetCents = baseBudgetAmount,
            adjustments = adjustments
        )
        val baseDailyBudget = cycleBudgetAmount / dayCount
        val daySections = buildContinuousDaySections(
            start = pendingCycle.start,
            endInclusive = pendingCycle.endExclusive.minusDays(1),
            expensesByDate = cycleExpenses.groupByDate(),
            dayTotals = dayTotals,
            remainingBudgetForDay = { totalSpent -> baseDailyBudget - totalSpent },
            isEditable = true,
            today = null
        )
        val totalSpent = cycleExpenses.sumOf { it.amountCents }
        val biggestExpense = cycleExpenses.maxByOrNull { it.amountCents }
        val highestSpendDay = daySections.maxByOrNull { it.totalSpentCents }?.date
        val topCategory = cycleExpenses
            .filter { it.icon != null }
            .groupBy { it.icon }
            .maxByOrNull { (_, categoryExpenses) -> categoryExpenses.sumOf { it.amountCents } }
            ?.key

        return PendingCycleCloseoutState(
            cycleStartDate = pendingCycle.start,
            cycleEndDateExclusive = pendingCycle.endExclusive,
            budgetAmountCents = cycleBudgetAmount,
            totalSpentCents = totalSpent,
            surplusCents = cycleBudgetAmount - totalSpent,
            averageDailySpendCents = totalSpent / dayCount,
            biggestExpense = biggestExpense,
            highestSpendDay = highestSpendDay,
            topCategory = topCategory,
            trendSummary = buildTrendSummary(daySections),
            daySections = daySections
        )
    }
}
