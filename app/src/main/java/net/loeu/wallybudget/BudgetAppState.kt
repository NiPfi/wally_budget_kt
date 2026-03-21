package net.loeu.wallybudget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.map
import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.BucketSummaryState
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.ExpenseCycleSection
import net.loeu.wallybudget.domain.model.ExpenseDaySection
import net.loeu.wallybudget.domain.model.PendingCycleCloseoutState
import net.loeu.wallybudget.domain.model.PortfolioOverviewState
import net.loeu.wallybudget.domain.model.PortfolioState
import net.loeu.wallybudget.domain.model.SelectedBucketOverview
import net.loeu.wallybudget.domain.model.SnapshotError
import net.loeu.wallybudget.domain.model.SnapshotImportPreview
import net.loeu.wallybudget.domain.model.SpendingForecast
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.ui.viewmodel.BudgetViewModel
import java.time.LocalDate

internal data class BudgetAppState(
    val isOnboardingCompleted: Boolean?,
    val userSettings: UserSettings,
    val portfolioOverviewState: PortfolioOverviewState?,
    val portfolioState: PortfolioState?,
    val selectedBucketOverview: SelectedBucketOverview?,
    val bucketSummaries: List<BucketSummaryState>,
    val allBuckets: List<BudgetBucket>,
    val budgetState: BudgetState?,
    val effectiveCurrentDate: LocalDate,
    val todayExpenses: List<Expense>,
    val activeCycleExpenseSections: List<ExpenseDaySection>,
    val spendingForecast: SpendingForecast?,
    val historySections: List<ExpenseCycleSection>,
    val historyBucketNameByUuid: Map<String, String>,
    val pendingCycleCloseoutState: PendingCycleCloseoutState?,
    val timelineLockReason: String?,
    val isAddExpenseSheetVisible: Boolean,
    val isHomeDataLoading: Boolean,
    val snapshotImportPreview: SnapshotImportPreview?,
    val snapshotError: SnapshotError?,
    val snapshotStatusMessage: String?,
    val snapshotBusy: Boolean,
    val settingsStatusMessage: String?,
    val isPaydayUndoAvailable: Boolean,
    val paydayUndoExpiresAtExclusive: LocalDate?
)

@Composable
internal fun rememberBudgetAppUiState(viewModel: BudgetViewModel): BudgetAppState {
    val isOnboardingCompleted by remember(viewModel) {
        viewModel.userSettingsFlow.map { it.isOnboardingCompleted }
    }.collectAsState(initial = null)
    val userSettings by viewModel.userSettings.collectAsState()
    val portfolioOverviewState by viewModel.portfolioOverviewState.collectAsState()
    val portfolioState by viewModel.portfolioState.collectAsState()
    val selectedBucketOverview by viewModel.selectedBucketOverview.collectAsState()
    val bucketSummaries by viewModel.bucketSummaries.collectAsState()
    val allBuckets by viewModel.allBuckets.collectAsState()
    val budgetState by viewModel.budgetState.collectAsState()
    val effectiveCurrentDate by viewModel.effectiveCurrentDate.collectAsState()
    val todayExpenses by viewModel.todayExpenses.collectAsState()
    val activeCycleExpenseSections by viewModel.activeCycleExpenseSections.collectAsState()
    val spendingForecast by viewModel.spendingForecast.collectAsState()
    val historySections by viewModel.historySections.collectAsState()
    val historyBucketNameByUuid by viewModel.historyBucketNameByUuid.collectAsState()
    val pendingCycleCloseoutState by viewModel.pendingCycleCloseoutState.collectAsState()
    val timelineLockReason by remember(viewModel) { viewModel.timelineLockState.map { it.reason } }
        .collectAsState(initial = null)
    val isAddExpenseSheetVisible by viewModel.isAddExpenseSheetVisible.collectAsState()
    val snapshotImportPreview by viewModel.snapshotImportPreview.collectAsState()
    val snapshotError by viewModel.snapshotError.collectAsState()
    val snapshotStatusMessage by viewModel.snapshotStatusMessage.collectAsState()
    val snapshotBusy by viewModel.snapshotBusy.collectAsState()
    val settingsStatusMessage by viewModel.settingsStatusMessage.collectAsState()
    val paydayUndoState by viewModel.paydayUndoState.collectAsState()

    return BudgetAppState(
        isOnboardingCompleted = isOnboardingCompleted,
        userSettings = userSettings,
        portfolioOverviewState = portfolioOverviewState,
        portfolioState = portfolioState,
        selectedBucketOverview = selectedBucketOverview,
        bucketSummaries = bucketSummaries,
        allBuckets = allBuckets,
        budgetState = budgetState,
        effectiveCurrentDate = effectiveCurrentDate,
        todayExpenses = todayExpenses,
        activeCycleExpenseSections = activeCycleExpenseSections,
        spendingForecast = spendingForecast,
        historySections = historySections,
        historyBucketNameByUuid = historyBucketNameByUuid,
        pendingCycleCloseoutState = pendingCycleCloseoutState,
        timelineLockReason = timelineLockReason,
        isAddExpenseSheetVisible = isAddExpenseSheetVisible,
        isHomeDataLoading = portfolioOverviewState == null,
        snapshotImportPreview = snapshotImportPreview,
        snapshotError = snapshotError,
        snapshotStatusMessage = snapshotStatusMessage,
        snapshotBusy = snapshotBusy,
        settingsStatusMessage = settingsStatusMessage,
        isPaydayUndoAvailable = paydayUndoState != null,
        paydayUndoExpiresAtExclusive = paydayUndoState?.expiresAtExclusive
    )
}
