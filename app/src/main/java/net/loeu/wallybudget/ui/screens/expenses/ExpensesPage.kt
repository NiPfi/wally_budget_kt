package net.loeu.wallybudget.ui.screens.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import net.loeu.wallybudget.data.model.BudgetState
import net.loeu.wallybudget.data.model.Expense
import net.loeu.wallybudget.data.model.groupByDate
import net.loeu.wallybudget.util.CurrencyFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

@Composable
fun ExpensesPage(
    budgetState: BudgetState,
    todayExpenses: List<Expense>,
    previousCycleExpenses: List<Expense>,
    isVisible: Boolean,
    resetToLatestTrigger: Int,
    onEditExpense: (Expense) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val cycleStart = budgetState.cycleStartDate
    val daysInCycleSoFar = ChronoUnit.DAYS.between(cycleStart, today).toInt() + 1

    val pagerState = rememberPagerState(
        initialPage = (daysInCycleSoFar - 1).coerceAtLeast(0),
        pageCount = { daysInCycleSoFar.coerceAtLeast(1) }
    )

    val lifecycleOwner = LocalLifecycleOwner.current
    var wasPaused by remember { mutableStateOf(false) }
    var shouldResetToTodayOnOpen by remember { mutableStateOf(false) }
    var hasOpenedExpensesAtLeastOnce by remember { mutableStateOf(false) }
    var lastHandledExternalResetTrigger by remember { mutableStateOf(resetToLatestTrigger) }
    var lastKnownDaysInCycleSoFar by remember { mutableStateOf(daysInCycleSoFar) }
    var shouldResetToLatestOnNextOpenAfterRollover by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> wasPaused = true
                Lifecycle.Event.ON_RESUME -> {
                    if (wasPaused) {
                        shouldResetToTodayOnOpen = true
                        wasPaused = false
                    }
                }
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(isVisible, daysInCycleSoFar, shouldResetToTodayOnOpen, resetToLatestTrigger) {
        val dayRolledOver = daysInCycleSoFar > lastKnownDaysInCycleSoFar

        if (!isVisible) {
            if (dayRolledOver) {
                shouldResetToLatestOnNextOpenAfterRollover = true
            }
            lastKnownDaysInCycleSoFar = daysInCycleSoFar
            return@LaunchedEffect
        }

        val newLastPage = (daysInCycleSoFar - 1).coerceAtLeast(0)

        val hasExternalResetRequest = resetToLatestTrigger != lastHandledExternalResetTrigger
        val shouldResetToLatest =
            !hasOpenedExpensesAtLeastOnce ||
                shouldResetToTodayOnOpen ||
                hasExternalResetRequest ||
                shouldResetToLatestOnNextOpenAfterRollover

        if (shouldResetToLatest) {
            pagerState.scrollToPage(newLastPage)
            shouldResetToTodayOnOpen = false
            shouldResetToLatestOnNextOpenAfterRollover = false
            lastHandledExternalResetTrigger = resetToLatestTrigger
        }

        lastKnownDaysInCycleSoFar = daysInCycleSoFar
        hasOpenedExpensesAtLeastOnce = true
    }

    LaunchedEffect(pagerState.currentPage) {
        val selectedDate = cycleStart.plusDays(pagerState.currentPage.toLong())
        onDateSelected(selectedDate)
    }

    val allExpenses = remember(todayExpenses, previousCycleExpenses) {
        todayExpenses + previousCycleExpenses
    }
    val expensesByDate = remember(allExpenses) {
        allExpenses.groupByDate()
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

            val dayExpenses = remember(expensesByDate, currentDate) {
                expensesByDate[currentDate].orEmpty()
            }

            val dayTotalSpent = remember(dayExpenses) {
                dayExpenses.sumOf { it.amountCents }
            }
            val dayRemaining = budgetState.dailyBudgetCents - dayTotalSpent

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

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (dayRemaining >= 0) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (dayRemaining >= 0) "Leftover" else "Overspending",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = CurrencyFormatter.format(abs(dayRemaining)),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (dayRemaining >= 0) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                }
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Spent",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = CurrencyFormatter.format(dayTotalSpent),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (dayRemaining >= 0) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                }
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 120.dp),
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
                                onEdit = { onEditExpense(expense) }
                            )
                        }
                    }
                }
            }
        }
    }
}
