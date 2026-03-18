package net.loeu.wallybudget.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext
import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.ExpenseCategory
import net.loeu.wallybudget.domain.model.ExpenseCycleSection
import net.loeu.wallybudget.domain.model.ExpenseDaySection
import net.loeu.wallybudget.domain.model.HistoryState
import net.loeu.wallybudget.domain.model.HomeOverviewState
import net.loeu.wallybudget.domain.model.MonthlyHistory
import net.loeu.wallybudget.domain.model.PendingCycleCloseoutState
import net.loeu.wallybudget.domain.model.SnapshotError
import net.loeu.wallybudget.domain.model.SnapshotImportPreview
import net.loeu.wallybudget.domain.model.SpendingForecast
import net.loeu.wallybudget.domain.model.TimelineLockState
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.data.time.CurrentDateProvider
import net.loeu.wallybudget.domain.usecase.ApplyOnboardingRestoreUseCase
import net.loeu.wallybudget.domain.usecase.AddExpenseUseCase
import net.loeu.wallybudget.domain.usecase.CompleteOnboardingUseCase
import net.loeu.wallybudget.domain.usecase.CompletePortfolioMigrationUseCase
import net.loeu.wallybudget.domain.usecase.ConcludePendingCycleUseCase
import net.loeu.wallybudget.domain.usecase.ClearPendingSettingsUndoUseCase
import net.loeu.wallybudget.domain.usecase.DeleteExpenseUseCase
import net.loeu.wallybudget.domain.usecase.EnsureBucketMigrationStateUseCase
import net.loeu.wallybudget.domain.usecase.EnsureBudgetPolicyHistoryUseCase
import net.loeu.wallybudget.domain.usecase.ExportSnapshotUseCase
import net.loeu.wallybudget.domain.usecase.ObserveForecastUseCase
import net.loeu.wallybudget.domain.usecase.ObserveHistoryUseCase
import net.loeu.wallybudget.domain.usecase.ObserveHomeOverviewUseCase
import net.loeu.wallybudget.domain.usecase.PerformMonthlyResetUseCase
import net.loeu.wallybudget.domain.usecase.PrepareSnapshotImportUseCase
import net.loeu.wallybudget.domain.usecase.PreparedSnapshotImport
import net.loeu.wallybudget.domain.usecase.RebuildMonthlyHistoryUseCase
import net.loeu.wallybudget.domain.usecase.ResolveMutationEffectiveDateUseCase
import net.loeu.wallybudget.domain.usecase.RestoreDeletedExpenseUseCase
import net.loeu.wallybudget.domain.usecase.SnapshotOperationException
import net.loeu.wallybudget.domain.usecase.SyncObservedDateUseCase
import net.loeu.wallybudget.domain.usecase.UndoBudgetSettingsChangeUseCase
import net.loeu.wallybudget.domain.usecase.UpdateBudgetSettingsRequest
import net.loeu.wallybudget.domain.usecase.UpdateBudgetSettingsUseCase
import net.loeu.wallybudget.domain.usecase.UpdateExpenseUseCase
import net.loeu.wallybudget.domain.model.BudgetChangeMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class SettingsUndoState(
    val expiresAtExclusive: LocalDate
)

@Suppress("TooManyFunctions", "TooGenericExceptionCaught")
class BudgetViewModel(
    upstreamUserSettingsFlow: Flow<UserSettings>,
    observeHomeOverviewUseCase: ObserveHomeOverviewUseCase,
    observeHistoryUseCase: ObserveHistoryUseCase,
    observeForecastUseCase: ObserveForecastUseCase,
    private val addExpenseUseCase: AddExpenseUseCase,
    private val updateExpenseUseCase: UpdateExpenseUseCase,
    private val deleteExpenseUseCase: DeleteExpenseUseCase,
    private val restoreDeletedExpenseUseCase: RestoreDeletedExpenseUseCase,
    private val updateBudgetSettingsUseCase: UpdateBudgetSettingsUseCase,
    private val undoBudgetSettingsChangeUseCase: UndoBudgetSettingsChangeUseCase,
    private val completeOnboardingUseCase: CompleteOnboardingUseCase,
    private val performMonthlyResetUseCase: PerformMonthlyResetUseCase,
    private val concludePendingCycleUseCase: ConcludePendingCycleUseCase,
    private val exportSnapshotUseCase: ExportSnapshotUseCase,
    private val prepareSnapshotImportUseCase: PrepareSnapshotImportUseCase,
    private val applyOnboardingRestoreUseCase: ApplyOnboardingRestoreUseCase,
    private val ensureBucketMigrationStateUseCase: EnsureBucketMigrationStateUseCase,
    private val ensureBudgetPolicyHistoryUseCase: EnsureBudgetPolicyHistoryUseCase,
    private val rebuildMonthlyHistoryUseCase: RebuildMonthlyHistoryUseCase,
    private val completePortfolioMigrationUseCase: CompletePortfolioMigrationUseCase,
    private val resolveMutationEffectiveDateUseCase: ResolveMutationEffectiveDateUseCase,
    private val clearPendingSettingsUndoUseCase: ClearPendingSettingsUndoUseCase,
    pendingSettingsUndoFlow: Flow<net.loeu.wallybudget.domain.model.PendingSettingsUndo?>,
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
    private val _snapshotImportPreview = MutableStateFlow<SnapshotImportPreview?>(null)
    val snapshotImportPreview = _snapshotImportPreview.asStateFlow()
    private val _snapshotError = MutableStateFlow<SnapshotError?>(null)
    val snapshotError = _snapshotError.asStateFlow()
    private val _snapshotStatusMessage = MutableStateFlow<String?>(null)
    val snapshotStatusMessage = _snapshotStatusMessage.asStateFlow()
    private val _snapshotBusy = MutableStateFlow(false)
    val snapshotBusy = _snapshotBusy.asStateFlow()
    private val _settingsStatusMessage = MutableStateFlow<String?>(null)
    val settingsStatusMessage = _settingsStatusMessage.asStateFlow()
    private val _settingsUndoState = MutableStateFlow<SettingsUndoState?>(null)
    val settingsUndoState = _settingsUndoState.asStateFlow()
    private var preparedSnapshotImport: PreparedSnapshotImport? = null

    init {
        viewModelScope.launch {
            ensureBucketMigrationStateUseCase(currentDateProvider.currentDate())
        }
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
        viewModelScope.launch {
            combine(pendingSettingsUndoFlow, effectiveCurrentDate) { pendingUndo, effectiveDate ->
                pendingUndo to effectiveDate
            }.collect { (pendingUndo, effectiveDate) ->
                if (pendingUndo == null) {
                    _settingsUndoState.value = null
                } else if (!effectiveDate.isBefore(pendingUndo.expiresAtExclusiveDate())) {
                    clearPendingSettingsUndoUseCase()
                    _settingsUndoState.value = null
                } else {
                    _settingsUndoState.value = SettingsUndoState(
                        expiresAtExclusive = pendingUndo.expiresAtExclusiveDate()
                    )
                }
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

    fun updateBudgetSettings(
        amountCents: Long,
        paydayDate: Int,
        budgetChangeMode: BudgetChangeMode
    ) {
        viewModelScope.launch {
            val result = updateBudgetSettingsUseCase(
                UpdateBudgetSettingsRequest(
                    monthlyBudgetCents = amountCents,
                    paydayDate = paydayDate,
                    budgetChangeMode = budgetChangeMode
                )
            )
            _settingsStatusMessage.value = result.summaryMessage
        }
    }

    fun completePortfolioMigration(portfolioMonthlyBudgetCents: Long) {
        viewModelScope.launch {
            completePortfolioMigrationUseCase(portfolioMonthlyBudgetCents)
        }
    }

    fun undoBudgetSettingsChange() {
        viewModelScope.launch {
            val result = undoBudgetSettingsChangeUseCase()
            _settingsStatusMessage.value = result.summaryMessage
        }
    }

    fun prepareSnapshotImport(uri: Uri) {
        viewModelScope.launch {
            _snapshotBusy.value = true
            _snapshotError.value = null
            _snapshotStatusMessage.value = null
            preparedSnapshotImport = null
            _snapshotImportPreview.value = null
            try {
                val prepared = prepareSnapshotImportUseCase(uri)
                preparedSnapshotImport = prepared
                _snapshotImportPreview.value = prepared.preview
            } catch (exception: SnapshotOperationException) {
                preparedSnapshotImport = null
                _snapshotImportPreview.value = null
                _snapshotError.value = exception.snapshotError
            } catch (exception: Exception) {
                preparedSnapshotImport = null
                _snapshotImportPreview.value = null
                _snapshotError.value = SnapshotError.IoFailure(
                    exception.message ?: "Unable to read snapshot."
                )
            } finally {
                _snapshotBusy.value = false
            }
        }
    }

    fun applyPreparedSnapshotImport() {
        val prepared = preparedSnapshotImport ?: return
        viewModelScope.launch {
            _snapshotBusy.value = true
            _snapshotError.value = null
            try {
                val result = applyOnboardingRestoreUseCase(prepared)
                preparedSnapshotImport = null
                _snapshotImportPreview.value = null
                _snapshotStatusMessage.value =
                    "Restored ${result.importedExpenseCount} expenses across ${result.importedBudgetPolicyCount} budget cycles."
            } catch (exception: SnapshotOperationException) {
                _snapshotError.value = exception.snapshotError
            } catch (exception: Exception) {
                _snapshotError.value = SnapshotError.IoFailure(
                    exception.message ?: "Unable to apply snapshot."
                )
            } finally {
                _snapshotBusy.value = false
            }
        }
    }

    fun clearPreparedSnapshotImport() {
        preparedSnapshotImport = null
        _snapshotImportPreview.value = null
        _snapshotError.value = null
    }

    fun exportSnapshot(uri: Uri) {
        viewModelScope.launch {
            _snapshotBusy.value = true
            _snapshotError.value = null
            _snapshotStatusMessage.value = null
            try {
                val bytesWritten = withContext(Dispatchers.IO) {
                    exportSnapshotUseCase(uri)
                }
                _snapshotStatusMessage.value = "Compressed snapshot exported (${bytesWritten} bytes)."
            } catch (exception: SnapshotOperationException) {
                _snapshotError.value = exception.snapshotError
            } finally {
                _snapshotBusy.value = false
            }
        }
    }

    fun clearSnapshotFeedback() {
        _snapshotError.value = null
        _snapshotStatusMessage.value = null
    }

    fun clearSettingsFeedback() {
        _settingsStatusMessage.value = null
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
