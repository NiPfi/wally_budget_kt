package net.loeu.wallybudget.ui.screens.overview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.loeu.wallybudget.data.model.BudgetState
import net.loeu.wallybudget.data.model.SpendingForecast
import net.loeu.wallybudget.domain.config.ForecastConfig
import net.loeu.wallybudget.util.CurrencyFormatter
import kotlin.math.abs

@Composable
fun ForecastCard(
    spendingForecast: SpendingForecast,
    budgetState: BudgetState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Forecast",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = if (spendingForecast.confidenceScore < ForecastConfig.MIN_CONFIDENCE_THRESHOLD) {
                    Icons.Default.Warning
                } else {
                    Icons.Default.Info
                }
                val tint = if (spendingForecast.confidenceScore < ForecastConfig.MIN_CONFIDENCE_THRESHOLD) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint
                )
                Text(
                    text = "Details",
                    style = MaterialTheme.typography.labelLarge,
                    color = tint,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }

        Text(
            text = CurrencyFormatter.format(abs(spendingForecast.estimatedEndCycleRemainingCents)),
            style = MaterialTheme.typography.displaySmall,
            color = if (spendingForecast.isProjectedOverBudget) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            fontWeight = FontWeight.Black
        )

        Text(
            text = if (spendingForecast.isProjectedOverBudget) {
                "Projected deficit by cycle end"
            } else {
                "Projected remaining budget by cycle end"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ForecastRangeIndicator(
            lowerBoundCents = spendingForecast.lowerBoundCents,
            upperBoundCents = spendingForecast.upperBoundCents,
            projectedCents = spendingForecast.projectedTotalSpentCents,
            budgetLimitCents = budgetState.monthlyBudgetCents,
            scale = 1f
        )
    }
}
