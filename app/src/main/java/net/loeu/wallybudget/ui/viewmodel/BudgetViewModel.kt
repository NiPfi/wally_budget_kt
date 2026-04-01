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
import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.BucketSummaryState
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.ExpenseCategory
import net.loeu.wallybudget.domain.model.ExpenseCycleSection
import net.loeu.wallybudget.domain.model.ExpenseDaySection
import net.loeu.wallybudget.domain.model.Fund
import net.loeu.wallybudget.domain.model.HistoryState
import net.loeu.wallybudget.domain.model.MonthlyHistory
import net.loeu.wallybudget.domain.model.PendingCycleCloseoutState
import net.loeu.wallybudget.domain.model.PortfolioOverviewState
import net.loeu.wallybudget.domain.model.PortfolioState
import net.loeu.wallybudget.domain.model.SelectedBucketOverview
import net.loeu.wallybudget.domain.model.SnapshotError
import net.loeu.wallybudget.domain.model.SnapshotImportPreview
import net.loeu.wallybudget.domain.model.SpendingForecast
import net.loeu.wallybudget.domain.model.TimelineLockState
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.data.time.CurrentDateProvider
import net.loeu.wallybudget.data.time.WallyTime
import net.loeu.wallybudget.domain.usecase.ApplyOnboardingRestoreUseCase
import net.loeu.wallybudget.domain.usecase.AddExpenseUseCase
import net.loeu.wallybudget.domain.usecase.CreateGoalFundRequest
import net.loeu.wallybudget.domain.usecase.CreateGoalFundUseCase
import net.loeu.wallybudget.domain.usecase.CompleteOnboardingUseCase
import net.loeu.wallybudget.domain.usecase.ConcludePendingCycleUseCase
import net.loeu.wallybudget.domain.usecase.ClearPendingPaydayUndoUseCase
import net.loeu.wallybudget.domain.usecase.DeleteExpenseUseCase
import net.loeu.wallybudget.domain.usecase.EnsureDefaultBucketStateUseCase
import net.loeu.wallybudget.domain.usecase.EnsureBudgetPolicyHistoryUseCase
import net.loeu.wallybudget.domain.usecase.EnsureDefaultFundUseCase
import net.loeu.wallybudget.domain.usecase.ExportSnapshotUseCase
import net.loeu.wallybudget.domain.usecase.ExpenseEditNotAllowedException
import net.loeu.wallybudget.domain.usecase.ObserveBudgetBucketsUseCase
import net.loeu.wallybudget.domain.usecase.ObserveForecastUseCase
import net.loeu.wallybudget.domain.usecase.ObserveHistoryUseCase
import net.loeu.wallybudget.domain.usecase.ObserveHomeOverviewUseCase
import net.loeu.wallybudget.domain.usecase.PerformMonthlyResetUseCase
import net.loeu.wallybudget.domain.usecase.PrepareSnapshotImportUseCase
import net.loeu.wallybudget.domain.usecase.PreparedSnapshotImport
import net.loeu.wallybudget.domain.usecase.RebuildMonthlyHistoryUseCase
import net.loeu.wallybudget.domain.usecase.ResolveMutationEffectiveDateUseCase
import net.loeu.wallybudget.domain.usecase.RestoreDeletedExpenseUseCase
import net.loeu.wallybudget.domain.usecase.SelectBucketUseCase
import net.loeu.wallybudget.domain.usecase.SnapshotOperationException
import net.loeu.wallybudget.domain.usecase.SyncObservedDateUseCase
import net.loeu.wallybudget.domain.usecase.UndoPaydayChangeUseCase
import net.loeu.wallybudget.domain.usecase.BucketDraft
import net.loeu.wallybudget.domain.usecase.UpdatePaydayRequest
import net.loeu.wallybudget.domain.usecase.UpdatePaydayUseCase
import net.loeu.wallybudget.domain.usecase.UpdateGoalFundRequest
import net.loeu.wallybudget.domain.usecase.UpdateGoalFundUseCase
import net.loeu.wallybudget.domain.usecase.UpdatePortfolioPlanRequest
import net.loeu.wallybudget.domain.usecase.UpdatePortfolioPlanUseCase
import net.loeu.wallybudget.domain.usecase.UpdateExpenseUseCase
import java.time.LocalDate

data class PaydayUndoState(
    val expiresAtExclusive: LocalDate
)

@Suppress("TooManyFunctions", "TooGenericExceptionCaught")
class BudgetViewModel(
    upstreamUserSettingsFlow: Flow<UserSettings>,
    observeHomeOverviewUseCase: ObserveHomeOverviewUseCase,
    observeBudgetBucketsUseCase: ObserveBudgetBucketsUseCase,
    observeHistoryUseCase: ObserveHistoryUseCase,
    observeForecastUseCase: ObserveForecastUseCase,
    private val addExpenseUseCase: AddExpenseUseCase,
    private val updateExpenseUseCase: UpdateExpenseUseCase,
    private val deleteExpenseUseCase: DeleteExpenseUseCase,
    private val restoreDeletedExpenseUseCase: RestoreDeletedExpenseUseCase,
    private val createGoalFundUseCase: CreateGoalFundUseCase,
    private val updateGoalFundUseCase: UpdateGoalFundUseCase,
    private val updatePortfolioPlanUseCase: UpdatePortfolioPlanUseCase,
    private val updatePaydayUseCase: UpdatePaydayUseCase,
    private val undoPaydayChangeUseCase: UndoPaydayChangeUseCase,
    private val completeOnboardingUseCase: CompleteOnboardingUseCase,
    private val performMonthlyResetUseCase: PerformMonthlyResetUseCase,
    private val concludePendingCycleUseCase: ConcludePendingCycleUseCase,
    private val exportSnapshotUseCase: ExportSnapshotUseCase,
    private val prepareSnapshotImportUseCase: PrepareSnapshotImportUseCase,
    private val applyOnboardingRestoreUseCase: ApplyOnboardingRestoreUseCase,
    private val ensureDefaultBucketStateUseCase: EnsureDefaultBucketStateUseCase,
    private val ensureDefaultFundUseCase: EnsureDefaultFundUseCase,
    private val ensureBudgetPolicyHistoryUseCase: EnsureBudgetPolicyHistoryUseCase,
    private val rebuildMonthlyHistoryUseCase: RebuildMonthlyHistoryUseCase,
    private val resolveMutationEffectiveDateUseCase: ResolveMutationEffectiveDateUseCase,
    private val selectBucketUseCase: SelectBucketUseCase,
    private val clearPendingPaydayUndoUseCase: ClearPendingPaydayUndoUseCase,
    pendingPaydayUndoFlow: Flow<net.loeu.wallybudget.domain.model.PendingPaydayUndo?>,
    private val syncObservedDateUseCase: SyncObservedDateUseCase,
    private val currentDateProvider: CurrentDateProvider
) : ViewModel() {

    val userSettingsFlow: Flow<UserSettings> = upstreamUserSettingsFlow
    val portfolioOverviewState: StateFlow<PortfolioOverviewState?> = observeHomeOverviewUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
    val allBuckets: StateFlow<List<BudgetBucket>> = observeBudgetBucketsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    private val historyState: StateFlow<HistoryState?> = observeHistoryUseCase(
        portfolioOverviewState.filterNotNull()
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

    val effectiveCurrentDate: StateFlow<LocalDate> = portfolioOverviewState
        .map { it?.effectiveCurrentDate ?: currentDateProvider.currentDate() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = currentDateProvider.currentDate()
        )

    val selectedBucketOverview: StateFlow<SelectedBucketOverview?> = portfolioOverviewState
        .map { it?.selectedBucketOverview }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val portfolioState: StateFlow<PortfolioState?> = portfolioOverviewState
        .map { it?.portfolioState }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val bucketSummaries: StateFlow<List<BucketSummaryState>> = portfolioOverviewState
        .map { it?.bucketSummaries ?: emptyList() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val funds: StateFlow<List<Fund>> = portfolioOverviewState
        .map { it?.funds ?: emptyList() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Selected bucket state
    val budgetState: StateFlow<BudgetState?> = selectedBucketOverview
        .map { it?.budgetState }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val todayExpenses: StateFlow<List<Expense>> = selectedBucketOverview
        .map { it?.todayExpenses ?: emptyList() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val activeCycleExpenseSections: StateFlow<List<ExpenseDaySection>> = selectedBucketOverview
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

    val historyBucketNameByUuid: StateFlow<Map<String, String>> = historyState
        .map { it?.bucketNameByUuid ?: emptyMap() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    val pendingCycleCloseoutState: StateFlow<PendingCycleCloseoutState?> = portfolioOverviewState
        .map { it?.pendingCycleCloseoutState }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val timelineLockState: StateFlow<TimelineLockState> = portfolioOverviewState
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
    private val _paydayUndoState = MutableStateFlow<PaydayUndoState?>(null)
    val paydayUndoState = _paydayUndoState.asStateFlow()
    private var preparedSnapshotImport: PreparedSnapshotImport? = null

    init {
        viewModelScope.launch {
            ensureDefaultBucketStateUseCase(currentDateProvider.currentDate())
        }
        viewModelScope.launch {
            ensureDefaultFundUseCase(currentDateProvider.currentDate())
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
            combine(pendingPaydayUndoFlow, effectiveCurrentDate) { pendingUndo, effectiveDate ->
                pendingUndo to effectiveDate
            }.collect { (pendingUndo, effectiveDate) ->
                if (pendingUndo == null) {
                    _paydayUndoState.value = null
                } else if (!effectiveDate.isBefore(pendingUndo.expiresAtExclusiveDate())) {
                    clearPendingPaydayUndoUseCase()
                    _paydayUndoState.value = null
                } else {
                    _paydayUndoState.value = PaydayUndoState(
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
    fun addExpense(
        bucketUuid: String,
        amountCents: Long,
        description: String,
        icon: ExpenseCategory? = null,
        date: LocalDate? = null
    ) {
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
                WallyTime.currentEpochTimeMs()
            } else {
                WallyTime.startOfDayEpochTimeMs(resolvedDate)
            }
            val expense = Expense(
                amountCents = amountCents,
                description = description,
                icon = icon,
                timestamp = timestamp,
                expenseDate = resolvedDate.toString(),
                bucketUuid = bucketUuid
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
            try {
                updateExpenseUseCase(expense)
            } catch (_: ExpenseEditNotAllowedException) {
                return@launch
            }
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

    suspend fun createGoalFund(name: String, targetAmountCents: Long) {
        createGoalFundUseCase(
            CreateGoalFundRequest(
                name = name,
                targetAmountCents = targetAmountCents
            )
        )
    }

    suspend fun updateGoalFund(goalUuid: String, name: String, targetAmountCents: Long) {
        updateGoalFundUseCase(
            UpdateGoalFundRequest(
                fundUuid = goalUuid,
                name = name,
                targetAmountCents = targetAmountCents
            )
        )
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

    fun selectBucket(bucketUuid: String) {
        viewModelScope.launch {
            selectBucketUseCase(bucketUuid)
        }
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

    fun updatePortfolioPlan(
        portfolioMonthlyBudgetCents: Long,
        buckets: List<BucketDraft>
    ) {
        viewModelScope.launch {
            try {
                val result = updatePortfolioPlanUseCase(
                    UpdatePortfolioPlanRequest(
                        portfolioMonthlyBudgetCents = portfolioMonthlyBudgetCents,
                        buckets = buckets
                    )
                )
                _settingsStatusMessage.value = result.summaryMessage
            } catch (exception: IllegalArgumentException) {
                _settingsStatusMessage.value = exception.message ?: "Unable to save portfolio plan."
            }
        }
    }

    fun updatePayday(paydayDate: Int) {
        viewModelScope.launch {
            try {
                val result = updatePaydayUseCase(
                    UpdatePaydayRequest(paydayDate = paydayDate)
                )
                _settingsStatusMessage.value = result.summaryMessage
            } catch (exception: IllegalArgumentException) {
                _settingsStatusMessage.value = exception.message ?: "Unable to update payday."
            }
        }
    }

    fun undoPaydayChange() {
        viewModelScope.launch {
            val result = undoPaydayChangeUseCase()
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
