package net.loeu.wallybudget.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.loeu.wallybudget.data.model.BudgetState
import net.loeu.wallybudget.util.CurrencyFormatter
import kotlin.math.abs

@Composable
fun SummaryCard(
    budgetState: BudgetState,
    scale: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding((12 * scale).dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Days Left",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = (11 * scale).sp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Text(
                        text = budgetState.daysRemainingInCycle.toString(),
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = (24 * scale).sp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Cycle Left",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = (11 * scale).sp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Text(
                        text = CurrencyFormatter.format(abs(budgetState.remainingCycleCents)),
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = (24 * scale).sp),
                        color = if (budgetState.remainingCycleCents >= 0L) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height((6 * scale).dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
            Spacer(Modifier.height((6 * scale).dp))

            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "TODAY'S BUDGET",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = (12 * scale).sp,
                        letterSpacing = (1.5 * scale).sp
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height((2 * scale).dp))
                AnimatedCounter(
                    amountCents = budgetState.remainingTodayCents,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.displayLarge.copy(fontSize = (44 * scale).sp),
                    color = if (budgetState.remainingTodayCents >= 0L) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
