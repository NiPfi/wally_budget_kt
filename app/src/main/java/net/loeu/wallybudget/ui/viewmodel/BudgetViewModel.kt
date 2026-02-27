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
import net.loeu.wallybudget.data.model.ExpenseIcon
import net.loeu.wallybudget.data.model.MonthlyHistory
import net.loeu.wallybudget.data.model.UserSettings
import net.loeu.wallybudget.data.repository.BudgetRepository
import java.time.Instant
import java.time.LocalDate

class BudgetViewModel(
    private val repository: BudgetRepository
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
                monthlyBudget = 0.0,
                totalSpentThisCycle = 0.0,
                dailyBudget = 0.0,
                spentToday = 0.0,
                remainingToday = 0.0,
                daysRemainingInCycle = 0,
                cumulativeSavings = 0.0,
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

    // UI state
    private val _isAddExpenseSheetVisible = MutableStateFlow(false)
    val isAddExpenseSheetVisible = _isAddExpenseSheetVisible.asStateFlow()

    init {
        // Check for monthly reset on initialization and local date changes
        viewModelScope.launch {
            combine(userSettingsFlow, repository.observeCurrentDate()) { settings, _ -> settings }
                .collect { settings ->
                repository.checkAndPerformMonthlyReset(settings)
            }
        }
    }

    /**
     * Add a new expense
     */
    fun addExpense(amount: Double, description: String, icon: ExpenseIcon? = null, date: LocalDate = LocalDate.now()) {
        viewModelScope.launch {
            val timestamp = if (date == LocalDate.now()) {
                Instant.now().toEpochMilli()
            } else {
                date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
            val expense = Expense(
                amount = amount,
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
        monthlyBudget: Double,
        paydayDate: Int,
        cycleStartDate: LocalDate,
        previousExpenses: Double
    ) {
        viewModelScope.launch {
            repository.completeOnboarding(
                monthlyBudget = monthlyBudget,
                paydayDate = paydayDate,
                cycleStartDate = cycleStartDate,
                previousExpenses = previousExpenses
            )
        }
    }

    /**
     * Update monthly budget
     */
    fun updateMonthlyBudget(amount: Double) {
        viewModelScope.launch {
            repository.updateMonthlyBudget(amount)
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
}


