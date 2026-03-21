package net.loeu.wallybudget

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import kotlinx.coroutines.flow.StateFlow
import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.domain.model.BucketSummaryState
import net.loeu.wallybudget.domain.model.BucketTrackingMode
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.ExpenseCategory
import net.loeu.wallybudget.domain.model.ExpenseCycleSection
import net.loeu.wallybudget.domain.model.MonthlyHistory
import net.loeu.wallybudget.domain.model.PortfolioState
import net.loeu.wallybudget.domain.model.SelectedBucketOverview
import net.loeu.wallybudget.domain.model.SpendingForecast
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.usecase.BucketDraft
import net.loeu.wallybudget.ui.navigation.Screen
import net.loeu.wallybudget.ui.screens.analysis.AnalysisScreen
import net.loeu.wallybudget.ui.screens.history.HistoryScreen
import net.loeu.wallybudget.ui.screens.home.HomeScreen
import net.loeu.wallybudget.ui.screens.home.PortfolioScreen
import net.loeu.wallybudget.ui.screens.settings.SettingsScreen
import java.time.LocalDate

internal fun NavGraphBuilder.addHomeDestination(
    navController: NavHostController,
    bucketSummaries: List<BucketSummaryState>,
    selectedBucketOverview: SelectedBucketOverview,
    allBuckets: List<BudgetBucket>,
    userSettings: UserSettings,
    effectiveCurrentDate: LocalDate,
    spendingForecast: SpendingForecast?,
    isHomeDataLoading: Boolean,
    onSelectBucket: (String) -> Unit,
    onSavePortfolioPlan: (Long, List<BucketDraft>) -> Unit,
    onAddExpense: (String, Long, String, ExpenseCategory?, LocalDate) -> Unit,
    onRestoreExpense: (Expense) -> Unit,
    onUpdateExpense: (Expense) -> Unit,
    onDeleteExpense: (Expense) -> Unit,
    showAddExpenseSheet: Boolean,
    onShowAddExpenseSheet: () -> Unit,
    onHideAddExpenseSheet: () -> Unit,
    settingsMessage: String?,
    onSettingsMessageConsumed: () -> Unit,
    timelineLockReason: String?,
    usesVerticalNavigation: Boolean
) {
    composable(Screen.Home.route) {
        HomeScreen(
            bucketSummaries = bucketSummaries,
            selectedBucketOverview = selectedBucketOverview,
            allBuckets = allBuckets,
            userSettings = userSettings,
            currentDate = effectiveCurrentDate,
            spendingForecast = spendingForecast,
            isLoadingData = isHomeDataLoading,
            onSelectBucket = onSelectBucket,
            onSavePortfolioPlan = onSavePortfolioPlan,
            onAddExpense = onAddExpense,
            onRestoreExpense = onRestoreExpense,
            onUpdateExpense = onUpdateExpense,
            onDeleteExpense = onDeleteExpense,
            onNavigateToAnalysis = { navController.navigate(Screen.Analysis.route) },
            showTopRightSettingsAction = !usesVerticalNavigation,
            showAddExpenseSheet = showAddExpenseSheet,
            onShowAddExpenseSheet = onShowAddExpenseSheet,
            onHideAddExpenseSheet = onHideAddExpenseSheet,
            settingsMessage = settingsMessage,
            onSettingsMessageConsumed = onSettingsMessageConsumed,
            timelineLockReason = timelineLockReason
        )
    }
}

internal fun NavGraphBuilder.addPortfolioDestination(
    navController: NavHostController,
    portfolioState: PortfolioState,
    bucketSummaries: List<BucketSummaryState>,
    allBuckets: List<BudgetBucket>,
    userSettings: UserSettings,
    onSavePortfolioPlan: (Long, List<BucketDraft>) -> Unit,
    timelineLockReason: String?,
    usesVerticalNavigation: Boolean
) {
    composable(Screen.Portfolio.route) {
        PortfolioScreen(
            portfolioState = portfolioState,
            bucketSummaries = bucketSummaries,
            allBuckets = allBuckets,
            userSettings = userSettings,
            onSavePortfolioPlan = onSavePortfolioPlan,
            onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
            showTopRightSettingsAction = !usesVerticalNavigation,
            interactionsEnabled = timelineLockReason == null
        )
    }
}

internal fun NavGraphBuilder.addHistoryDestination(
    navController: NavHostController,
    historySections: List<ExpenseCycleSection>,
    historyBucketNameByUuid: Map<String, String>,
    allBuckets: List<BudgetBucket>,
    selectedBucketUuid: String?,
    onAddExpense: (String, Long, String, ExpenseCategory?, LocalDate) -> Unit,
    onRestoreExpense: (Expense) -> Unit,
    onUpdateExpense: (Expense) -> Unit,
    onDeleteExpense: (Expense) -> Unit,
    timelineLockReason: String?,
    usesVerticalNavigation: Boolean
) {
    composable(Screen.History.route) {
        HistoryScreen(
            historySections = historySections,
            historyBucketNameByUuid = historyBucketNameByUuid,
            allBuckets = allBuckets,
            selectedBucketUuid = selectedBucketUuid,
            onAddExpense = onAddExpense,
            onRestoreExpense = onRestoreExpense,
            onUpdateExpense = onUpdateExpense,
            onDeleteExpense = onDeleteExpense,
            onNavigateToSettings = if (usesVerticalNavigation) null else {
                { navController.navigate(Screen.Settings.route) }
            },
            interactionsEnabled = timelineLockReason == null,
            timelineLockReason = timelineLockReason
        )
    }
}

internal fun NavGraphBuilder.addAnalysisDestination(
    navController: NavHostController,
    selectedBucketOverview: SelectedBucketOverview?,
    budgetState: BudgetState?,
    spendingForecast: SpendingForecast?,
    monthlyHistoryState: StateFlow<List<MonthlyHistory>?>,
    isHomeDataLoading: Boolean,
    timelineLockReason: String?,
    usesVerticalNavigation: Boolean
) {
    composable(Screen.Analysis.route) {
        val monthlyHistory by monthlyHistoryState.collectAsState()
        val isAnalysisLoading = isHomeDataLoading || monthlyHistory == null
        val isReserveBucket = selectedBucketOverview?.bucket?.trackingMode == BucketTrackingMode.CYCLE_RESERVE
        if (!isAnalysisLoading && isReserveBucket) {
            LaunchedEffect(selectedBucketOverview.bucket.bucketUuid) {
                val popped = navController.popBackStack()
                if (!popped) {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            }
            return@composable
        }
        AnalysisScreen(
            budgetState = budgetState,
            spendingForecast = spendingForecast,
            monthlyHistory = monthlyHistory.orEmpty(),
            timelineLockReason = timelineLockReason,
            isLoading = isAnalysisLoading,
            onNavigateBack = { navController.popBackStack() },
            onNavigateToSettings = if (usesVerticalNavigation) null else {
                { navController.navigate(Screen.Settings.route) }
            }
        )
    }
}

internal fun NavGraphBuilder.addSettingsDestination(
    navController: NavHostController,
    userSettings: UserSettings,
    allBuckets: List<BudgetBucket>,
    bucketSummaries: List<BucketSummaryState>,
    currentDate: LocalDate,
    onSavePortfolioPlan: (Long, List<BucketDraft>) -> Unit,
    onSavePayday: (Int) -> Unit,
    onUndoPaydayChange: () -> Unit,
    isSettingsUndoAvailable: Boolean,
    settingsUndoExpiresAtExclusive: LocalDate?,
    onSettingsMessageConsumed: () -> Unit,
    onRequestExportSnapshot: () -> Unit,
    settingsMessage: String?,
    snapshotMessage: String?,
    snapshotErrorMessage: String?,
    isSnapshotBusy: Boolean
) {
    composable(Screen.Settings.route) {
        SettingsScreen(
            userSettings = userSettings,
            allBuckets = allBuckets,
            bucketSummaries = bucketSummaries,
            currentDate = currentDate,
            onSavePortfolioPlan = onSavePortfolioPlan,
            onSavePayday = onSavePayday,
            onUndoPaydayChange = onUndoPaydayChange,
            isSettingsUndoAvailable = isSettingsUndoAvailable,
            settingsUndoExpiresAtExclusive = settingsUndoExpiresAtExclusive,
            onSettingsMessageConsumed = onSettingsMessageConsumed,
            onRequestExportSnapshot = onRequestExportSnapshot,
            settingsMessage = settingsMessage,
            snapshotMessage = snapshotMessage,
            snapshotErrorMessage = snapshotErrorMessage,
            isSnapshotBusy = isSnapshotBusy,
            onNavigateBack = { navController.popBackStack() }
        )
    }
}
