package net.loeu.wallybudget.ui.screens.expenses

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import net.loeu.wallybudget.data.local.entity.Expense
import net.loeu.wallybudget.domain.model.description
import net.loeu.wallybudget.domain.model.iconRes
import net.loeu.wallybudget.ui.screens.overview.AnimatedCounter
import net.loeu.wallybudget.util.CurrencyFormatter
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ExpenseItem(
    expense: Expense,
    modifier: Modifier = Modifier,
    animateAmount: Boolean = false,
    showDivider: Boolean = true,
    onEdit: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { onEdit?.invoke() }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = expense.icon.iconRes),
                contentDescription = expense.icon.description,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = expense.description,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = formatTime(expense.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (animateAmount) {
                AnimatedCounter(
                    amountCents = expense.amountCents,
                    textStyle = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    text = CurrencyFormatter.format(expense.amountCents),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val zonedDateTime = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
    val localTime = zonedDateTime.toLocalTime()

    if (localTime == LocalTime.MIDNIGHT) {
        return "—"
    }

    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    return localTime.format(formatter)
}
