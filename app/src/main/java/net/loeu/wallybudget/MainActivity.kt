@file:Suppress("TooManyFunctions")

package net.loeu.wallybudget

import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import net.loeu.wallybudget.ui.navigation.Screen
import net.loeu.wallybudget.ui.screens.analysis.AnalysisScreen
import net.loeu.wallybudget.ui.screens.closeout.CycleCloseoutReviewScreen
import net.loeu.wallybudget.ui.screens.closeout.CycleCloseoutScreen
import net.loeu.wallybudget.ui.screens.history.HistoryScreen
import net.loeu.wallybudget.ui.screens.home.AddExpenseSheet
import net.loeu.wallybudget.ui.screens.home.HomeScreen
import net.loeu.wallybudget.ui.screens.onboarding.OnboardingScreen
import net.loeu.wallybudget.ui.screens.overview.PlaceholderShimmerProvider
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
import androidx.window.core.layout.WindowSizeClass.Companion.HEIGHT_DP_MEDIUM_LOWER_BOUND
import net.loeu.wallybudget.domain.model.BudgetChangeMode
import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.ExpenseCategory
import net.loeu.wallybudget.domain.model.ExpenseCycleSection
import net.loeu.wallybudget.domain.model.ExpenseDaySection
import net.loeu.wallybudget.domain.model.MonthlyHistory
import net.loeu.wallybudget.domain.model.PendingCycleCloseoutState
import net.loeu.wallybudget.domain.model.SnapshotError
import net.loeu.wallybudget.domain.model.SpendingForecast
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.model.recordedDate

private const val OVERVIEW_NAVIGATION_LABEL = "Overview"

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
@Suppress("LongMethod")
fun BudgetApp(
    viewModel: BudgetViewModel,
    modifier: Modifier = Modifier
) {
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshCycleState()
        onPauseOrDispose { }
    }
    val appState = rememberBudgetAppUiState(viewModel)
    val openSnapshotDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(viewModel::prepareSnapshotImport)
    }
    val createSnapshotDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/gzip")
    ) { uri ->
        uri?.let(viewModel::exportSnapshot)
    }
    val snapshotErrorMessage = appState.snapshotError?.toDisplayMessage()
    if (appState.isOnboardingCompleted == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val displayBudgetState = appState.budgetState ?: loadingBudgetState(appState.effectiveCurrentDate)
    val displaySpendingForecast = appState.spendingForecast ?: SpendingForecast()
    when {
        appState.isOnboardingCompleted != true -> OnboardingScreen(
            onComplete = viewModel::completeOnboarding,
            onRequestRestoreSnapshot = { openSnapshotDocument.launch(arrayOf("*/*")) },
            onApplySnapshotRestore = viewModel::applyPreparedSnapshotImport,
            onDismissSnapshotPreview = viewModel::clearPreparedSnapshotImport,
            snapshotPreview = appState.snapshotImportPreview,
            snapshotErrorMessage = snapshotErrorMessage,
            snapshotStatusMessage = appState.snapshotStatusMessage,
            isSnapshotBusy = appState.snapshotBusy
        )
        appState.pendingCycleCloseoutState != null -> {
            PendingCycleFlow(
                pendingCycle = appState.pendingCycleCloseoutState,
                showAddExpenseSheet = appState.isAddExpenseSheetVisible,
                onShowAddExpenseSheet = viewModel::showAddExpenseSheet,
                onHideAddExpenseSheet = viewModel::hideAddExpenseSheet,
                onConcludeCycle = viewModel::concludePendingCycle,
                onAddExpense = viewModel::addExpense,
                onUpdateExpense = viewModel::updateExpense,
                onDeleteExpense = viewModel::deleteExpense,
                modifier = modifier
            )
        }

        else -> {
            val shellContent: @Composable () -> Unit = {
                MainNavigationShell(
                    budgetState = displayBudgetState,
                    todayExpenses = appState.todayExpenses,
                    effectiveCurrentDate = appState.effectiveCurrentDate,
                    activeCycleExpenseSections = appState.activeCycleExpenseSections,
                    spendingForecast = displaySpendingForecast,
                    monthlyHistoryState = viewModel.monthlyHistory,
                    isHomeDataLoading = appState.isHomeDataLoading,
                    historySections = appState.historySections,
                    userSettings = appState.userSettings,
                    showAddExpenseSheet = appState.isAddExpenseSheetVisible,
                    onShowAddExpenseSheet = viewModel::showAddExpenseSheet,
                    onHideAddExpenseSheet = viewModel::hideAddExpenseSheet,
                    onAddExpense = viewModel::addExpense,
                    onUpdateExpense = viewModel::updateExpense,
                    onDeleteExpense = viewModel::deleteExpense,
                    onRestoreExpense = viewModel::restoreDeletedExpense,
                    onSaveSettings = viewModel::updateBudgetSettings,
                    onRequestExportSnapshot = {
                        createSnapshotDocument.launch(defaultSnapshotFilename(appState.effectiveCurrentDate))
                    },
                    settingsMessage = appState.settingsStatusMessage,
                    snapshotMessage = appState.snapshotStatusMessage,
                    snapshotErrorMessage = snapshotErrorMessage,
                    isSnapshotBusy = appState.snapshotBusy,
                    timelineLockReason = appState.timelineLockReason,
                    modifier = modifier
                )
            }
            if (appState.isHomeDataLoading) PlaceholderShimmerProvider(content = shellContent) else shellContent()
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun MainNavigationShell(
    budgetState: BudgetState,
    todayExpenses: List<Expense>,
    effectiveCurrentDate: LocalDate,
    activeCycleExpenseSections: List<ExpenseDaySection>,
    spendingForecast: SpendingForecast,
    monthlyHistoryState: StateFlow<List<MonthlyHistory>?>,
    isHomeDataLoading: Boolean,
    historySections: List<ExpenseCycleSection>,
    userSettings: UserSettings,
    showAddExpenseSheet: Boolean,
    onShowAddExpenseSheet: () -> Unit,
    onHideAddExpenseSheet: () -> Unit,
    onAddExpense: (Long, String, ExpenseCategory?, LocalDate) -> Unit,
    onUpdateExpense: (Expense) -> Unit,
    onDeleteExpense: (Expense) -> Unit,
    onRestoreExpense: (Expense) -> Unit,
    onSaveSettings: (Long, Int, BudgetChangeMode) -> Unit,
    onRequestExportSnapshot: () -> Unit,
    settingsMessage: String?,
    snapshotMessage: String?,
    snapshotErrorMessage: String?,
    isSnapshotBusy: Boolean,
    timelineLockReason: String?,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val navigationLayoutType = mainNavigationLayoutType()
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
            MainNavigationItems(
                currentRoute = currentRoute,
                usesVerticalNavigation = usesVerticalNavigation,
                onNavigate = navController::navigateToTopLevel
            )
        }
    ) {
        MainNavigationHost(
            navController = navController,
            budgetState = budgetState,
            todayExpenses = todayExpenses,
            effectiveCurrentDate = effectiveCurrentDate,
            activeCycleExpenseSections = activeCycleExpenseSections,
            spendingForecast = spendingForecast,
            monthlyHistoryState = monthlyHistoryState,
            isHomeDataLoading = isHomeDataLoading,
            historySections = historySections,
            userSettings = userSettings,
            showAddExpenseSheet = showAddExpenseSheet,
            onShowAddExpenseSheet = onShowAddExpenseSheet,
            onHideAddExpenseSheet = onHideAddExpenseSheet,
            onAddExpense = onAddExpense,
            onUpdateExpense = onUpdateExpense,
            onDeleteExpense = onDeleteExpense,
            onRestoreExpense = onRestoreExpense,
            onSaveSettings = onSaveSettings,
            onRequestExportSnapshot = onRequestExportSnapshot,
            settingsMessage = settingsMessage,
            snapshotMessage = snapshotMessage,
            snapshotErrorMessage = snapshotErrorMessage,
            isSnapshotBusy = isSnapshotBusy,
            timelineLockReason = timelineLockReason,
            usesVerticalNavigation = usesVerticalNavigation
        )
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun mainNavigationLayoutType(): NavigationSuiteType {
    val adaptiveInfo = currentWindowAdaptiveInfo()
    return if (
        !adaptiveInfo.windowSizeClass.isHeightAtLeastBreakpoint(HEIGHT_DP_MEDIUM_LOWER_BOUND)
    ) {
        NavigationSuiteType.WideNavigationRailCollapsed
    } else {
        NavigationSuiteScaffoldDefaults.navigationSuiteType(adaptiveInfo)
    }
}

@Composable
private fun MainNavigationItems(
    currentRoute: String?,
    usesVerticalNavigation: Boolean,
    onNavigate: (Screen) -> Unit
) {
    val primaryScreens = listOf(Screen.Home, Screen.Analysis, Screen.History)
    if (usesVerticalNavigation) {
        Column {
            primaryScreens.forEach { screen ->
                MainTopLevelNavigationItem(
                    screen = screen,
                    selected = currentRoute == screen.route,
                    onClick = { onNavigate(screen) }
                )
            }
        }
        MainTopLevelNavigationItem(
            screen = Screen.Settings,
            selected = currentRoute == Screen.Settings.route,
            onClick = { onNavigate(Screen.Settings) }
        )
    } else {
        primaryScreens.forEach { screen ->
            MainTopLevelNavigationItem(
                screen = screen,
                selected = currentRoute == screen.route,
                onClick = { onNavigate(screen) }
            )
        }
    }
}

@Composable
private fun MainTopLevelNavigationItem(
    screen: Screen,
    selected: Boolean,
    onClick: () -> Unit
) {
    val (iconRes, label) = when (screen) {
        Screen.Home -> R.drawable.ic_dashboard to OVERVIEW_NAVIGATION_LABEL
        Screen.Analysis -> R.drawable.ic_analytics to "Analysis"
        Screen.History -> R.drawable.ic_history to "History"
        Screen.Settings -> R.drawable.ic_settings to "Settings"
        else -> R.drawable.ic_dashboard to OVERVIEW_NAVIGATION_LABEL
    }
    MainNavigationItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null
            )
        },
        label = { Text(label) }
    )
}

private fun NavHostController.navigateToTopLevel(screen: Screen) {
    navigate(screen.route) {
        popUpTo(Screen.Home.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun MainNavigationHost(
    navController: NavHostController,
    budgetState: BudgetState,
    todayExpenses: List<Expense>,
    effectiveCurrentDate: LocalDate,
    activeCycleExpenseSections: List<ExpenseDaySection>,
    spendingForecast: SpendingForecast,
    monthlyHistoryState: StateFlow<List<MonthlyHistory>?>,
    isHomeDataLoading: Boolean,
    historySections: List<ExpenseCycleSection>,
    userSettings: UserSettings,
    showAddExpenseSheet: Boolean,
    onShowAddExpenseSheet: () -> Unit,
    onHideAddExpenseSheet: () -> Unit,
    onAddExpense: (Long, String, ExpenseCategory?, LocalDate) -> Unit,
    onUpdateExpense: (Expense) -> Unit,
    onDeleteExpense: (Expense) -> Unit,
    onRestoreExpense: (Expense) -> Unit,
    onSaveSettings: (Long, Int, BudgetChangeMode) -> Unit,
    onRequestExportSnapshot: () -> Unit,
    settingsMessage: String?,
    snapshotMessage: String?,
    snapshotErrorMessage: String?,
    isSnapshotBusy: Boolean,
    timelineLockReason: String?,
    usesVerticalNavigation: Boolean
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        addHomeDestination(
            navController = navController,
            budgetState = budgetState,
            todayExpenses = todayExpenses,
            effectiveCurrentDate = effectiveCurrentDate,
            activeCycleExpenseSections = activeCycleExpenseSections,
            spendingForecast = spendingForecast,
            isHomeDataLoading = isHomeDataLoading,
            onAddExpense = onAddExpense,
            onRestoreExpense = onRestoreExpense,
            onUpdateExpense = onUpdateExpense,
            onDeleteExpense = onDeleteExpense,
            showAddExpenseSheet = showAddExpenseSheet,
            onShowAddExpenseSheet = onShowAddExpenseSheet,
            onHideAddExpenseSheet = onHideAddExpenseSheet,
            timelineLockReason = timelineLockReason,
            usesVerticalNavigation = usesVerticalNavigation
        )
        addHistoryDestination(
            navController = navController,
            historySections = historySections,
            onAddExpense = onAddExpense,
            onRestoreExpense = onRestoreExpense,
            onUpdateExpense = onUpdateExpense,
            onDeleteExpense = onDeleteExpense,
            timelineLockReason = timelineLockReason,
            usesVerticalNavigation = usesVerticalNavigation
        )
        addAnalysisDestination(
            navController = navController,
            budgetState = budgetState,
            spendingForecast = spendingForecast,
            monthlyHistoryState = monthlyHistoryState,
            isHomeDataLoading = isHomeDataLoading,
            timelineLockReason = timelineLockReason,
            usesVerticalNavigation = usesVerticalNavigation
        )
        addSettingsDestination(
            navController = navController,
            userSettings = userSettings,
            currentDate = effectiveCurrentDate,
            onSaveSettings = onSaveSettings,
            onRequestExportSnapshot = onRequestExportSnapshot,
            settingsMessage = settingsMessage,
            snapshotMessage = snapshotMessage,
            snapshotErrorMessage = snapshotErrorMessage,
            isSnapshotBusy = isSnapshotBusy
        )
    }
}

private fun loadingBudgetState(currentDate: LocalDate): BudgetState {
    return BudgetState(
        monthlyBudgetCents = 1_000_00L,
        totalSpentThisCycleCents = 0L,
        dailyBudgetCents = 35_00L,
        spentTodayCents = 0L,
        remainingTodayCents = 35_00L,
        daysRemainingInCycle = 12,
        cumulativeSavingsCents = 0L,
        paydayDate = currentDate.dayOfMonth.coerceAtLeast(1),
        cycleStartDate = currentDate.minusDays(18)
    )
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
    pendingCycle: PendingCycleCloseoutState,
    showAddExpenseSheet: Boolean,
    onShowAddExpenseSheet: () -> Unit,
    onHideAddExpenseSheet: () -> Unit,
    onConcludeCycle: () -> Unit,
    onAddExpense: (Long, String, ExpenseCategory?, LocalDate) -> Unit,
    onUpdateExpense: (Expense) -> Unit,
    onDeleteExpense: (Expense) -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    var selectedDate by rememberSaveable { mutableStateOf(pendingCycle.cycleEndDateExclusive.minusDays(1)) }
    var expenseBeingEdited by remember { mutableStateOf<Expense?>(null) }
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
    PendingCycleExpenseSheets(
        showAddExpenseSheet = showAddExpenseSheet,
        selectedDate = selectedDate,
        onHideAddExpenseSheet = onHideAddExpenseSheet,
        onAddExpense = onAddExpense,
        expenseBeingEdited = expenseBeingEdited,
        onDismissExpenseEditor = { expenseBeingEdited = null },
        onUpdateExpense = onUpdateExpense,
        onDeleteExpense = onDeleteExpense
    )
}

private fun SnapshotError.toDisplayMessage(): String {
    return when (this) {
        SnapshotError.UnsupportedSchemaVersion ->
            "This snapshot was created by a newer app version and cannot be restored here."
        SnapshotError.MalformedSnapshot ->
            "The selected file is not a valid WallyBudget snapshot."
        SnapshotError.InvalidCompression ->
            "The selected file could not be decompressed."
        SnapshotError.NonEmptyProfileRestoreBlocked ->
            "Restore only works before setup is complete and before any local data exists."
        SnapshotError.OnboardingCompletedRestoreBlocked ->
            "Restore only works before setup is complete."
        is SnapshotError.IoFailure -> message
    }
}

private fun defaultSnapshotFilename(currentDate: LocalDate): String {
    return "wallybudget-snapshot-${currentDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"))}.json.gz"
}
