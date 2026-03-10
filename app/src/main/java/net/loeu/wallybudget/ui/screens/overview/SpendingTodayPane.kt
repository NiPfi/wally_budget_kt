package net.loeu.wallybudget.ui.screens.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.data.local.entity.Expense
import net.loeu.wallybudget.ui.model.ExpenseDaySection
import net.loeu.wallybudget.ui.screens.expenses.ExpenseItem

@Composable
fun SpendingTodayPane(
    budgetState: BudgetState,
    todayExpenses: List<Expense>,
    activeCycleExpenseSections: List<ExpenseDaySection>,
    onEditTodayExpense: ((Expense) -> Unit)?,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    showTodayExpensesSection: Boolean = true
) {
    val previousExpensesTotal = activeCycleExpenseSections
        .filterNot { it.isToday }
        .sumOf { it.totalSpentCents }
    val adjustedDailyAllowanceCents = budgetState.remainingTodayCents + budgetState.spentTodayCents
    val dailyAdjustmentCents = adjustedDailyAllowanceCents - budgetState.dailyBudgetCents

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_spending_today_section"),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        SpendingDetailsCard(
            budgetState = budgetState,
            previousExpensesTotal = previousExpensesTotal,
            dailyAdjustmentCents = dailyAdjustmentCents,
            adjustedDailyAllowanceCents = adjustedDailyAllowanceCents,
            isLoading = isLoading
        )

        if (showTodayExpensesSection) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = 10.dp)
            )
            TodayExpensesSection(
                totalSpentCents = budgetState.spentTodayCents,
                todayExpenses = todayExpenses,
                isLoading = isLoading,
                onEditTodayExpense = onEditTodayExpense,
                modifier = Modifier.testTag("home_today_expenses_section")
            )
        }
    }
}

@Composable
private fun TodayExpensesSection(
    totalSpentCents: Long,
    todayExpenses: List<Expense>,
    isLoading: Boolean,
    onEditTodayExpense: ((Expense) -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val displayedExpenses = todayExpenses.take(4)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = "Today's expenses",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            AnimatedCounter(
                amountCents = totalSpentCents,
                textStyle = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                animate = true,
                textAlign = TextAlign.End,
                placeholder = isLoading,
                placeholderText = "$888"
            )
        }

        if (isLoading) {
            repeat(3) {
                LoadingExpenseRow()
                if (it != 2) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        } else if (todayExpenses.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No expenses yet for today.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            displayedExpenses.forEachIndexed { index, expense ->
                ExpenseItem(
                    expense = expense,
                    animateAmount = false,
                    showDivider = false,
                    onEdit = onEditTodayExpense?.let { editExpense ->
                        { editExpense(expense) }
                    }
                )
                if (index != displayedExpenses.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun LoadingExpenseRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LoadingValuePlaceholder(
            sampleText = "88",
            textStyle = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Start
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LoadingValuePlaceholder(
                sampleText = "Groceries and coffee",
                textStyle = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Start
            )
            LoadingValuePlaceholder(
                sampleText = "12:45",
                textStyle = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Start
            )
        }
        LoadingValuePlaceholder(
            sampleText = "$88.88",
            textStyle = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.End
        )
    }
}
