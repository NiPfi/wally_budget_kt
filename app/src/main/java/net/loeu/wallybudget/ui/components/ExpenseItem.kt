package net.loeu.wallybudget.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    onEdit: (() -> Unit)? = null,
    modifier: Modifier = Modifier
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
            // Icon
            Icon(
                painter = painterResource(id = expense.icon.iconRes),
                contentDescription = expense.icon.description,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))

            // Description and time
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

            // Amount
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

    // If the time is exactly midnight (00:00), it indicates an expense added to a different 
    // day than its creation date (backdated), as these are stored at the start of the day.
    // In such cases, we show a dash as the specific time is not recorded or relevant.
    if (localTime == LocalTime.MIDNIGHT) {
        return "—"
    }

    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    return localTime.format(formatter)
}
