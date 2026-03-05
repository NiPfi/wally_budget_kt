package net.loeu.wallybudget.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.loeu.wallybudget.data.model.BudgetState
import net.loeu.wallybudget.data.model.Expense
import net.loeu.wallybudget.data.model.ExpenseCategory
import net.loeu.wallybudget.data.model.MonthlyHistory
import net.loeu.wallybudget.data.model.SpendingForecast
import net.loeu.wallybudget.data.model.UserSettings
import net.loeu.wallybudget.data.repository.BudgetRepository
import net.loeu.wallybudget.data.time.CurrentDateProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class BudgetViewModel(
    private val repository: BudgetRepository,
    private val currentDateProvider: CurrentDateProvider
) : ViewModel() {

    val userSettingsFlow: Flow<UserSettings> = repository.userSettings

    // User settings
    val userSettings: StateFlow<UserSettings> = userSettingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserSettings()
        )

    // Budget state
    val budgetState: StateFlow<BudgetState> = repository.getBudgetState()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BudgetState(
                monthlyBudgetCents = 0L,
                totalSpentThisCycleCents = 0L,
                dailyBudgetCents = 0L,
                spentTodayCents = 0L,
                remainingTodayCents = 0L,
                daysRemainingInCycle = 0,
                cumulativeSavingsCents = 0L,
                paydayDate = 1,
                cycleStartDate = LocalDate.now()
            )
        )

    // Today's expenses
    val todayExpenses: StateFlow<List<Expense>> = repository.getTodayExpenses()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Previous expenses in current cycle (before today)
    val previousCycleExpenses: StateFlow<List<Expense>> = repository.getPreviousCycleExpenses()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Monthly history
    val monthlyHistory: StateFlow<List<MonthlyHistory>> = repository.getMonthlyHistory()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val spendingForecast: StateFlow<SpendingForecast> = repository.getSpendingForecast()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SpendingForecast()
        )

    // UI state
    private val _isAddExpenseSheetVisible = MutableStateFlow(false)
    val isAddExpenseSheetVisible = _isAddExpenseSheetVisible.asStateFlow()

    init {
        // Check for monthly reset on initialization and local date changes
        viewModelScope.launch {
            combine(userSettingsFlow, currentDateProvider.observeCurrentDate()) { settings, _ -> settings }
                .collect { settings ->
                repository.checkAndPerformMonthlyReset(settings)
            }
        }
    }

    /**
     * Add a new expense
     */
    fun addExpense(amountCents: Long, description: String, icon: ExpenseCategory? = null, date: LocalDate = LocalDate.now()) {
        viewModelScope.launch {
            val timestamp = if (date == LocalDate.now()) {
                Instant.now().toEpochMilli()
            } else {
                date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
            val expense = Expense(
                amountCents = amountCents,
                description = description,
                icon = icon,
                timestamp = timestamp
            )
            repository.addExpense(expense)
            _isAddExpenseSheetVisible.value = false
        }
    }

    /**
     * Update an expense
     */
    fun updateExpense(expense: Expense) {
        viewModelScope.launch {
            repository.updateExpense(expense)
        }
    }

    /**
     * Delete an expense
     */
    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
        }
    }

    fun restoreDeletedExpense(expense: Expense) {
        viewModelScope.launch {
            repository.addExpense(expense.copy(id = 0L))
        }
    }

    /**
     * Show add expense sheet
     */
    fun showAddExpenseSheet() {
        _isAddExpenseSheetVisible.value = true
    }

    /**
     * Hide add expense sheet
     */
    fun hideAddExpenseSheet() {
        _isAddExpenseSheetVisible.value = false
    }

    /**
     * Complete onboarding with initial settings
     */
    fun completeOnboarding(
        monthlyBudgetCents: Long,
        paydayDate: Int,
        cycleStartDate: LocalDate,
        previousExpensesCents: Long
    ) {
        viewModelScope.launch {
            repository.completeOnboarding(
                monthlyBudgetCents = monthlyBudgetCents,
                paydayDate = paydayDate,
                cycleStartDate = cycleStartDate,
                previousExpensesCents = previousExpensesCents
            )
        }
    }

    /**
     * Update monthly budget
     */
    fun updateMonthlyBudget(amountCents: Long) {
        viewModelScope.launch {
            repository.updateMonthlyBudget(amountCents)
        }
    }

    /**
     * Update payday date
     */
    fun updatePaydayDate(day: Int) {
        viewModelScope.launch {
            repository.updatePaydayDate(day)
        }
    }

    fun updateForecastSensitivityPercent(percent: Int) {
        viewModelScope.launch {
            repository.updateForecastSensitivityPercent(percent)
        }
    }
}
