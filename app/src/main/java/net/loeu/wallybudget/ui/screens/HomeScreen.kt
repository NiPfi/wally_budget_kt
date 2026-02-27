package net.loeu.wallybudget.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import net.loeu.wallybudget.data.model.BudgetState
import net.loeu.wallybudget.data.model.Expense
import net.loeu.wallybudget.ui.components.AddExpenseSheet
import net.loeu.wallybudget.ui.components.AnimatedCounter
import net.loeu.wallybudget.ui.components.ExpenseItem
import net.loeu.wallybudget.util.CurrencyFormatter
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class HomePage {
    Overview,
    Expenses
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    budgetState: BudgetState,
    todayExpenses: List<Expense>,
    previousCycleExpenses: List<Expense>,
    onAddExpense: (Double, String, net.loeu.wallybudget.data.model.ExpenseIcon?, LocalDate) -> Unit,
    onUpdateExpense: (Expense) -> Unit,
    onDeleteExpense: (Expense) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
    showAddExpenseSheet: Boolean,
    onShowAddExpenseSheet: () -> Unit,
    onHideAddExpenseSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedDateForExpense by remember { mutableStateOf(LocalDate.now()) }
    var expenseBeingEdited by remember { mutableStateOf<Expense?>(null) }

    Scaffold(
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
                onClick = onShowAddExpenseSheet,
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
            val threshold = pageHeightPx * 0.28f

            var pageState by remember { mutableStateOf(HomePage.Overview) }
            var dragging by remember { mutableStateOf(false) }
            val coroutineScope = rememberCoroutineScope()
            val offsetAnim = remember { Animatable(0f) }

            LaunchedEffect(pageHeightPx) {
                offsetAnim.updateBounds(lowerBound = 0f, upperBound = pageHeightPx)
                if (!dragging) {
                    offsetAnim.snapTo(if (pageState == HomePage.Expenses) pageHeightPx else 0f)
                }
            }

            LaunchedEffect(pageState, dragging, pageHeightPx) {
                if (!dragging) {
                    val targetOffset = if (pageState == HomePage.Expenses) pageHeightPx else 0f
                    offsetAnim.animateTo(
                        targetValue = targetOffset,
                        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                    )
                }
            }

            val effectiveOffset = offsetAnim.value

            val dragState = rememberDraggableState { delta ->
                coroutineScope.launch {
                    val nextOffset = (offsetAnim.value - delta).coerceIn(0f, pageHeightPx)
                    offsetAnim.snapTo(nextOffset)
                }
            }

            val onDragStarted = {
                dragging = true
                coroutineScope.launch {
                    offsetAnim.stop()
                }
            }

            val onDragStopped: (Float) -> Unit = { velocity ->
                val currentOffset = offsetAnim.value
                val shouldOpenExpenses = when {
                    velocity < -1000f -> true
                    velocity > 1000f -> false
                    else -> currentOffset > if (pageState == HomePage.Overview) threshold else pageHeightPx - threshold
                }

                pageState = if (shouldOpenExpenses) HomePage.Expenses else HomePage.Overview
                dragging = false
            }

            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(0, -effectiveOffset.roundToInt()) }
                ) {
                    OverviewPage(
                        budgetState = budgetState,
                        previousCycleExpenses = previousCycleExpenses,
                        modifier = Modifier.fillMaxSize()
                    )
                    ExpensesPage(
                        budgetState = budgetState,
                        todayExpenses = todayExpenses,
                        previousCycleExpenses = previousCycleExpenses,
                        onEditExpense = { expenseBeingEdited = it },
                        onDeleteExpense = onDeleteExpense,
                        onDateSelected = { selectedDateForExpense = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .offset { IntOffset(0, pageHeightPx.roundToInt()) }
                    )
                }

                if (pageState == HomePage.Overview) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxSize()
                            .padding(bottom = 140.dp)
                            .draggable(
                                state = dragState,
                                orientation = Orientation.Vertical,
                                onDragStarted = { onDragStarted() },
                                onDragStopped = { velocity -> onDragStopped(velocity) }
                            )
                    )
                }

                val progress = (effectiveOffset / pageHeightPx).coerceIn(0f, 1f)
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
                            state = dragState,
                            orientation = Orientation.Vertical,
                            onDragStarted = { onDragStarted() },
                            onDragStopped = { velocity -> onDragStopped(velocity) }
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    pageState = if (pageState == HomePage.Overview) HomePage.Expenses else HomePage.Overview
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
            onDismiss = onHideAddExpenseSheet,
            onSubmitExpense = { amount, description, icon ->
                onAddExpense(amount, description, icon, selectedDateForExpense)
            }
        )
    }

    expenseBeingEdited?.let { editingExpense ->
        AddExpenseSheet(
            onDismiss = { expenseBeingEdited = null },
            onSubmitExpense = { amount, description, icon ->
                onUpdateExpense(
                    editingExpense.copy(
                        amount = amount,
                        description = description,
                        icon = icon
                    )
                )
                expenseBeingEdited = null
            },
            title = "Edit Expense",
            confirmButtonText = "Save Changes",
            initialAmount = editingExpense.amount,
            initialDescription = editingExpense.description,
            initialIcon = editingExpense.icon
        )
    }
}

@Composable
private fun OverviewPage(
    budgetState: BudgetState,
    previousCycleExpenses: List<Expense>,
    modifier: Modifier = Modifier
) {
    val previousExpensesTotal = remember(previousCycleExpenses) {
        previousCycleExpenses.sumOf { it.amount }
    }

    LazyColumn(
        modifier = modifier,
        userScrollEnabled = false,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Today's Budget",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    AnimatedCounter(
                        amount = budgetState.remainingToday,
                        textStyle = MaterialTheme.typography.displayLarge,
                        color = if (budgetState.remainingToday >= 0) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "remaining",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Spent Today",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = CurrencyFormatter.format(budgetState.spentToday),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Daily Allowance",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = CurrencyFormatter.format(budgetState.dailyBudget),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Days Remaining",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = budgetState.daysRemainingInCycle.toString(),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "This Cycle",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = CurrencyFormatter.format(budgetState.totalSpentThisCycle),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
        }

        if (budgetState.cumulativeSavings != 0.0) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (budgetState.cumulativeSavings > 0) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Overall ${if (budgetState.cumulativeSavings > 0) "Savings" else "Deficit"}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = CurrencyFormatter.format(abs(budgetState.cumulativeSavings)),
                            style = MaterialTheme.typography.titleLarge,
                            color = if (budgetState.cumulativeSavings > 0) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            }
                        )
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Previous Expenses",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "This cycle before today",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = previousCycleExpenses.size.toString(),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = CurrencyFormatter.format(previousExpensesTotal),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExpensesPage(
    budgetState: BudgetState,
    todayExpenses: List<Expense>,
    previousCycleExpenses: List<Expense>,
    onEditExpense: (Expense) -> Unit,
    onDeleteExpense: (Expense) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val cycleStart = budgetState.cycleStartDate
    val daysInCycleSoFar = ChronoUnit.DAYS.between(cycleStart, today).toInt() + 1
    
    val pagerState = rememberPagerState(
        initialPage = daysInCycleSoFar - 1,
        pageCount = { daysInCycleSoFar }
    )

    LaunchedEffect(pagerState.currentPage) {
        val selectedDate = cycleStart.plusDays(pagerState.currentPage.toLong())
        onDateSelected(selectedDate)
    }

    val allExpenses = remember(todayExpenses, previousCycleExpenses) {
        todayExpenses + previousCycleExpenses
    }

    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.surface)
    ) {
        Spacer(modifier = Modifier.height(72.dp))
        
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val currentDate = cycleStart.plusDays(page.toLong())
            val isToday = currentDate == today
            
            val dayExpenses = remember(allExpenses, currentDate) {
                allExpenses.filter { expense ->
                    val expenseDate = java.time.Instant.ofEpochMilli(expense.timestamp)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                    expenseDate == currentDate
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isToday) "Today" else currentDate.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = currentDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Day ${page + 1} of cycle",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (dayExpenses.isEmpty()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp, horizontal = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No expenses on this day.\nTap + to add one.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(dayExpenses, key = { it.id }) { expense ->
                            ExpenseItem(
                                expense = expense,
                                onEdit = { onEditExpense(expense) },
                                onDelete = { onDeleteExpense(expense) }
                            )
                        }
                    }
                }
            }
        }
    }
}
