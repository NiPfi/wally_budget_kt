package net.loeu.wallybudget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.map
import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.ExpenseCycleSection
import net.loeu.wallybudget.domain.model.ExpenseDaySection
import net.loeu.wallybudget.domain.model.PendingCycleCloseoutState
import net.loeu.wallybudget.domain.model.SnapshotError
import net.loeu.wallybudget.domain.model.SnapshotImportPreview
import net.loeu.wallybudget.domain.model.SpendingForecast
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.ui.viewmodel.BudgetViewModel
import java.time.LocalDate

internal data class BudgetAppState(
    val isOnboardingCompleted: Boolean?,
    val userSettings: UserSettings,
    val budgetState: BudgetState?,
    val effectiveCurrentDate: LocalDate,
    val todayExpenses: List<Expense>,
    val activeCycleExpenseSections: List<ExpenseDaySection>,
    val spendingForecast: SpendingForecast?,
    val historySections: List<ExpenseCycleSection>,
    val pendingCycleCloseoutState: PendingCycleCloseoutState?,
    val timelineLockReason: String?,
    val isAddExpenseSheetVisible: Boolean,
    val isHomeDataLoading: Boolean,
    val snapshotImportPreview: SnapshotImportPreview?,
    val snapshotError: SnapshotError?,
    val snapshotStatusMessage: String?,
    val snapshotBusy: Boolean
)

@Composable
internal fun rememberBudgetAppUiState(viewModel: BudgetViewModel): BudgetAppState {
    val isOnboardingCompleted by remember(viewModel) {
        viewModel.userSettingsFlow.map { it.isOnboardingCompleted }
    }.collectAsState(initial = null)
    val userSettings by viewModel.userSettings.collectAsState()
    val budgetState by viewModel.budgetState.collectAsState()
    val effectiveCurrentDate by viewModel.effectiveCurrentDate.collectAsState()
    val todayExpenses by viewModel.todayExpenses.collectAsState()
    val activeCycleExpenseSections by viewModel.activeCycleExpenseSections.collectAsState()
    val spendingForecast by viewModel.spendingForecast.collectAsState()
    val historySections by viewModel.historySections.collectAsState()
    val pendingCycleCloseoutState by viewModel.pendingCycleCloseoutState.collectAsState()
    val timelineLockReason by remember(viewModel) { viewModel.timelineLockState.map { it.reason } }
        .collectAsState(initial = null)
    val isAddExpenseSheetVisible by viewModel.isAddExpenseSheetVisible.collectAsState()
    val snapshotImportPreview by viewModel.snapshotImportPreview.collectAsState()
    val snapshotError by viewModel.snapshotError.collectAsState()
    val snapshotStatusMessage by viewModel.snapshotStatusMessage.collectAsState()
    val snapshotBusy by viewModel.snapshotBusy.collectAsState()

    return BudgetAppState(
        isOnboardingCompleted = isOnboardingCompleted,
        userSettings = userSettings,
        budgetState = budgetState,
        effectiveCurrentDate = effectiveCurrentDate,
        todayExpenses = todayExpenses,
        activeCycleExpenseSections = activeCycleExpenseSections,
        spendingForecast = spendingForecast,
        historySections = historySections,
        pendingCycleCloseoutState = pendingCycleCloseoutState,
        timelineLockReason = timelineLockReason,
        isAddExpenseSheetVisible = isAddExpenseSheetVisible,
        isHomeDataLoading = budgetState == null || spendingForecast == null,
        snapshotImportPreview = snapshotImportPreview,
        snapshotError = snapshotError,
        snapshotStatusMessage = snapshotStatusMessage,
        snapshotBusy = snapshotBusy
    )
}
