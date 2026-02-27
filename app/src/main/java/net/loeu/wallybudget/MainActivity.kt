package net.loeu.wallybudget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.flow.map
import net.loeu.wallybudget.ui.navigation.Screen
import net.loeu.wallybudget.ui.screens.HistoryScreen
import net.loeu.wallybudget.ui.screens.HomeScreen
import net.loeu.wallybudget.ui.screens.OnboardingScreen
import net.loeu.wallybudget.ui.screens.SettingsScreen
import net.loeu.wallybudget.ui.theme.WallyBudgetTheme
import net.loeu.wallybudget.ui.viewmodel.BudgetViewModel
import net.loeu.wallybudget.ui.viewmodel.BudgetViewModelFactory

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
    val navController = rememberNavController()
    val isOnboardingCompleted by viewModel.userSettingsFlow
        .map { it.isOnboardingCompleted }
        .collectAsState(initial = null)

    if (isOnboardingCompleted == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val onboardingCompleted = isOnboardingCompleted == true
    val userSettings by viewModel.userSettings.collectAsState()
    val budgetState by viewModel.budgetState.collectAsState()
    val todayExpenses by viewModel.todayExpenses.collectAsState()
    val previousCycleExpenses by viewModel.previousCycleExpenses.collectAsState()
    val monthlyHistory by viewModel.monthlyHistory.collectAsState()
    val isAddExpenseSheetVisible by viewModel.isAddExpenseSheetVisible.collectAsState()

    val startDestination = if (onboardingCompleted) {
        Screen.Home.route
    } else {
        Screen.Onboarding.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier.fillMaxSize()
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onComplete = { budget, payday, cycleStartDate, previousExpenses ->
                    viewModel.completeOnboarding(budget, payday, cycleStartDate, previousExpenses)
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                budgetState = budgetState,
                todayExpenses = todayExpenses,
                previousCycleExpenses = previousCycleExpenses,
                onAddExpense = { amount, description, icon, date ->
                    viewModel.addExpense(amount, description, icon, date)
                },
                onUpdateExpense = { expense ->
                    viewModel.updateExpense(expense)
                },
                onDeleteExpense = { expense ->
                    viewModel.deleteExpense(expense)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToHistory = {
                    navController.navigate(Screen.History.route)
                },
                showAddExpenseSheet = isAddExpenseSheetVisible,
                onShowAddExpenseSheet = { viewModel.showAddExpenseSheet() },
                onHideAddExpenseSheet = { viewModel.hideAddExpenseSheet() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                userSettings = userSettings,
                onUpdateBudget = { budget ->
                    viewModel.updateMonthlyBudget(budget)
                },
                onUpdatePayday = { payday ->
                    viewModel.updatePaydayDate(payday)
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(
                monthlyHistory = monthlyHistory,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}