package net.loeu.wallybudget.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import net.loeu.wallybudget.data.local.BudgetDatabase
import net.loeu.wallybudget.data.local.UserPreferencesManager
import net.loeu.wallybudget.data.repository.BudgetRepository
import net.loeu.wallybudget.data.time.SystemCurrentDateProvider

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
                BudgetDatabase.MIGRATION_5_6
            )
            .build()
    }

    private val userPreferencesManager by lazy {
        UserPreferencesManager(context.applicationContext)
    }

    private val currentDateProvider by lazy {
        SystemCurrentDateProvider()
    }

    private val repository by lazy {
        BudgetRepository(
            expenseDao = database.expenseDao(),
            monthlyHistoryDao = database.monthlyHistoryDao(),
            userPreferencesManager = userPreferencesManager,
            currentDateProvider = currentDateProvider
        )
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BudgetViewModel::class.java)) {
            return BudgetViewModel(repository, currentDateProvider) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

