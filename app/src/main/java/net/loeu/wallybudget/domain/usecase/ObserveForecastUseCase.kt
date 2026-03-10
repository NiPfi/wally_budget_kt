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
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.usecase.internal.buildBudgetState
import net.loeu.wallybudget.domain.usecase.internal.effectiveCurrentDate

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
        val effectiveDate = combine(
            userSettings,
            currentDateProvider.observeCurrentDate()
        ) { settings, observedDate ->
            effectiveCurrentDate(settings, observedDate)
        }.distinctUntilChanged()
        val allExpenses = expenseDao.observeAllOrderedDesc().map { expenses ->
            expenses.map { it.toDomainModel() }
        }
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
            userSettings,
            effectiveDate,
            allExpenses,
            history,
            recentExpenses
        ) { settings, today, expenses, historyEntries, recentExpenseEntries ->
            val currentCycleRange = budgetCalculationService.getCurrentCycleProgressRange(
                now = today,
                paydayDate = settings.paydayDate
            )
            val totalSpentThisCycleCents = expenseDao.totalSpentInRange(
                currentCycleRange.start.toString(),
                currentCycleRange.endExclusive.toString()
            ) ?: 0L
            val spentTodayCents = expenseDao.totalSpentInRange(
                today.toString(),
                today.plusDays(1).toString()
            ) ?: 0L
            val budgetState = buildBudgetState(
                settings = settings,
                today = today,
                history = historyEntries,
                totalSpentThisCycleCents = totalSpentThisCycleCents,
                spentTodayCents = spentTodayCents,
                budgetCalculationService = budgetCalculationService
            )
            budgetCalculationService.calculateSpendingForecast(
                budgetState = budgetState,
                now = today,
                monthlyHistory = historyEntries.filter { it.totalSpentCents > 0L },
                recentExpenses = recentExpenseEntries
            )
        }.distinctUntilChanged()
    }
}
