package net.loeu.wallybudget.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.window.core.layout.WindowSizeClass.Companion.HEIGHT_DP_MEDIUM_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.loeu.wallybudget.data.model.BudgetState
import net.loeu.wallybudget.data.model.Expense
import net.loeu.wallybudget.data.model.ExpenseCategory
import net.loeu.wallybudget.data.model.ExpenseCycleSection
import net.loeu.wallybudget.data.model.ExpenseDaySection
import net.loeu.wallybudget.data.model.SpendingForecast
import net.loeu.wallybudget.data.model.recordedDate
import net.loeu.wallybudget.ui.components.TimelineLockBanner
import net.loeu.wallybudget.ui.screens.history.HistoryScreen
import net.loeu.wallybudget.ui.screens.overview.OverviewPage
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun HomeScreen(
    budgetState: BudgetState,
    todayExpenses: List<Expense>,
    currentDate: LocalDate,
    activeCycleExpenseSections: List<ExpenseDaySection>,
    historySections: List<ExpenseCycleSection>,
    spendingForecast: SpendingForecast,
    isLoadingData: Boolean = false,
    onAddExpense: (Long, String, ExpenseCategory?, LocalDate) -> Unit,
    onRestoreExpense: (Expense) -> Unit,
    onUpdateExpense: (Expense) -> Unit,
    onDeleteExpense: (Expense) -> Unit,
    onNavigateToSettings: () -> Unit,
    showTopRightSettingsAction: Boolean,
    showAddExpenseSheet: Boolean,
    onShowAddExpenseSheet: () -> Unit,
    onHideAddExpenseSheet: () -> Unit,
    timelineLockReason: String? = null,
    modifier: Modifier = Modifier
) {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val showLedgerPane = windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)
    val isShortHeight = !windowSizeClass.isHeightAtLeastBreakpoint(HEIGHT_DP_MEDIUM_LOWER_BOUND)
    val preferCompactSummary = showLedgerPane && isShortHeight
    val contentVerticalPadding = if (showLedgerPane && isShortHeight) 0.dp else 12.dp
    val selectedDateForExpenseEpochDay = rememberSaveable { mutableLongStateOf(currentDate.toEpochDay()) }
    var expenseBeingEdited by remember { mutableStateOf<Expense?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val canEditExpenses = !isLoadingData && timelineLockReason == null

    LaunchedEffect(canEditExpenses) {
        if (!canEditExpenses) {
            expenseBeingEdited = null
            onHideAddExpenseSheet()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (!canEditExpenses) return@FloatingActionButton
                    selectedDateForExpenseEpochDay.longValue = currentDate.toEpochDay()
                    onShowAddExpenseSheet()
                },
                containerColor = if (!canEditExpenses) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
                contentColor = if (!canEditExpenses) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onPrimary
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add today’s expense")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            timelineLockReason?.let { reason ->
                TimelineLockBanner(
                    reason = reason,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            if (showLedgerPane) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = contentVerticalPadding),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OverviewPage(
                        budgetState = budgetState,
                        todayExpenses = todayExpenses,
                        activeCycleExpenseSections = activeCycleExpenseSections,
                        spendingForecast = spendingForecast,
                        isLoading = isLoadingData,
                        onEditTodayExpense = if (canEditExpenses) {
                            { expenseBeingEdited = it }
                        } else {
                            null
                        },
                        onNavigateToSettings = if (showTopRightSettingsAction) onNavigateToSettings else null,
                        showTodayExpensesSection = false,
                        enableHeaderCollapse = preferCompactSummary,
                        defaultCollapsedHeader = preferCompactSummary,
                        modifier = Modifier.weight(1.08f)
                    )
                    HistoryScreen(
                        historySections = historySections,
                        onAddExpense = onAddExpense,
                        onRestoreExpense = onRestoreExpense,
                        onUpdateExpense = onUpdateExpense,
                        onDeleteExpense = onDeleteExpense,
                        modifier = Modifier.weight(0.92f),
                        embedded = true,
                        interactionsEnabled = canEditExpenses
                    )
                }
            } else {
                OverviewPage(
                    budgetState = budgetState,
                    todayExpenses = todayExpenses,
                    activeCycleExpenseSections = activeCycleExpenseSections,
                    spendingForecast = spendingForecast,
                    isLoading = isLoadingData,
                    onEditTodayExpense = if (canEditExpenses) {
                        { expenseBeingEdited = it }
                    } else {
                        null
                    },
                    onNavigateToSettings = if (showTopRightSettingsAction) onNavigateToSettings else null,
                    enableHeaderCollapse = true,
                    defaultCollapsedHeader = false,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = contentVerticalPadding)
                )
            }
        }
    }

    if (showAddExpenseSheet && canEditExpenses) {
        val selectedDate = LocalDate.ofEpochDay(selectedDateForExpenseEpochDay.longValue)
        AddExpenseSheet(
            onDismiss = onHideAddExpenseSheet,
            onSubmitExpense = { amountCents, description, icon ->
                onAddExpense(amountCents, description, icon, selectedDate)
            },
            title = if (selectedDate == currentDate) {
                "Add expense"
            } else {
                "Add expense for ${selectedDate.format(DateTimeFormatter.ofPattern("MMM d"))}"
            },
            confirmButtonText = if (selectedDate == currentDate) {
                "Add expense"
            } else {
                "Add to ${selectedDate.format(DateTimeFormatter.ofPattern("MMM d"))}"
            },
            dateLabel = if (selectedDate == currentDate) {
                "Recorded for today"
            } else {
                "Recorded for ${selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))}"
            }
        )
    }

    expenseBeingEdited?.takeIf { canEditExpenses }?.let { editingExpense ->
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
            dateLabel = "Recorded for ${editingExpense.recordedDate().format(DateTimeFormatter.ofPattern("EEEE, MMM d"))}",
            initialAmountCents = editingExpense.amountCents,
            initialDescription = editingExpense.description,
            initialIcon = editingExpense.icon
        )
    }
}
