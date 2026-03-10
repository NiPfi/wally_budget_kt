package net.loeu.wallybudget.ui.screens.closeout

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.ExpenseCycleSection
import net.loeu.wallybudget.domain.model.PendingCycleCloseoutState
import net.loeu.wallybudget.ui.screens.history.CycleLedgerScreen
import net.loeu.wallybudget.util.CurrencyFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun CycleCloseoutScreen(
    pendingCycle: PendingCycleCloseoutState,
    onReviewCycle: () -> Unit,
    onConcludeCycle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pulseTransition = rememberInfiniteTransition(label = "closeout_button")
    val scale = pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "closeout_button_scale"
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.background
                    )
                )
            ),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Cycle complete",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = buildString {
                            append(
                                pendingCycle.cycleStartDate.format(
                                    DateTimeFormatter.ofPattern("MMM d")
                                )
                            )
                            append(" - ")
                            append(
                                pendingCycle.cycleEndDateExclusive
                                    .minusDays(1)
                                    .format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
                            )
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (pendingCycle.surplusCents >= 0L) {
                            "You finished ${CurrencyFormatter.format(pendingCycle.surplusCents)} under budget."
                        } else {
                            "You finished ${CurrencyFormatter.format(kotlin.math.abs(pendingCycle.surplusCents))} over budget."
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
        item {
            InsightGrid(
                pendingCycle = pendingCycle,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Before you lock this cycle",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Review the ending cycle if you need to correct an expense. Normal Home stays locked until you conclude this cycle.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onReviewCycle,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Review cycle expenses")
                    }
                    Button(
                        onClick = onConcludeCycle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .scale(scale.value)
                    ) {
                        Text("Conclude cycle")
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightGrid(
    pendingCycle: PendingCycleCloseoutState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Cycle insights",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            InsightRow("Spent", CurrencyFormatter.format(pendingCycle.totalSpentCents))
            InsightRow("Average day", CurrencyFormatter.format(pendingCycle.averageDailySpendCents))
            InsightRow(
                "Biggest expense",
                pendingCycle.biggestExpense?.let { "${it.description} • ${CurrencyFormatter.format(it.amountCents)}" }
                    ?: "No standout expense"
            )
            InsightRow(
                "Highest-spend day",
                pendingCycle.highestSpendDay?.format(DateTimeFormatter.ofPattern("MMM d")) ?: "No spending recorded"
            )
            InsightRow(
                "Top category",
                pendingCycle.topCategory?.description ?: "No category trend yet"
            )
            InsightRow("Trend", pendingCycle.trendSummary)
        }
    }
}

@Composable
private fun InsightRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun CycleCloseoutReviewScreen(
    pendingCycle: PendingCycleCloseoutState,
    onNavigateBack: () -> Unit,
    onEditExpense: (Expense) -> Unit,
    onAddExpenseForDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    CycleLedgerScreen(
        section = pendingCycle.toExpenseCycleSection(),
        title = "Review ended cycle",
        modifier = modifier,
        onEditExpense = onEditExpense,
        onAddExpenseForDate = onAddExpenseForDate,
        onNavigateBack = onNavigateBack
    )
}

private fun PendingCycleCloseoutState.toExpenseCycleSection(): ExpenseCycleSection {
    return ExpenseCycleSection(
        cycleStartDate = cycleStartDate,
        cycleEndDateExclusive = cycleEndDateExclusive,
        title = formatCycleDisplayName(cycleStartDate, cycleEndDateExclusive),
        budgetAmountCents = budgetAmountCents,
        totalSpentCents = totalSpentCents,
        surplusCents = surplusCents,
        daySections = daySections,
        isActiveCycle = false,
        isReadOnly = false,
        isCompletedCycle = true
    )
}

private fun formatCycleDisplayName(
    cycleStartDate: LocalDate,
    cycleEndDateExclusive: LocalDate
): String {
    val cycleEndDate = cycleEndDateExclusive.minusDays(1)

    return if (cycleStartDate.year == cycleEndDate.year) {
        "${cycleStartDate.format(DateTimeFormatter.ofPattern("MMM d"))} - ${
            cycleEndDate.format(
                DateTimeFormatter.ofPattern("MMM d, yyyy")
            )
        }"
    } else {
        "${cycleStartDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))} - ${
            cycleEndDate.format(
                DateTimeFormatter.ofPattern("MMM d, yyyy")
            )
        }"
    }
}
