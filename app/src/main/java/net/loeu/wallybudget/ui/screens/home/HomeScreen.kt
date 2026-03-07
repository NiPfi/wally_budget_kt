package net.loeu.wallybudget.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.loeu.wallybudget.data.model.BudgetState
import net.loeu.wallybudget.data.model.Expense
import net.loeu.wallybudget.data.model.ExpenseCategory
import net.loeu.wallybudget.data.model.ExpenseCycleSection
import net.loeu.wallybudget.data.model.ExpenseDaySection
import net.loeu.wallybudget.data.model.SpendingForecast
import net.loeu.wallybudget.ui.screens.history.HistoryScreen
import net.loeu.wallybudget.ui.screens.overview.OverviewPage
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun HomeScreen(
    budgetState: BudgetState,
    todayExpenses: List<Expense>,
    activeCycleExpenseSections: List<ExpenseDaySection>,
    historySections: List<ExpenseCycleSection>,
    spendingForecast: SpendingForecast,
    onAddExpense: (Long, String, ExpenseCategory?, LocalDate) -> Unit,
    onRestoreExpense: (Expense) -> Unit,
    onUpdateExpense: (Expense) -> Unit,
    onDeleteExpense: (Expense) -> Unit,
    onNavigateToSettings: () -> Unit,
    showTopRightSettingsAction: Boolean,
    showAddExpenseSheet: Boolean,
    onShowAddExpenseSheet: () -> Unit,
    onHideAddExpenseSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val showLedgerPane = windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)
    val selectedDateForExpenseEpochDay = rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var expenseBeingEdited by remember { mutableStateOf<Expense?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    selectedDateForExpenseEpochDay.longValue = LocalDate.now().toEpochDay()
                    onShowAddExpenseSheet()
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add today’s expense")
            }
        }
    ) { paddingValues ->
        if (showLedgerPane) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OverviewPage(
                    budgetState = budgetState,
                    todayExpenses = todayExpenses,
                    activeCycleExpenseSections = activeCycleExpenseSections,
                    spendingForecast = spendingForecast,
                    onEditTodayExpense = { expenseBeingEdited = it },
                    onNavigateToSettings = if (showTopRightSettingsAction) onNavigateToSettings else null,
                    showTodayExpensesSection = false,
                    enableHeaderCollapse = false,
                    modifier = Modifier.weight(1.08f)
                )
                HistoryScreen(
                    historySections = historySections,
                    onAddExpense = onAddExpense,
                    onRestoreExpense = onRestoreExpense,
                    onUpdateExpense = onUpdateExpense,
                    onDeleteExpense = onDeleteExpense,
                    modifier = Modifier.weight(0.92f),
                    embedded = true
                )
            }
        } else {
            OverviewPage(
                budgetState = budgetState,
                todayExpenses = todayExpenses,
                activeCycleExpenseSections = activeCycleExpenseSections,
                spendingForecast = spendingForecast,
                onEditTodayExpense = { expenseBeingEdited = it },
                onNavigateToSettings = if (showTopRightSettingsAction) onNavigateToSettings else null,
                enableHeaderCollapse = true,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        }
    }

    if (showAddExpenseSheet) {
        val selectedDate = LocalDate.ofEpochDay(selectedDateForExpenseEpochDay.longValue)
        AddExpenseSheet(
            onDismiss = onHideAddExpenseSheet,
            onSubmitExpense = { amountCents, description, icon ->
                onAddExpense(amountCents, description, icon, selectedDate)
            },
            title = if (selectedDate == LocalDate.now()) {
                "Add expense"
            } else {
                "Add expense for ${selectedDate.format(DateTimeFormatter.ofPattern("MMM d"))}"
            },
            confirmButtonText = if (selectedDate == LocalDate.now()) {
                "Add expense"
            } else {
                "Add to ${selectedDate.format(DateTimeFormatter.ofPattern("MMM d"))}"
            },
            dateLabel = if (selectedDate == LocalDate.now()) {
                "Recorded for today"
            } else {
                "Recorded for ${selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))}"
            }
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
                scope.launch {
                    val dismissJob = launch {
                        delay(5000)
                        snackbarHostState.currentSnackbarData?.dismiss()
                    }
                    val result = snackbarHostState.showSnackbar(
                        message = if (editingExpense.description.isNotBlank()) {
                            "Deleted \"${editingExpense.description}\""
                        } else {
                            "Deleted expense"
                        },
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        dismissJob.cancel()
                        onRestoreExpense(editingExpense)
                    }
                }
                expenseBeingEdited = null
            },
            title = "Edit expense",
            confirmButtonText = "Save changes",
            dateLabel = "Recorded for ${
                java.time.Instant.ofEpochMilli(editingExpense.timestamp)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
                    .format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
            }",
            initialAmountCents = editingExpense.amountCents,
            initialDescription = editingExpense.description,
            initialIcon = editingExpense.icon
        )
    }
}
