package net.loeu.wallybudget.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.SelectedBucketOverview
import net.loeu.wallybudget.ui.screens.expenses.ExpenseItem
import net.loeu.wallybudget.ui.screens.overview.LoadingExpenseList
import net.loeu.wallybudget.ui.screens.overview.LoadingValuePlaceholder
import net.loeu.wallybudget.util.CurrencyFormatter
import java.time.format.DateTimeFormatter

@Composable
internal fun BucketLoadingSection(title: String, rows: List<String>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        LoadingValuePlaceholder(
            sampleText = title,
            textStyle = MaterialTheme.typography.titleLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Start
        )
        rows.forEachIndexed { index, sampleText ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LoadingValuePlaceholder(
                    sampleText = sampleText,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                    fillWidth = index == rows.lastIndex
                )
                if (index != rows.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
internal fun DailyPacingBucketDetailsSection(selectedBucketOverview: SelectedBucketOverview) {
    val summary = selectedBucketOverview.summary
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        SectionHeading("Bucket details")
        PlainMetricRow("Allocated", CurrencyFormatter.format(summary.allocatedThisCycleCents))
        PlainMetricRow("Spent", CurrencyFormatter.format(summary.spentThisCycleCents))
        PlainMetricRow("Remaining", CurrencyFormatter.formatSigned(summary.remainingThisCycleCents))
        if (summary.overspentCents > 0L) {
            PlainMetricRow("Overspent", CurrencyFormatter.format(summary.overspentCents))
        }
        if (summary.earmarkedBalanceCents > 0L) {
            PlainMetricRow("Earmarked balance", CurrencyFormatter.format(summary.earmarkedBalanceCents))
        }
    }
}

@Composable
internal fun BucketCycleExpensesSection(
    selectedBucketOverview: SelectedBucketOverview,
    canEditExpenses: Boolean,
    onEditExpense: (Expense) -> Unit,
    isLoading: Boolean = false
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeading("Cycle expenses")
        if (isLoading) {
            LoadingExpenseList()
        } else if (selectedBucketOverview.activeCycleExpenseSections.isEmpty()) {
            Text(
                text = "No expenses recorded in this bucket this cycle.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            selectedBucketOverview.activeCycleExpenseSections.forEach { daySection ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = daySection.date.format(DateTimeFormatter.ofPattern("EEE, MMM d")),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    daySection.expenses.forEachIndexed { index, expense ->
                        ExpenseItem(
                            expense = expense,
                            showDivider = index != daySection.expenses.lastIndex,
                            onEdit = if (canEditExpenses) {
                                { onEditExpense(expense) }
                            } else {
                                null
                            }
                        )
                    }
                }
            }
        }
    }
}
