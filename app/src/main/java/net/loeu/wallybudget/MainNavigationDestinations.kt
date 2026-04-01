package net.loeu.wallybudget

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import kotlinx.coroutines.flow.StateFlow
import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.domain.model.BucketSummaryState
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.ExpenseCategory
import net.loeu.wallybudget.domain.model.ExpenseCycleSection
import net.loeu.wallybudget.domain.model.Fund
import net.loeu.wallybudget.domain.model.MonthlyHistory
import net.loeu.wallybudget.domain.model.PortfolioState
import net.loeu.wallybudget.domain.model.SelectedBucketOverview
import net.loeu.wallybudget.domain.model.SpendingForecast
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.usecase.BucketDraft
import net.loeu.wallybudget.ui.navigation.AnalysisRoute
import net.loeu.wallybudget.ui.navigation.FundsRoute
import net.loeu.wallybudget.ui.navigation.HistoryRoute
import net.loeu.wallybudget.ui.navigation.HomeRoute
import net.loeu.wallybudget.ui.navigation.PortfolioRoute
import net.loeu.wallybudget.ui.navigation.SettingsRoute
import net.loeu.wallybudget.ui.screens.analysis.AnalysisScreen
import net.loeu.wallybudget.ui.screens.funds.FundsScreen
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
    composable<HomeRoute> {
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
            onNavigateToAnalysis = { navController.navigate(AnalysisRoute) },
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
    funds: List<Fund>,
    allBuckets: List<BudgetBucket>,
    userSettings: UserSettings,
    onSavePortfolioPlan: (Long, List<BucketDraft>) -> Unit,
    timelineLockReason: String?,
    usesVerticalNavigation: Boolean
) {
    composable<PortfolioRoute> {
        PortfolioScreen(
            portfolioState = portfolioState,
            bucketSummaries = bucketSummaries,
            funds = funds,
            allBuckets = allBuckets,
            userSettings = userSettings,
            onSavePortfolioPlan = onSavePortfolioPlan,
            onNavigateToFunds = { navController.navigate(FundsRoute) },
            onNavigateToSettings = { navController.navigate(SettingsRoute) },
            showTopRightSettingsAction = !usesVerticalNavigation,
            interactionsEnabled = timelineLockReason == null
        )
    }
}

internal fun NavGraphBuilder.addFundsDestination(
    navController: NavHostController,
    funds: List<Fund>,
    onCreateGoalFund: suspend (String, Long) -> Unit,
    onUpdateGoalFund: suspend (String, String, Long) -> Unit
) {
    composable<FundsRoute> {
        FundsScreen(
            funds = funds,
            onNavigateBack = { navController.popBackStack() },
            onCreateGoalFund = onCreateGoalFund,
            onUpdateGoalFund = onUpdateGoalFund
        )
    }
}

internal fun NavGraphBuilder.addHistoryDestination(
    navController: NavHostController,
    historySections: List<ExpenseCycleSection>,
    historyBucketNameByUuid: Map<String, String>,
    timelineLockReason: String?,
    usesVerticalNavigation: Boolean
) {
    composable<HistoryRoute> {
        HistoryScreen(
            historySections = historySections,
            historyBucketNameByUuid = historyBucketNameByUuid,
            onNavigateToSettings = if (usesVerticalNavigation) null else {
                { navController.navigate(SettingsRoute) }
            },
            interactionsEnabled = timelineLockReason == null,
            timelineLockReason = timelineLockReason
        )
    }
}

internal fun NavGraphBuilder.addAnalysisDestination(
    navController: NavHostController,
    @Suppress("UNUSED_PARAMETER")
    selectedBucketOverview: SelectedBucketOverview?,
    effectiveCurrentDate: LocalDate,
    budgetState: BudgetState?,
    spendingForecast: SpendingForecast?,
    monthlyHistoryState: StateFlow<List<MonthlyHistory>?>,
    isHomeDataLoading: Boolean,
    timelineLockReason: String?,
    usesVerticalNavigation: Boolean
) {
    composable<AnalysisRoute> {
        val monthlyHistory by monthlyHistoryState.collectAsState()
        AnalysisScreen(
            budgetState = budgetState,
            spendingForecast = spendingForecast,
            monthlyHistory = monthlyHistory.orEmpty(),
            effectiveCurrentDate = effectiveCurrentDate,
            timelineLockReason = timelineLockReason,
            isLoading = isHomeDataLoading || monthlyHistory == null,
            onNavigateBack = { navController.popBackStack() },
            onNavigateToSettings = if (usesVerticalNavigation) null else {
                { navController.navigate(SettingsRoute) }
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
    isPaydayUndoAvailable: Boolean,
    paydayUndoExpiresAtExclusive: LocalDate?,
    onSettingsMessageConsumed: () -> Unit,
    onRequestExportSnapshot: () -> Unit,
    settingsMessage: String?,
    snapshotMessage: String?,
    snapshotErrorMessage: String?,
    isSnapshotBusy: Boolean
) {
    composable<SettingsRoute> {
        SettingsScreen(
            userSettings = userSettings,
            allBuckets = allBuckets,
            bucketSummaries = bucketSummaries,
            currentDate = currentDate,
            onSavePortfolioPlan = onSavePortfolioPlan,
            onSavePayday = onSavePayday,
            onUndoPaydayChange = onUndoPaydayChange,
            isPaydayUndoAvailable = isPaydayUndoAvailable,
            paydayUndoExpiresAtExclusive = paydayUndoExpiresAtExclusive,
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
