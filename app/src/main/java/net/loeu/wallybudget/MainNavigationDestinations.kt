package net.loeu.wallybudget

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import kotlinx.coroutines.flow.StateFlow
import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.ExpenseCategory
import net.loeu.wallybudget.domain.model.ExpenseCycleSection
import net.loeu.wallybudget.domain.model.ExpenseDaySection
import net.loeu.wallybudget.domain.model.MonthlyHistory
import net.loeu.wallybudget.domain.model.SpendingForecast
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.model.BudgetChangeMode
import net.loeu.wallybudget.ui.navigation.Screen
import net.loeu.wallybudget.ui.screens.analysis.AnalysisScreen
import net.loeu.wallybudget.ui.screens.history.HistoryScreen
import net.loeu.wallybudget.ui.screens.home.HomeScreen
import net.loeu.wallybudget.ui.screens.settings.SettingsScreen
import java.time.LocalDate

internal fun NavGraphBuilder.addHomeDestination(
    navController: NavHostController,
    budgetState: BudgetState,
    todayExpenses: List<Expense>,
    effectiveCurrentDate: LocalDate,
    activeCycleExpenseSections: List<ExpenseDaySection>,
    spendingForecast: SpendingForecast,
    isHomeDataLoading: Boolean,
    onAddExpense: (Long, String, ExpenseCategory?, LocalDate) -> Unit,
    onRestoreExpense: (Expense) -> Unit,
    onUpdateExpense: (Expense) -> Unit,
    onDeleteExpense: (Expense) -> Unit,
    showAddExpenseSheet: Boolean,
    onShowAddExpenseSheet: () -> Unit,
    onHideAddExpenseSheet: () -> Unit,
    timelineLockReason: String?,
    usesVerticalNavigation: Boolean
) {
    composable(Screen.Home.route) {
        HomeScreen(
            budgetState = budgetState,
            todayExpenses = todayExpenses,
            currentDate = effectiveCurrentDate,
            activeCycleExpenseSections = activeCycleExpenseSections,
            spendingForecast = spendingForecast,
            isLoadingData = isHomeDataLoading,
            onAddExpense = onAddExpense,
            onRestoreExpense = onRestoreExpense,
            onUpdateExpense = onUpdateExpense,
            onDeleteExpense = onDeleteExpense,
            onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
            showTopRightSettingsAction = !usesVerticalNavigation,
            showAddExpenseSheet = showAddExpenseSheet,
            onShowAddExpenseSheet = onShowAddExpenseSheet,
            onHideAddExpenseSheet = onHideAddExpenseSheet,
            timelineLockReason = timelineLockReason
        )
    }
}

internal fun NavGraphBuilder.addHistoryDestination(
    navController: NavHostController,
    historySections: List<ExpenseCycleSection>,
    onAddExpense: (Long, String, ExpenseCategory?, LocalDate) -> Unit,
    onRestoreExpense: (Expense) -> Unit,
    onUpdateExpense: (Expense) -> Unit,
    onDeleteExpense: (Expense) -> Unit,
    timelineLockReason: String?,
    usesVerticalNavigation: Boolean
) {
    composable(Screen.History.route) {
        HistoryScreen(
            historySections = historySections,
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
    budgetState: BudgetState,
    spendingForecast: SpendingForecast,
    monthlyHistoryState: StateFlow<List<MonthlyHistory>?>,
    isHomeDataLoading: Boolean,
    timelineLockReason: String?,
    usesVerticalNavigation: Boolean
) {
    composable(Screen.Analysis.route) {
        val monthlyHistory by monthlyHistoryState.collectAsState()
        val isAnalysisLoading = isHomeDataLoading || monthlyHistory == null
        AnalysisScreen(
            budgetState = budgetState,
            spendingForecast = spendingForecast,
            monthlyHistory = monthlyHistory.orEmpty(),
            timelineLockReason = timelineLockReason,
            isLoading = isAnalysisLoading,
            onNavigateToSettings = if (usesVerticalNavigation) null else {
                { navController.navigate(Screen.Settings.route) }
            }
        )
    }
}

internal fun NavGraphBuilder.addSettingsDestination(
    navController: NavHostController,
    userSettings: UserSettings,
    budgetState: BudgetState,
    currentDate: LocalDate,
    onSaveSettings: (Long, Int, BudgetChangeMode) -> Unit,
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
            budgetState = budgetState,
            currentDate = currentDate,
            onSaveSettings = onSaveSettings,
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
