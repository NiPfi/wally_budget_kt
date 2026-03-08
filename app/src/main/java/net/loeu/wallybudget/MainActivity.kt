package net.loeu.wallybudget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.map
import net.loeu.wallybudget.ui.navigation.Screen
import net.loeu.wallybudget.ui.screens.closeout.CycleCloseoutReviewScreen
import net.loeu.wallybudget.ui.screens.closeout.CycleCloseoutScreen
import net.loeu.wallybudget.ui.screens.history.HistoryScreen
import net.loeu.wallybudget.ui.screens.home.AddExpenseSheet
import net.loeu.wallybudget.ui.screens.home.HomeScreen
import net.loeu.wallybudget.ui.screens.onboarding.OnboardingScreen
import net.loeu.wallybudget.ui.screens.settings.SettingsScreen
import net.loeu.wallybudget.ui.theme.WallyBudgetTheme
import net.loeu.wallybudget.ui.viewmodel.BudgetViewModel
import net.loeu.wallybudget.ui.viewmodel.BudgetViewModelFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowSizeClass.Companion.HEIGHT_DP_MEDIUM_LOWER_BOUND
import net.loeu.wallybudget.data.model.recordedDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WallyBudgetTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: BudgetViewModel = viewModel(
                        factory = BudgetViewModelFactory(applicationContext)
                    )
                    BudgetApp(viewModel)
                }
            }
        }
    }
}

@Composable
fun BudgetApp(
    viewModel: BudgetViewModel,
    modifier: Modifier = Modifier
) {
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshCycleState()
        onPauseOrDispose { }
    }

    val onboardingCompletedFlow = remember(viewModel) {
        viewModel.userSettingsFlow.map { it.isOnboardingCompleted }
    }
    val isOnboardingCompleted by onboardingCompletedFlow.collectAsState(initial = null)

    if (isOnboardingCompleted == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val userSettings by viewModel.userSettings.collectAsState()
    val budgetState by viewModel.budgetState.collectAsState()
    val effectiveCurrentDate by viewModel.effectiveCurrentDate.collectAsState()
    val todayExpenses by viewModel.todayExpenses.collectAsState()
    val activeCycleExpenseSections by viewModel.activeCycleExpenseSections.collectAsState()
    val spendingForecast by viewModel.spendingForecast.collectAsState()
    val historySections by viewModel.historySections.collectAsState()
    val pendingCycleCloseoutState by viewModel.pendingCycleCloseoutState.collectAsState()
    val isAddExpenseSheetVisible by viewModel.isAddExpenseSheetVisible.collectAsState()

    when {
        isOnboardingCompleted != true -> {
            OnboardingScreen(
                onComplete = { budgetCents, payday, cycleStartDate, previousExpensesCents ->
                    viewModel.completeOnboarding(budgetCents, payday, cycleStartDate, previousExpensesCents)
                }
            )
        }

        pendingCycleCloseoutState != null -> {
            PendingCycleFlow(
                pendingCycle = pendingCycleCloseoutState!!,
                showAddExpenseSheet = isAddExpenseSheetVisible,
                onShowAddExpenseSheet = { viewModel.showAddExpenseSheet() },
                onHideAddExpenseSheet = { viewModel.hideAddExpenseSheet() },
                onConcludeCycle = { viewModel.concludePendingCycle() },
                onAddExpense = { amountCents, description, icon, date ->
                    viewModel.addExpense(amountCents, description, icon, date)
                },
                onUpdateExpense = { expense -> viewModel.updateExpense(expense) },
                onDeleteExpense = { expense -> viewModel.deleteExpense(expense) },
                modifier = modifier
            )
        }

        else -> {
            MainNavigationShell(
                budgetState = budgetState,
                todayExpenses = todayExpenses,
                effectiveCurrentDate = effectiveCurrentDate,
                activeCycleExpenseSections = activeCycleExpenseSections,
                spendingForecast = spendingForecast,
                historySections = historySections,
                userSettings = userSettings,
                showAddExpenseSheet = isAddExpenseSheetVisible,
                onShowAddExpenseSheet = { viewModel.showAddExpenseSheet() },
                onHideAddExpenseSheet = { viewModel.hideAddExpenseSheet() },
                onAddExpense = { amountCents, description, icon, date ->
                    viewModel.addExpense(amountCents, description, icon, date)
                },
                onUpdateExpense = { expense -> viewModel.updateExpense(expense) },
                onDeleteExpense = { expense -> viewModel.deleteExpense(expense) },
                onRestoreExpense = { expense -> viewModel.restoreDeletedExpense(expense) },
                onUpdateBudget = { budgetCents -> viewModel.updateMonthlyBudget(budgetCents) },
                onUpdatePayday = { payday -> viewModel.updatePaydayDate(payday) },
                modifier = modifier
            )
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun MainNavigationShell(
    budgetState: net.loeu.wallybudget.data.model.BudgetState,
    todayExpenses: List<net.loeu.wallybudget.data.model.Expense>,
    effectiveCurrentDate: LocalDate,
    activeCycleExpenseSections: List<net.loeu.wallybudget.data.model.ExpenseDaySection>,
    spendingForecast: net.loeu.wallybudget.data.model.SpendingForecast,
    historySections: List<net.loeu.wallybudget.data.model.ExpenseCycleSection>,
    userSettings: net.loeu.wallybudget.data.model.UserSettings,
    showAddExpenseSheet: Boolean,
    onShowAddExpenseSheet: () -> Unit,
    onHideAddExpenseSheet: () -> Unit,
    onAddExpense: (Long, String, net.loeu.wallybudget.data.model.ExpenseCategory?, LocalDate) -> Unit,
    onUpdateExpense: (net.loeu.wallybudget.data.model.Expense) -> Unit,
    onDeleteExpense: (net.loeu.wallybudget.data.model.Expense) -> Unit,
    onRestoreExpense: (net.loeu.wallybudget.data.model.Expense) -> Unit,
    onUpdateBudget: (Long) -> Unit,
    onUpdatePayday: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val navigationLayoutType = if (
        !adaptiveInfo.windowSizeClass.isHeightAtLeastBreakpoint(HEIGHT_DP_MEDIUM_LOWER_BOUND)
    ) {
        NavigationSuiteType.WideNavigationRailCollapsed
    } else {
        NavigationSuiteScaffoldDefaults.navigationSuiteType(adaptiveInfo)
    }
    val usesVerticalNavigation = when (navigationLayoutType) {
        NavigationSuiteType.NavigationRail,
        NavigationSuiteType.NavigationDrawer,
        NavigationSuiteType.WideNavigationRailCollapsed,
        NavigationSuiteType.WideNavigationRailExpanded -> true
        else -> false
    }

    NavigationSuiteScaffold(
        modifier = modifier.fillMaxSize(),
        navigationSuiteType = navigationLayoutType,
        navigationItemVerticalArrangement = if (usesVerticalNavigation) {
            Arrangement.SpaceBetween
        } else {
            Arrangement.Top
        },
        navigationItems = {
            if (usesVerticalNavigation) {
                Column {
                    MainNavigationItem(
                        selected = currentRoute == Screen.Home.route,
                        onClick = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Home.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("Home") }
                    )
                    MainNavigationItem(
                        selected = currentRoute == Screen.History.route,
                        onClick = {
                            navController.navigate(Screen.History.route) {
                                popUpTo(Screen.Home.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.History, contentDescription = null) },
                        label = { Text("History") }
                    )
                }
                MainNavigationItem(
                    selected = currentRoute == Screen.Settings.route,
                    onClick = {
                        navController.navigate(Screen.Settings.route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") }
                )
            } else {
                MainNavigationItem(
                    selected = currentRoute == Screen.Home.route,
                    onClick = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Home") }
                )
                MainNavigationItem(
                    selected = currentRoute == Screen.History.route,
                    onClick = {
                        navController.navigate(Screen.History.route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    label = { Text("History") }
                )
            }
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    budgetState = budgetState,
                    todayExpenses = todayExpenses,
                    currentDate = effectiveCurrentDate,
                    activeCycleExpenseSections = activeCycleExpenseSections,
                    historySections = historySections,
                    spendingForecast = spendingForecast,
                    onAddExpense = onAddExpense,
                    onRestoreExpense = onRestoreExpense,
                    onUpdateExpense = onUpdateExpense,
                    onDeleteExpense = onDeleteExpense,
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    showTopRightSettingsAction = !usesVerticalNavigation,
                    showAddExpenseSheet = showAddExpenseSheet,
                    onShowAddExpenseSheet = onShowAddExpenseSheet,
                    onHideAddExpenseSheet = onHideAddExpenseSheet
                )
            }
            composable(Screen.History.route) {
                HistoryScreen(
                    historySections = historySections,
                    onAddExpense = onAddExpense,
                    onRestoreExpense = onRestoreExpense,
                    onUpdateExpense = onUpdateExpense,
                    onDeleteExpense = onDeleteExpense,
                    onNavigateToSettings = if (usesVerticalNavigation) null else {
                        { navController.navigate(Screen.Settings.route) }
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    userSettings = userSettings,
                    onUpdateBudget = onUpdateBudget,
                    onUpdatePayday = onUpdatePayday,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun MainNavigationItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: @Composable () -> Unit
) {
    NavigationSuiteItem(
        selected = selected,
        onClick = onClick,
        icon = icon,
        label = label
    )
}

@Composable
private fun PendingCycleFlow(
    pendingCycle: net.loeu.wallybudget.data.model.PendingCycleCloseoutState,
    showAddExpenseSheet: Boolean,
    onShowAddExpenseSheet: () -> Unit,
    onHideAddExpenseSheet: () -> Unit,
    onConcludeCycle: () -> Unit,
    onAddExpense: (Long, String, net.loeu.wallybudget.data.model.ExpenseCategory?, LocalDate) -> Unit,
    onUpdateExpense: (net.loeu.wallybudget.data.model.Expense) -> Unit,
    onDeleteExpense: (net.loeu.wallybudget.data.model.Expense) -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    var selectedDate by rememberSaveable { androidx.compose.runtime.mutableStateOf(pendingCycle.cycleEndDateExclusive.minusDays(1)) }
    var expenseBeingEdited by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<net.loeu.wallybudget.data.model.Expense?>(null) }

    NavHost(
        navController = navController,
        startDestination = Screen.CycleCloseout.route,
        modifier = modifier.fillMaxSize()
    ) {
        composable(Screen.CycleCloseout.route) {
            CycleCloseoutScreen(
                pendingCycle = pendingCycle,
                onReviewCycle = { navController.navigate(Screen.CycleCloseoutReview.route) },
                onConcludeCycle = onConcludeCycle
            )
        }
        composable(Screen.CycleCloseoutReview.route) {
            CycleCloseoutReviewScreen(
                pendingCycle = pendingCycle,
                onNavigateBack = { navController.popBackStack() },
                onEditExpense = { expenseBeingEdited = it },
                onAddExpenseForDate = { date ->
                    selectedDate = date
                    onShowAddExpenseSheet()
                }
            )
        }
    }

    if (showAddExpenseSheet) {
        AddExpenseSheet(
            onDismiss = onHideAddExpenseSheet,
            onSubmitExpense = { amountCents, description, icon ->
                onAddExpense(amountCents, description, icon, selectedDate)
            },
            title = "Add expense for ${selectedDate.format(DateTimeFormatter.ofPattern("MMM d"))}",
            confirmButtonText = "Add to ${selectedDate.format(DateTimeFormatter.ofPattern("MMM d"))}",
            dateLabel = "Recorded for ${selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))}"
        )
    }

    expenseBeingEdited?.let { editingExpense ->
        AddExpenseSheet(
            onDismiss = { expenseBeingEdited = null },
            onSubmitExpense = { amountCents, description, icon ->
                onUpdateExpense(
                    editingExpense.copy(
                        amountCents = amountCents,
                        description = description,
                        icon = icon
                    )
                )
                expenseBeingEdited = null
            },
            onDeleteExpense = {
                onDeleteExpense(editingExpense)
                expenseBeingEdited = null
            },
            title = "Edit cycle expense",
            confirmButtonText = "Save changes",
            dateLabel = "Recorded for ${editingExpense.recordedDate().format(DateTimeFormatter.ofPattern("EEEE, MMM d"))}",
            initialAmountCents = editingExpense.amountCents,
            initialDescription = editingExpense.description,
            initialIcon = editingExpense.icon
        )
    }
}
