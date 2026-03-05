package net.loeu.wallybudget.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.loeu.wallybudget.data.model.BudgetState
import net.loeu.wallybudget.data.model.SpendingForecast
import net.loeu.wallybudget.domain.config.ForecastConfig
import net.loeu.wallybudget.util.CurrencyFormatter
import kotlin.math.abs

@Composable
fun ForecastCard(
    spendingForecast: SpendingForecast,
    budgetState: BudgetState,
    scale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding((10 * scale).dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Forecast",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = (15 * scale).sp),
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(4.dp)
                ) {
                    if (spendingForecast.confidenceScore < ForecastConfig.MIN_CONFIDENCE_THRESHOLD) {
                        Icon(Icons.Default.Warning, null, Modifier.size((16 * scale).dp), MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "${spendingForecast.confidenceRating} Accuracy",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = (10 * scale).sp),
                            color = MaterialTheme.colorScheme.error,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text("Details", style = MaterialTheme.typography.labelMedium.copy(fontSize = (10 * scale).sp), color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.Info, null, Modifier.size((16 * scale).dp), MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(Modifier.height((2 * scale).dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = CurrencyFormatter.format(abs(spendingForecast.estimatedEndCycleRemainingCents)),
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = (22 * scale).sp),
                    color = if (spendingForecast.isProjectedOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "est. ${if (spendingForecast.isProjectedOverBudget) "deficit" else "remaining"}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = (11 * scale).sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = (3 * scale).dp)
                )
            }

            Spacer(Modifier.height((6 * scale).dp))
            ForecastRangeIndicator(
                lowerBoundCents = spendingForecast.lowerBoundCents,
                upperBoundCents = spendingForecast.upperBoundCents,
                projectedCents = spendingForecast.projectedTotalSpentCents,
                budgetLimitCents = budgetState.monthlyBudgetCents,
                scale = scale
            )
        }
    }
}
