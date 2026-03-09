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
import net.loeu.wallybudget.data.model.ExpenseCycleSection
import net.loeu.wallybudget.data.model.ExpenseDaySection
import net.loeu.wallybudget.data.model.MonthlyHistory
import net.loeu.wallybudget.data.model.PendingCycleCloseoutState
import net.loeu.wallybudget.data.model.SpendingForecast
import net.loeu.wallybudget.data.model.TimelineLockState
import net.loeu.wallybudget.data.model.UserSettings
import net.loeu.wallybudget.data.repository.BudgetRepository
import net.loeu.wallybudget.data.time.CurrentDateProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal object ExpenseEntryDatePolicy {
    fun resolveRequestedDate(requestedDate: LocalDate?, effectiveCurrentDate: LocalDate): LocalDate {
        return when {
            requestedDate == null -> effectiveCurrentDate
            requestedDate.isAfter(effectiveCurrentDate) -> effectiveCurrentDate
            else -> requestedDate
        }
    }
}

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

    val effectiveCurrentDate: StateFlow<LocalDate> = repository.getEffectiveCurrentDate()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = currentDateProvider.currentDate()
        )

    // Budget state
    val budgetState: StateFlow<BudgetState?> = repository.getBudgetState()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // Today's expenses
    val todayExpenses: StateFlow<List<Expense>> = repository.getTodayExpenses()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val activeCycleExpenseSections: StateFlow<List<ExpenseDaySection>> = repository.getActiveCycleExpenseSections()
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

    val spendingForecast: StateFlow<SpendingForecast?> = repository.getSpendingForecast()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val historySections: StateFlow<List<ExpenseCycleSection>> = repository.getHistorySections()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val pendingCycleCloseoutState: StateFlow<PendingCycleCloseoutState?> = repository.getPendingCycleCloseoutState()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val timelineLockState: StateFlow<TimelineLockState> = repository.getTimelineLockState()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TimelineLockState()
        )

    // UI state
    private val _isAddExpenseSheetVisible = MutableStateFlow(false)
    val isAddExpenseSheetVisible = _isAddExpenseSheetVisible.asStateFlow()

    init {
        // Check for monthly reset on initialization and local date changes
        viewModelScope.launch {
            combine(userSettingsFlow, currentDateProvider.observeCurrentDate()) { settings, observedDate ->
                settings to observedDate
            }.collect { (settings, observedDate) ->
                val effectiveDate = repository.syncObservedDate(settings, observedDate)
                repository.checkAndPerformMonthlyReset(settings, effectiveDate)
            }
        }
    }

    fun refreshCycleState() {
        viewModelScope.launch {
            val settings = userSettings.value
            val effectiveDate = repository.syncObservedDate(settings, currentDateProvider.currentDate())
            repository.checkAndPerformMonthlyReset(settings, effectiveDate)
        }
    }

    /**
     * Add a new expense
     */
    fun addExpense(amountCents: Long, description: String, icon: ExpenseCategory? = null, date: LocalDate? = null) {
        viewModelScope.launch {
            val effectiveDate = currentEffectiveDateForMutation() ?: run {
                _isAddExpenseSheetVisible.value = false
                return@launch
            }
            val resolvedDate = ExpenseEntryDatePolicy.resolveRequestedDate(date, effectiveDate)
            val timestamp = if (resolvedDate == effectiveDate) {
                Instant.now().toEpochMilli()
            } else {
                resolvedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
            val expense = Expense(
                amountCents = amountCents,
                description = description,
                icon = icon,
                timestamp = timestamp,
                expenseDate = resolvedDate.toString()
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
            if (currentEffectiveDateForMutation() == null) return@launch
            repository.updateExpense(expense)
        }
    }

    /**
     * Delete an expense
     */
    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            if (currentEffectiveDateForMutation() == null) return@launch
            repository.deleteExpense(expense)
        }
    }

    fun restoreDeletedExpense(expense: Expense) {
        viewModelScope.launch {
            if (currentEffectiveDateForMutation() == null) return@launch
            repository.addExpense(expense.copy(id = 0L))
        }
    }

    fun concludePendingCycle() {
        viewModelScope.launch {
            repository.concludePendingCycle(userSettings.value)
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

    private suspend fun currentEffectiveDateForMutation(): LocalDate? {
        val settings = userSettings.value
        val effectiveDate = repository.syncObservedDate(
            settings = settings,
            observedDate = currentDateProvider.currentDate()
        )
        return if (timelineLockState.value.isLocked) {
            null
        } else {
            effectiveDate
        }
    }
}
