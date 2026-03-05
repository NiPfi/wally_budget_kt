package net.loeu.wallybudget.ui.screens.overview

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.loeu.wallybudget.data.model.BudgetState
import net.loeu.wallybudget.util.CurrencyFormatter
import kotlin.math.abs

@Composable
fun SpendingDetailsCard(
    budgetState: BudgetState,
    previousExpensesTotal: Long,
    dailyAdjustmentCents: Long,
    adjustedDailyAllowanceCents: Long,
    scale: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding((10 * scale).dp)) {
            Text(
                text = "Cycle Progress",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = (15 * scale).sp),
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height((2 * scale).dp))
            Text(
                text = CurrencyFormatter.format(budgetState.totalSpentThisCycleCents),
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = (19 * scale).sp),
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height((6 * scale).dp))
            DetailRowSmall("Past Days", CurrencyFormatter.format(previousExpensesTotal), scale)
            DetailRowSmall("Spent Today", CurrencyFormatter.format(budgetState.spentTodayCents), scale)

            Spacer(Modifier.height((4 * scale).dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(Modifier.height((4 * scale).dp))

            DetailRowSmall("Base Daily Allowance", CurrencyFormatter.format(budgetState.dailyBudgetCents), scale)
            val adjColor = if (dailyAdjustmentCents >= 0L) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
            DetailRowSmall(
                "Budget Adjustment",
                "${if (dailyAdjustmentCents >= 0L) "+" else "-"}${CurrencyFormatter.format(abs(dailyAdjustmentCents))}",
                scale,
                valueColor = adjColor
            )

            Spacer(Modifier.height((4 * scale).dp))
            DetailRowSmall("Effective Allowance", CurrencyFormatter.format(adjustedDailyAllowanceCents), scale, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun DetailRowSmall(
    label: String,
    value: String,
    scale: Float,
    valueColor: Color = Color.Unspecified,
    fontWeight: FontWeight = FontWeight.Normal
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = (2 * scale).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = (11.5 * scale).sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = (13 * scale).sp),
            color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor,
            fontWeight = fontWeight,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = (8 * scale).dp)
        )
    }
}
