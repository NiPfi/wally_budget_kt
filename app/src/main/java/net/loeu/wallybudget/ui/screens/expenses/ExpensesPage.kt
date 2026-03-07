package net.loeu.wallybudget.ui.screens.expenses

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.loeu.wallybudget.data.model.Expense
import net.loeu.wallybudget.data.model.ExpenseDaySection
import net.loeu.wallybudget.util.CurrencyFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesPage(
    sections: List<ExpenseDaySection>,
    title: String,
    modifier: Modifier = Modifier,
    onEditExpense: (Expense) -> Unit,
    onAddExpenseForDate: ((LocalDate) -> Unit)? = null,
    onNavigateBack: (() -> Unit)? = null
) {
    val expandedDates = rememberSaveable { mutableStateListOf<Long>() }

    LaunchedEffect(sections.firstOrNull()?.date?.toEpochDay()) {
        val firstDate = sections.firstOrNull()?.date?.toEpochDay() ?: return@LaunchedEffect
        if (expandedDates.isEmpty()) {
            expandedDates += firstDate
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .testTag("expenses_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(sections, key = { it.date.toEpochDay() }) { section ->
                val sectionKey = section.date.toEpochDay()
                val isExpanded = sectionKey in expandedDates
                ExpenseDaySectionCard(
                    section = section,
                    isExpanded = isExpanded,
                    onToggleExpanded = {
                        if (isExpanded) {
                            expandedDates.remove(sectionKey)
                        } else {
                            expandedDates += sectionKey
                        }
                    },
                    onEditExpense = onEditExpense,
                    onAddExpenseForDate = onAddExpenseForDate
                )
            }
        }
    }
}

@Composable
private fun ExpenseDaySectionCard(
    section: ExpenseDaySection,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onEditExpense: (Expense) -> Unit,
    onAddExpenseForDate: ((LocalDate) -> Unit)?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (section.isToday) {
                            "Today"
                        } else {
                            section.date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${section.expenses.size} expense${if (section.expenses.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = CurrencyFormatter.format(section.totalSpentCents),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    section.remainingForDayCents?.let { remaining ->
                        Text(
                            text = if (remaining >= 0L) {
                                "${CurrencyFormatter.format(abs(remaining))} left"
                            } else {
                                "${CurrencyFormatter.format(abs(remaining))} over"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (remaining >= 0L) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    if (section.expenses.isEmpty()) {
                        Text(
                            text = "No expenses recorded for this day.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        section.expenses.forEach { expense ->
                            ExpenseItem(
                                expense = expense,
                                onEdit = if (section.isEditable) {
                                    { onEditExpense(expense) }
                                } else {
                                    null
                                }
                            )
                        }
                    }

                    if (section.isEditable && !section.isToday && onAddExpenseForDate != null) {
                        TextButton(
                            onClick = { onAddExpenseForDate(section.date) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Add to ${section.date.format(DateTimeFormatter.ofPattern("MMM d"))}")
                        }
                    } else if (section.expenses.isEmpty() && section.isToday && onAddExpenseForDate != null) {
                        Button(
                            onClick = { onAddExpenseForDate(section.date) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Add today’s first expense")
                        }
                    }
                }
            }
        }
    }
}
