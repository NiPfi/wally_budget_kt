package net.loeu.wallybudget.ui.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.ExpenseCategory
import net.loeu.wallybudget.domain.model.ExpenseCycleSection
import net.loeu.wallybudget.domain.model.ExpenseDaySection
import net.loeu.wallybudget.domain.model.HistoryState
import net.loeu.wallybudget.domain.model.HomeOverviewState
import net.loeu.wallybudget.domain.model.MonthlyHistory
import net.loeu.wallybudget.domain.model.PendingCycleCloseoutState
import net.loeu.wallybudget.domain.model.SpendingForecast
import net.loeu.wallybudget.domain.model.TimelineLockState
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.data.time.CurrentDateProvider
import net.loeu.wallybudget.domain.usecase.AddExpenseUseCase
import net.loeu.wallybudget.domain.usecase.CompleteOnboardingUseCase
import net.loeu.wallybudget.domain.usecase.ConcludePendingCycleUseCase
import net.loeu.wallybudget.domain.usecase.DeleteExpenseUseCase
import net.loeu.wallybudget.domain.usecase.EnsureBudgetPolicyHistoryUseCase
import net.loeu.wallybudget.domain.usecase.ObserveForecastUseCase
import net.loeu.wallybudget.domain.usecase.ObserveHistoryUseCase
import net.loeu.wallybudget.domain.usecase.ObserveHomeOverviewUseCase
import net.loeu.wallybudget.domain.usecase.PerformMonthlyResetUseCase
import net.loeu.wallybudget.domain.usecase.RebuildMonthlyHistoryUseCase
import net.loeu.wallybudget.domain.usecase.ResolveMutationEffectiveDateUseCase
import net.loeu.wallybudget.domain.usecase.RestoreDeletedExpenseUseCase
import net.loeu.wallybudget.domain.usecase.SyncObservedDateUseCase
import net.loeu.wallybudget.domain.usecase.UpdateExpenseUseCase
import net.loeu.wallybudget.domain.usecase.UpdateMonthlyBudgetUseCase
import net.loeu.wallybudget.domain.usecase.UpdatePaydayDateUseCase
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class BudgetViewModel(
    upstreamUserSettingsFlow: Flow<UserSettings>,
    observeHomeOverviewUseCase: ObserveHomeOverviewUseCase,
    observeHistoryUseCase: ObserveHistoryUseCase,
    observeForecastUseCase: ObserveForecastUseCase,
    private val addExpenseUseCase: AddExpenseUseCase,
    private val updateExpenseUseCase: UpdateExpenseUseCase,
    private val deleteExpenseUseCase: DeleteExpenseUseCase,
    private val restoreDeletedExpenseUseCase: RestoreDeletedExpenseUseCase,
    private val updateMonthlyBudgetUseCase: UpdateMonthlyBudgetUseCase,
    private val updatePaydayDateUseCase: UpdatePaydayDateUseCase,
    private val completeOnboardingUseCase: CompleteOnboardingUseCase,
    private val performMonthlyResetUseCase: PerformMonthlyResetUseCase,
    private val concludePendingCycleUseCase: ConcludePendingCycleUseCase,
    private val ensureBudgetPolicyHistoryUseCase: EnsureBudgetPolicyHistoryUseCase,
    private val rebuildMonthlyHistoryUseCase: RebuildMonthlyHistoryUseCase,
    private val resolveMutationEffectiveDateUseCase: ResolveMutationEffectiveDateUseCase,
    private val syncObservedDateUseCase: SyncObservedDateUseCase,
    private val currentDateProvider: CurrentDateProvider
) : ViewModel() {

    val userSettingsFlow: Flow<UserSettings> = upstreamUserSettingsFlow
    private val homeOverviewState: StateFlow<HomeOverviewState?> = observeHomeOverviewUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
    private val historyState: StateFlow<HistoryState?> = observeHistoryUseCase(
        homeOverviewState.filterNotNull()
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    // User settings
    val userSettings: StateFlow<UserSettings> = userSettingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserSettings()
        )

    val effectiveCurrentDate: StateFlow<LocalDate> = homeOverviewState
        .map { it?.effectiveCurrentDate ?: currentDateProvider.currentDate() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = currentDateProvider.currentDate()
        )

    // Budget state
    val budgetState: StateFlow<BudgetState?> = homeOverviewState
        .map { it?.budgetState }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // Today's expenses
    val todayExpenses: StateFlow<List<Expense>> = homeOverviewState
        .map { it?.todayExpenses ?: emptyList() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val activeCycleExpenseSections: StateFlow<List<ExpenseDaySection>> = homeOverviewState
        .map { it?.activeCycleExpenseSections ?: emptyList() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Monthly history
    val monthlyHistory: StateFlow<List<MonthlyHistory>?> = historyState
        .map { it?.monthlyHistory }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val spendingForecast: StateFlow<SpendingForecast?> = observeForecastUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val historySections: StateFlow<List<ExpenseCycleSection>> = historyState
        .map { it?.historySections ?: emptyList() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val pendingCycleCloseoutState: StateFlow<PendingCycleCloseoutState?> = homeOverviewState
        .map { it?.pendingCycleCloseoutState }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val timelineLockState: StateFlow<TimelineLockState> = homeOverviewState
        .map { it?.timelineLockState ?: TimelineLockState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TimelineLockState()
        )

    // UI state
    private val _isAddExpenseSheetVisible = MutableStateFlow(false)
    val isAddExpenseSheetVisible = _isAddExpenseSheetVisible.asStateFlow()

    init {
        viewModelScope.launch {
            ensureBudgetPolicyHistoryUseCase(currentDateProvider.currentDate())
        }
        // Check for monthly reset on initialization and local date changes
        viewModelScope.launch {
            combine(userSettingsFlow, currentDateProvider.observeCurrentDate()) { settings, observedDate ->
                settings to observedDate
            }.collect { (settings, observedDate) ->
                val effectiveDate = syncObservedDateUseCase(settings, observedDate)
                performMonthlyResetUseCase(settings, effectiveDate)
            }
        }
    }

    fun refreshCycleState() {
        viewModelScope.launch {
            val settings = userSettings.value
            val effectiveDate = syncObservedDateUseCase(settings, currentDateProvider.currentDate())
            performMonthlyResetUseCase(settings, effectiveDate)
        }
    }

    /**
     * Add a new expense
     */
    fun addExpense(amountCents: Long, description: String, icon: ExpenseCategory? = null, date: LocalDate? = null) {
        viewModelScope.launch {
            val effectiveDate = currentEffectiveDateForMutation(
                resolveMutationEffectiveDateUseCase = resolveMutationEffectiveDateUseCase,
                settings = userSettings.value,
                currentDateProvider = currentDateProvider
            ) ?: run {
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
            addExpenseUseCase(expense)
            _isAddExpenseSheetVisible.value = false
        }
    }

    /**
     * Update an expense
     */
    fun updateExpense(expense: Expense) {
        viewModelScope.launch {
            if (
                currentEffectiveDateForMutation(
                    resolveMutationEffectiveDateUseCase = resolveMutationEffectiveDateUseCase,
                    settings = userSettings.value,
                    currentDateProvider = currentDateProvider
                ) == null
            ) {
                return@launch
            }
            updateExpenseUseCase(expense)
        }
    }

    /**
     * Delete an expense
     */
    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            if (
                currentEffectiveDateForMutation(
                    resolveMutationEffectiveDateUseCase = resolveMutationEffectiveDateUseCase,
                    settings = userSettings.value,
                    currentDateProvider = currentDateProvider
                ) == null
            ) {
                return@launch
            }
            deleteExpenseUseCase(expense)
        }
    }

    fun restoreDeletedExpense(expense: Expense) {
        viewModelScope.launch {
            if (
                currentEffectiveDateForMutation(
                    resolveMutationEffectiveDateUseCase = resolveMutationEffectiveDateUseCase,
                    settings = userSettings.value,
                    currentDateProvider = currentDateProvider
                ) == null
            ) {
                return@launch
            }
            restoreDeletedExpenseUseCase(expense)
        }
    }

    fun concludePendingCycle() {
        viewModelScope.launch {
            concludePendingCycleUseCase(userSettings.value)
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
            completeOnboardingUseCase(
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
            updateMonthlyBudgetUseCase(amountCents)
        }
    }

    /**
     * Update payday date
     */
    fun updatePaydayDate(day: Int) {
        viewModelScope.launch {
            updatePaydayDateUseCase(day)
        }
    }
}

private suspend fun currentEffectiveDateForMutation(
    resolveMutationEffectiveDateUseCase: ResolveMutationEffectiveDateUseCase,
    settings: UserSettings,
    currentDateProvider: CurrentDateProvider
): LocalDate? {
    return resolveMutationEffectiveDateUseCase(
        settings = settings,
        observedDate = currentDateProvider.currentDate()
    )
}
