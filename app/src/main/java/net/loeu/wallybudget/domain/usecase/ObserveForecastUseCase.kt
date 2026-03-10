package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.dao.MonthlyHistoryDao
import net.loeu.wallybudget.data.local.entity.toDomainModel
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.data.time.CurrentDateProvider
import net.loeu.wallybudget.domain.config.ForecastConfig
import net.loeu.wallybudget.domain.model.SpendingForecast
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.usecase.internal.buildBudgetState
import net.loeu.wallybudget.domain.usecase.internal.effectiveCurrentDate
import net.loeu.wallybudget.domain.usecase.internal.filterByRange
import java.time.LocalDate

private data class EffectiveForecastInputs(
    val settings: UserSettings,
    val today: LocalDate
)

class ObserveForecastUseCase(
    private val expenseDao: ExpenseDao,
    private val monthlyHistoryDao: MonthlyHistoryDao,
    private val userSettingsStore: UserSettingsStore,
    private val currentDateProvider: CurrentDateProvider,
    private val budgetCalculationService: BudgetCalculationService
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<SpendingForecast> {
        val userSettings = userSettingsStore.userSettings
        val effectiveInputs = combine(
            userSettings,
            currentDateProvider.observeCurrentDate()
        ) { settings, observedDate ->
            EffectiveForecastInputs(
                settings = settings,
                today = effectiveCurrentDate(settings, observedDate)
            )
        }.distinctUntilChanged()
        val effectiveDate = effectiveInputs
            .map { inputs -> inputs.today }
            .distinctUntilChanged()
        val history = monthlyHistoryDao.observeAll().map { entries ->
            entries.map { it.toDomainModel() }
        }
        val recentExpenses = effectiveDate
            .map { now ->
                now.minusDays(ForecastConfig.HISTORICAL_DAYS_LOOKBACK.toLong()).toString()
            }
            .distinctUntilChanged()
            .flatMapLatest { lookbackDate ->
                expenseDao.observeSince(lookbackDate)
            }
            .map { expenses ->
                expenses.map { it.toDomainModel() }
            }

        return combine(
            effectiveInputs,
            history,
            recentExpenses
        ) { inputs, historyEntries, recentExpenseEntries ->
            val currentCycleRange = budgetCalculationService.getCurrentCycleProgressRange(
                now = inputs.today,
                paydayDate = inputs.settings.paydayDate
            )
            val currentCycleExpenses = recentExpenseEntries.filterByRange(
                start = currentCycleRange.start,
                endExclusive = currentCycleRange.endExclusive
            )
            val todayExpenses = currentCycleExpenses.filterByRange(
                start = inputs.today,
                endExclusive = inputs.today.plusDays(1)
            )
            val totalSpentThisCycleCents = currentCycleExpenses.sumOf { it.amountCents }
            val spentTodayCents = todayExpenses.sumOf { it.amountCents }
            val budgetState = buildBudgetState(
                settings = inputs.settings,
                today = inputs.today,
                history = historyEntries,
                totalSpentThisCycleCents = totalSpentThisCycleCents,
                spentTodayCents = spentTodayCents,
                budgetCalculationService = budgetCalculationService
            )
            budgetCalculationService.calculateSpendingForecast(
                budgetState = budgetState,
                now = inputs.today,
                monthlyHistory = historyEntries,
                recentExpenses = recentExpenseEntries
            )
        }.distinctUntilChanged()
    }
}
