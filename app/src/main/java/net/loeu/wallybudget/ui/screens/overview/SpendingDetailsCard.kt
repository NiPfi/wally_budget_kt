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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.loeu.wallybudget.data.model.BudgetState
import net.loeu.wallybudget.util.CurrencyFormatter
import kotlin.math.abs

@Composable
fun SpendingDetailsCard(
    budgetState: BudgetState,
    previousExpensesTotal: Long,
    dailyAdjustmentCents: Long,
    adjustedDailyAllowanceCents: Long,
    modifier: Modifier = Modifier
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

        DetailRowSmall("Cycle spent", CurrencyFormatter.format(budgetState.totalSpentThisCycleCents))
        DetailRowSmall("Past days", CurrencyFormatter.format(previousExpensesTotal))
        DetailRowSmall("Spent today", CurrencyFormatter.format(budgetState.spentTodayCents))

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        DetailRowSmall("Base daily allowance", CurrencyFormatter.format(budgetState.dailyBudgetCents))
        DetailRowSmall(
            "Budget adjustment",
            "${if (dailyAdjustmentCents >= 0L) "+" else "-"}${CurrencyFormatter.format(abs(dailyAdjustmentCents))}",
            valueColor = if (dailyAdjustmentCents >= 0L) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
        )
        DetailRowSmall(
            "Effective allowance",
            CurrencyFormatter.format(adjustedDailyAllowanceCents),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DetailRowSmall(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    fontWeight: FontWeight = FontWeight.Medium
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
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = valueColor,
            fontWeight = fontWeight
        )
    }
}
