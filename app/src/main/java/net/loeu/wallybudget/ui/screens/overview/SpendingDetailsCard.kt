package net.loeu.wallybudget.ui.screens.overview

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.loeu.wallybudget.domain.model.BudgetState
import kotlin.math.abs

@Composable
fun SpendingDetailsCard(
    budgetState: BudgetState,
    previousExpensesTotal: Long,
    dailyAdjustmentCents: Long,
    adjustedDailyAllowanceCents: Long,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Spending details",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        DetailRowSmall("Cycle spent") {
            AnimatedCounter(
                amountCents = budgetState.totalSpentThisCycleCents,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                animate = true,
                textAlign = TextAlign.End,
                placeholder = isLoading,
                placeholderText = "$8,888"
            )
        }
        DetailRowSmall("Past days") {
            AnimatedCounter(
                amountCents = previousExpensesTotal,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                animate = true,
                textAlign = TextAlign.End,
                placeholder = isLoading,
                placeholderText = "$8,888"
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        DetailRowSmall("Base daily allowance") {
            AnimatedCounter(
                amountCents = budgetState.dailyBudgetCents,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                animate = true,
                textAlign = TextAlign.End,
                placeholder = isLoading,
                placeholderText = "$888"
            )
        }
        DetailRowSmall(
            label = "Budget adjustment"
        ) {
            AnimatedCounter(
                amountCents = abs(dailyAdjustmentCents),
                formatter = { value ->
                    "${if (dailyAdjustmentCents >= 0L) "+" else "-"}${net.loeu.wallybudget.util.CurrencyFormatter.format(value)}"
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                animate = true,
                textAlign = TextAlign.End,
                color = if (dailyAdjustmentCents >= 0L) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                placeholder = isLoading,
                placeholderText = "+$888"
            )
        }
        DetailRowSmall("Effective allowance") {
            AnimatedCounter(
                amountCents = adjustedDailyAllowanceCents,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                animate = true,
                textAlign = TextAlign.End,
                placeholder = isLoading,
                placeholderText = "$888"
            )
        }
    }
}

@Composable
private fun DetailRowSmall(
    label: String,
    valueContent: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        valueContent()
    }
}
