package net.loeu.wallybudget.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import net.loeu.wallybudget.data.local.db.BudgetDatabase
import net.loeu.wallybudget.data.local.preferences.UserPreferencesManager
import net.loeu.wallybudget.data.time.SystemCurrentDateProvider
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.SpendingForecastCalculator
import net.loeu.wallybudget.domain.usecase.AddExpenseUseCase
import net.loeu.wallybudget.domain.usecase.CompleteOnboardingUseCase
import net.loeu.wallybudget.domain.usecase.ConcludePendingCycleUseCase
import net.loeu.wallybudget.domain.usecase.DeleteExpenseUseCase
import net.loeu.wallybudget.domain.usecase.ObserveForecastUseCase
import net.loeu.wallybudget.domain.usecase.ObserveHistoryUseCase
import net.loeu.wallybudget.domain.usecase.ObserveHomeOverviewUseCase
import net.loeu.wallybudget.domain.usecase.PerformMonthlyResetUseCase
import net.loeu.wallybudget.domain.usecase.SyncObservedDateUseCase
import net.loeu.wallybudget.domain.usecase.UpdateExpenseUseCase
import net.loeu.wallybudget.domain.usecase.UpdateMonthlyBudgetUseCase
import net.loeu.wallybudget.domain.usecase.UpdatePaydayDateUseCase

class BudgetViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    private val database by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            BudgetDatabase::class.java,
            "budget_database"
        )
            .addMigrations(
                BudgetDatabase.MIGRATION_1_2,
                BudgetDatabase.MIGRATION_2_3,
                BudgetDatabase.MIGRATION_3_4,
                BudgetDatabase.MIGRATION_4_5,
                BudgetDatabase.MIGRATION_5_6,
                BudgetDatabase.MIGRATION_6_7
            )
            .build()
    }

    private val userPreferencesManager by lazy {
        UserPreferencesManager(context.applicationContext)
    }

    private val currentDateProvider by lazy {
        SystemCurrentDateProvider(context.applicationContext)
    }

    private val forecastCalculator by lazy {
        SpendingForecastCalculator()
    }

    private val budgetCalculationService by lazy {
        BudgetCalculationService(forecastCalculator)
    }

    private val expenseDao by lazy { database.expenseDao() }
    private val monthlyHistoryDao by lazy { database.monthlyHistoryDao() }
    private val cycleOverviewDao by lazy { database.cycleOverviewDao() }

    private val observeHomeOverviewUseCase by lazy {
        ObserveHomeOverviewUseCase(
            expenseDao = expenseDao,
            monthlyHistoryDao = monthlyHistoryDao,
            cycleOverviewDao = cycleOverviewDao,
            userPreferencesManager = userPreferencesManager,
            currentDateProvider = currentDateProvider,
            budgetCalculationService = budgetCalculationService
        )
    }

    private val observeHistoryUseCase by lazy {
        ObserveHistoryUseCase(
            expenseDao = expenseDao,
            monthlyHistoryDao = monthlyHistoryDao,
            cycleOverviewDao = cycleOverviewDao,
            userPreferencesManager = userPreferencesManager,
            currentDateProvider = currentDateProvider,
            budgetCalculationService = budgetCalculationService
        )
    }

    private val observeForecastUseCase by lazy {
        ObserveForecastUseCase(
            expenseDao = expenseDao,
            monthlyHistoryDao = monthlyHistoryDao,
            userPreferencesManager = userPreferencesManager,
            currentDateProvider = currentDateProvider,
            budgetCalculationService = budgetCalculationService
        )
    }

    private val addExpenseUseCase by lazy { AddExpenseUseCase(expenseDao) }
    private val updateExpenseUseCase by lazy { UpdateExpenseUseCase(expenseDao) }
    private val deleteExpenseUseCase by lazy { DeleteExpenseUseCase(expenseDao) }
    private val updateMonthlyBudgetUseCase by lazy { UpdateMonthlyBudgetUseCase(userPreferencesManager) }
    private val updatePaydayDateUseCase by lazy { UpdatePaydayDateUseCase(userPreferencesManager) }
    private val completeOnboardingUseCase by lazy {
        CompleteOnboardingUseCase(
            database = database,
            monthlyHistoryDao = monthlyHistoryDao,
            userPreferencesManager = userPreferencesManager,
            currentDateProvider = currentDateProvider,
            budgetCalculationService = budgetCalculationService
        )
    }
    private val performMonthlyResetUseCase by lazy {
        PerformMonthlyResetUseCase(
            database = database,
            expenseDao = expenseDao,
            monthlyHistoryDao = monthlyHistoryDao,
            userPreferencesManager = userPreferencesManager,
            budgetCalculationService = budgetCalculationService
        )
    }
    private val concludePendingCycleUseCase by lazy {
        ConcludePendingCycleUseCase(
            database = database,
            expenseDao = expenseDao,
            monthlyHistoryDao = monthlyHistoryDao,
            userPreferencesManager = userPreferencesManager,
            budgetCalculationService = budgetCalculationService
        )
    }
    private val syncObservedDateUseCase by lazy { SyncObservedDateUseCase(userPreferencesManager) }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BudgetViewModel::class.java)) {
            return BudgetViewModel(
                userSettingsFlow = userPreferencesManager.userSettings,
                observeHomeOverviewUseCase = observeHomeOverviewUseCase,
                observeHistoryUseCase = observeHistoryUseCase,
                observeForecastUseCase = observeForecastUseCase,
                addExpenseUseCase = addExpenseUseCase,
                updateExpenseUseCase = updateExpenseUseCase,
                deleteExpenseUseCase = deleteExpenseUseCase,
                updateMonthlyBudgetUseCase = updateMonthlyBudgetUseCase,
                updatePaydayDateUseCase = updatePaydayDateUseCase,
                completeOnboardingUseCase = completeOnboardingUseCase,
                performMonthlyResetUseCase = performMonthlyResetUseCase,
                concludePendingCycleUseCase = concludePendingCycleUseCase,
                syncObservedDateUseCase = syncObservedDateUseCase,
                currentDateProvider = currentDateProvider
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
