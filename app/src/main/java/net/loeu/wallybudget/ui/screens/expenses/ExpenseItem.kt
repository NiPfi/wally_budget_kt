package net.loeu.wallybudget.ui.screens.expenses

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import net.loeu.wallybudget.data.model.Expense
import net.loeu.wallybudget.data.model.description
import net.loeu.wallybudget.data.model.iconRes
import net.loeu.wallybudget.util.CurrencyFormatter
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ExpenseItem(
    expense: Expense,
    modifier: Modifier = Modifier,
    onEdit: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { onEdit?.invoke() }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = expense.icon.iconRes),
                contentDescription = expense.icon.description,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))

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

            Text(
                text = CurrencyFormatter.format(expense.amountCents),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
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
