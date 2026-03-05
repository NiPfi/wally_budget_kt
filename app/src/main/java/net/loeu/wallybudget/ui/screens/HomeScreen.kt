package net.loeu.wallybudget.ui.screens

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import net.loeu.wallybudget.data.model.BudgetState
import net.loeu.wallybudget.data.model.Expense
import net.loeu.wallybudget.data.model.SpendingForecast
import net.loeu.wallybudget.ui.components.AddExpenseSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.loeu.wallybudget.data.model.ExpenseCategory
import java.time.LocalDate
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    budgetState: BudgetState,
    todayExpenses: List<Expense>,
    previousCycleExpenses: List<Expense>,
    spendingForecast: SpendingForecast,
    onAddExpense: (Long, String, ExpenseCategory?, LocalDate) -> Unit,
    onRestoreExpense: (Expense) -> Unit,
    onUpdateExpense: (Expense) -> Unit,
    onDeleteExpense: (Expense) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
    showAddExpenseSheet: Boolean,
    onShowAddExpenseSheet: () -> Unit,
    onHideAddExpenseSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedDateForExpenseEpochDay = rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    val selectedDateForExpense = LocalDate.ofEpochDay(selectedDateForExpenseEpochDay.longValue)
    val expenseBeingEdited = remember { mutableStateOf<Expense?>(null) }
    val isExpensesVisible = remember { mutableStateOf(false) }
    val addSheetOpenedFromOverview = rememberSaveable { mutableStateOf(false) }
    val resetExpensesToLatestTrigger = rememberSaveable { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        topBar = {
            TopAppBar(
                title = { Text("WallyBudget") },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.History, contentDescription = "View history")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (!isExpensesVisible.value) {
                        selectedDateForExpenseEpochDay.longValue = LocalDate.now().toEpochDay()
                        addSheetOpenedFromOverview.value = true
                    } else {
                        addSheetOpenedFromOverview.value = false
                    }
                    onShowAddExpenseSheet()
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add expense")
            }
        },
        modifier = modifier
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val pageHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
            val snapController = rememberVerticalSnapController(containerHeightPx = pageHeightPx)
            val effectiveOffset = snapController.offsetPx

            SideEffect {
                isExpensesVisible.value = snapController.isExpanded
            }

            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(0, -effectiveOffset.roundToInt()) }
                ) {
                    // Wrap OverviewPage in a draggable Box to allow swiping up from it
                    // without blocking clicks on its content (taps go to children first).
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (!snapController.isExpanded) {
                                    Modifier.draggable(
                                        state = snapController.dragState,
                                        orientation = Orientation.Vertical,
                                        onDragStarted = { snapController.onDragStarted() },
                                        onDragStopped = { velocity -> snapController.onDragStopped(velocity) }
                                    )
                                } else Modifier
                            )
                    ) {
                        OverviewPage(
                            budgetState = budgetState,
                            previousCycleExpenses = previousCycleExpenses,
                            spendingForecast = spendingForecast,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset { IntOffset(0, pageHeightPx.roundToInt()) }
                    ) {
                        ExpensesPage(
                            budgetState = budgetState,
                            todayExpenses = todayExpenses,
                            previousCycleExpenses = previousCycleExpenses,
                            isVisible = snapController.isExpanded,
                            resetToLatestTrigger = resetExpensesToLatestTrigger.intValue,
                            onEditExpense = { expenseBeingEdited.value = it },
                            onDeleteExpense = onDeleteExpense,
                            onDateSelected = { selectedDateForExpenseEpochDay.longValue = it.toEpochDay() },
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        // Overlay a small draggable area at the top of ExpensesPage 
                        // so users can pull it down easily.
                        if (snapController.isExpanded) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp) // Smaller hit area for pulling down
                                    .align(Alignment.TopCenter)
                                    .draggable(
                                        state = snapController.dragState,
                                        orientation = Orientation.Vertical,
                                        onDragStarted = { snapController.onDragStarted() },
                                        onDragStopped = { velocity -> snapController.onDragStopped(velocity) }
                                    )
                            )
                        }
                    }
                }

                // Floating "Pull up/down" indicator
                val progress = snapController.progress
                val density = LocalDensity.current
                val bottomY = remember(density, pageHeightPx) { pageHeightPx - with(density) { 120.dp.toPx() } }
                val topY = remember(density) { with(density) { 24.dp.toPx() } }
                val currentY = bottomY + (topY - bottomY) * progress

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .offset { IntOffset(0, currentY.roundToInt()) }
                        .draggable(
                            state = snapController.dragState,
                            orientation = Orientation.Vertical,
                            onDragStarted = { snapController.onDragStarted() },
                            onDragStopped = { velocity -> snapController.onDragStopped(velocity) }
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    snapController.toggle()
                                }
                            )
                        }
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = null,
                        modifier = Modifier.rotate(180f * progress),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Pull up to open expenses",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 1f - progress)
                        )
                        Text(
                            text = "Pull down to return",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = progress)
                        )
                    }
                }
            }
        }
    }

    if (showAddExpenseSheet) {
        AddExpenseSheet(
            onDismiss = {
                addSheetOpenedFromOverview.value = false
                onHideAddExpenseSheet()
            },
            onSubmitExpense = { amountCents, description, icon ->
                onAddExpense(amountCents, description, icon, selectedDateForExpense)
                if (addSheetOpenedFromOverview.value) {
                    resetExpensesToLatestTrigger.intValue += 1
                }
                addSheetOpenedFromOverview.value = false
            }
        )
    }

    expenseBeingEdited.value?.let { editingExpense ->
        AddExpenseSheet(
            onDismiss = { expenseBeingEdited.value = null },
            onSubmitExpense = { amountCents, description, icon ->
                onUpdateExpense(
                    editingExpense.copy(
                        amountCents = amountCents,
                        description = description,
                        icon = icon
                    )
                )
                expenseBeingEdited.value = null
            },
            onDeleteExpense = {
                onDeleteExpense(editingExpense)
                coroutineScope.launch {
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
                expenseBeingEdited.value = null
            }
        )
    }
}
