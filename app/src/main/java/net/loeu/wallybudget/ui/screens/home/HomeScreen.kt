package net.loeu.wallybudget.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.window.core.layout.WindowSizeClass.Companion.HEIGHT_DP_MEDIUM_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import net.loeu.wallybudget.R
import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.ExpenseCategory
import net.loeu.wallybudget.domain.model.ExpenseDaySection
import net.loeu.wallybudget.domain.model.SpendingForecast
import net.loeu.wallybudget.domain.model.displayDescription
import net.loeu.wallybudget.domain.model.recordedDate
import net.loeu.wallybudget.ui.components.TimelineLockBanner
import net.loeu.wallybudget.ui.screens.overview.OverviewPage
import net.loeu.wallybudget.ui.screens.overview.SpendingTodayPane
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val HomeFabSize = 56.dp
private val HomeFabListClearance = 16.dp

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun HomeScreen(
    budgetState: BudgetState,
    todayExpenses: List<Expense>,
    currentDate: LocalDate,
    activeCycleExpenseSections: List<ExpenseDaySection>,
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
    modifier: Modifier = Modifier,
    isLoadingData: Boolean = false,
    timelineLockReason: String? = null
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
    val overviewBottomContentPadding = if (showLedgerPane) 24.dp else HomeFabSize + HomeFabListClearance
    val onEditTodayExpense = if (canEditExpenses) { { expense: Expense -> expenseBeingEdited = expense } } else null
    HomeScreenEffects(canEditExpenses = canEditExpenses, onDisableEditing = {
        expenseBeingEdited = null
        onHideAddExpenseSheet()
    })
    HomeScreenScaffold(
        budgetState = budgetState,
        todayExpenses = todayExpenses,
        activeCycleExpenseSections = activeCycleExpenseSections,
        spendingForecast = spendingForecast,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        showLedgerPane = showLedgerPane,
        canEditExpenses = canEditExpenses,
        showTopRightSettingsAction = showTopRightSettingsAction,
        preferCompactSummary = preferCompactSummary,
        contentVerticalPadding = contentVerticalPadding,
        overviewBottomContentPadding = overviewBottomContentPadding,
        isLoadingData = isLoadingData,
        timelineLockReason = timelineLockReason,
        onEditTodayExpense = onEditTodayExpense,
        onNavigateToSettings = onNavigateToSettings,
        onShowAddExpenseSheet = {
            selectedDateForExpenseEpochDay.longValue = currentDate.toEpochDay()
            onShowAddExpenseSheet()
        }
    )
    AddExpenseSheetDialog(
        showAddExpenseSheet = showAddExpenseSheet,
        canEditExpenses = canEditExpenses,
        selectedDateEpochDay = selectedDateForExpenseEpochDay.longValue,
        currentDate = currentDate,
        onDismiss = onHideAddExpenseSheet,
        onAddExpense = onAddExpense
    )
    EditExpenseSheetDialog(
        editingExpense = expenseBeingEdited,
        canEditExpenses = canEditExpenses,
        snackbarHostState = snackbarHostState,
        onDismiss = { expenseBeingEdited = null },
        onUpdateExpense = onUpdateExpense,
        onDeleteExpense = onDeleteExpense,
        onRestoreExpense = onRestoreExpense,
        scope = scope
    )
}

@Composable
private fun HomeScreenEffects(
    canEditExpenses: Boolean,
    onDisableEditing: () -> Unit
) {
    LaunchedEffect(canEditExpenses) {
        if (!canEditExpenses) onDisableEditing()
    }
}

@Composable
private fun HomeScreenScaffold(
    budgetState: BudgetState,
    todayExpenses: List<Expense>,
    activeCycleExpenseSections: List<ExpenseDaySection>,
    spendingForecast: SpendingForecast,
    modifier: Modifier,
    snackbarHostState: SnackbarHostState,
    showLedgerPane: Boolean,
    canEditExpenses: Boolean,
    showTopRightSettingsAction: Boolean,
    preferCompactSummary: Boolean,
    contentVerticalPadding: Dp,
    overviewBottomContentPadding: Dp,
    isLoadingData: Boolean,
    timelineLockReason: String?,
    onEditTodayExpense: ((Expense) -> Unit)?,
    onNavigateToSettings: () -> Unit,
    onShowAddExpenseSheet: () -> Unit
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            HomeAddExpenseFab(
                canEditExpenses = canEditExpenses,
                onShowAddExpenseSheet = onShowAddExpenseSheet
            )
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
                WideHomeContent(
                    budgetState = budgetState,
                    todayExpenses = todayExpenses,
                    activeCycleExpenseSections = activeCycleExpenseSections,
                    spendingForecast = spendingForecast,
                    isLoading = isLoadingData,
                    onEditTodayExpense = onEditTodayExpense,
                    onNavigateToSettings = if (showTopRightSettingsAction) onNavigateToSettings else null,
                    preferCompactSummary = preferCompactSummary,
                    overviewBottomContentPadding = overviewBottomContentPadding,
                    detailsBottomContentPadding = HomeFabSize + HomeFabListClearance,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = contentVerticalPadding)
                )
            } else {
                OverviewPage(
                    budgetState = budgetState,
                    todayExpenses = todayExpenses,
                    activeCycleExpenseSections = activeCycleExpenseSections,
                    spendingForecast = spendingForecast,
                    isLoading = isLoadingData,
                    onEditTodayExpense = onEditTodayExpense,
                    onNavigateToSettings = if (showTopRightSettingsAction) onNavigateToSettings else null,
                    enableHeaderCollapse = true,
                    defaultCollapsedHeader = false,
                    bottomContentPadding = overviewBottomContentPadding,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = contentVerticalPadding)
                )
            }
        }
    }
}

@Composable
private fun HomeAddExpenseFab(
    canEditExpenses: Boolean,
    onShowAddExpenseSheet: () -> Unit
) {
    FloatingActionButton(
        onClick = {
            if (canEditExpenses) {
                onShowAddExpenseSheet()
            }
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
        Icon(
            painter = painterResource(R.drawable.ic_add),
            contentDescription = "Add today’s expense"
        )
    }
}

@Composable
private fun AddExpenseSheetDialog(
    showAddExpenseSheet: Boolean,
    canEditExpenses: Boolean,
    selectedDateEpochDay: Long,
    currentDate: LocalDate,
    onDismiss: () -> Unit,
    onAddExpense: (Long, String, ExpenseCategory?, LocalDate) -> Unit
) {
    if (!showAddExpenseSheet || !canEditExpenses) return

    val selectedDate = LocalDate.ofEpochDay(selectedDateEpochDay)
    AddExpenseSheet(
        onDismiss = onDismiss,
        onSubmitExpense = { amountCents, description, icon ->
            onAddExpense(amountCents, description, icon, selectedDate)
        },
        title = addExpenseSheetTitle(selectedDate = selectedDate, currentDate = currentDate),
        confirmButtonText = addExpenseSheetConfirmLabel(
            selectedDate = selectedDate,
            currentDate = currentDate
        ),
        dateLabel = addExpenseSheetDateLabel(selectedDate = selectedDate, currentDate = currentDate)
    )
}

@Composable
private fun EditExpenseSheetDialog(
    editingExpense: Expense?,
    canEditExpenses: Boolean,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit,
    onUpdateExpense: (Expense) -> Unit,
    onDeleteExpense: (Expense) -> Unit,
    onRestoreExpense: (Expense) -> Unit,
    scope: CoroutineScope
) {
    val editableExpense = editingExpense?.takeIf { canEditExpenses } ?: return

    AddExpenseSheet(
        onDismiss = onDismiss,
        onSubmitExpense = { amountCents, description, icon ->
            onUpdateExpense(
                editableExpense.copy(
                    amountCents = amountCents,
                    description = description,
                    icon = icon
                )
            )
            onDismiss()
        },
        onDeleteExpense = {
            onDeleteExpense(editableExpense)
            scope.launch {
                val dismissJob = launch {
                    delay(5000)
                    snackbarHostState.currentSnackbarData?.dismiss()
                }
                val result = snackbarHostState.showSnackbar(
                    message = deletedExpenseMessage(editableExpense),
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    dismissJob.cancel()
                    onRestoreExpense(editableExpense)
                }
            }
            onDismiss()
        },
        title = "Edit expense",
        confirmButtonText = "Save changes",
        dateLabel = "Recorded for ${
            editableExpense.recordedDate().format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
        }",
        initialAmountCents = editableExpense.amountCents,
        initialDescription = editableExpense.description,
        initialIcon = editableExpense.icon
    )
}

private fun addExpenseSheetTitle(selectedDate: LocalDate, currentDate: LocalDate): String =
    if (selectedDate == currentDate) {
        "Add expense"
    } else {
        "Add expense for ${selectedDate.format(DateTimeFormatter.ofPattern("MMM d"))}"
    }

private fun addExpenseSheetConfirmLabel(selectedDate: LocalDate, currentDate: LocalDate): String =
    if (selectedDate == currentDate) {
        "Add expense"
    } else {
        "Add to ${selectedDate.format(DateTimeFormatter.ofPattern("MMM d"))}"
    }

private fun addExpenseSheetDateLabel(selectedDate: LocalDate, currentDate: LocalDate): String =
    if (selectedDate == currentDate) {
        "Recorded for today"
    } else {
        "Recorded for ${selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))}"
    }

private fun deletedExpenseMessage(expense: Expense): String =
    if (expense.displayDescription.isNotBlank()) {
        "Deleted \"${expense.displayDescription}\""
    } else {
        "Deleted expense"
    }

@Composable
internal fun WideHomeContent(
    budgetState: BudgetState,
    todayExpenses: List<Expense>,
    activeCycleExpenseSections: List<ExpenseDaySection>,
    spendingForecast: SpendingForecast,
    isLoading: Boolean,
    onEditTodayExpense: ((Expense) -> Unit)?,
    onNavigateToSettings: (() -> Unit)?,
    preferCompactSummary: Boolean,
    overviewBottomContentPadding: Dp,
    detailsBottomContentPadding: Dp,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OverviewPage(
            budgetState = budgetState,
            todayExpenses = todayExpenses,
            activeCycleExpenseSections = activeCycleExpenseSections,
            spendingForecast = spendingForecast,
            isLoading = isLoading,
            onEditTodayExpense = onEditTodayExpense,
            onNavigateToSettings = onNavigateToSettings,
            showSpendingDetailsSection = false,
            showTodayExpensesSection = false,
            enableHeaderCollapse = preferCompactSummary,
            defaultCollapsedHeader = false,
            bottomContentPadding = overviewBottomContentPadding,
            modifier = Modifier
                .weight(1.08f)
                .testTag("home_landscape_left_pane")
        )
        LazyColumn(
            modifier = Modifier
                .weight(0.92f)
                .fillMaxWidth()
                .testTag("home_landscape_right_pane"),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 10.dp,
                bottom = detailsBottomContentPadding
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                SpendingTodayPane(
                    budgetState = budgetState,
                    todayExpenses = todayExpenses,
                    activeCycleExpenseSections = activeCycleExpenseSections,
                    isLoading = isLoading,
                    onEditTodayExpense = onEditTodayExpense
                )
            }
        }
    }
}
