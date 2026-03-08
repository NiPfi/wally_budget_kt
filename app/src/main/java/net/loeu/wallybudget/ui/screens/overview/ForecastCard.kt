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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.loeu.wallybudget.data.model.BudgetState
import net.loeu.wallybudget.data.model.SpendingForecast
import net.loeu.wallybudget.domain.config.ForecastConfig
import net.loeu.wallybudget.util.CurrencyFormatter

@Composable
fun ForecastCard(
    spendingForecast: SpendingForecast,
    displayedRecoverableOverspendCents: Long,
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

        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = if (spendingForecast.isProjectedOverBudget) {
                    "PROJECTED DEFICIT"
                } else {
                    "PROJECTED LEFT"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (spendingForecast.isProjectedOverBudget) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.78f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = CurrencyFormatter.formatSigned(spendingForecast.estimatedEndCycleRemainingCents),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 18.sp,
                    lineHeight = 24.sp
                ),
                color = if (spendingForecast.isProjectedOverBudget) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontWeight = FontWeight.Black
            )
        }

        ForecastRangeIndicator(
            lowerBoundCents = spendingForecast.lowerBoundCents,
            upperBoundCents = spendingForecast.upperBoundCents,
            projectedCents = spendingForecast.projectedTotalSpentCents,
            budgetLimitCents = budgetState.monthlyBudgetCents,
            scale = 1f
        )

        if (displayedRecoverableOverspendCents > 0L) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.Top
            ) {
                ForecastMetaMetric(
                    label = "Recoverable overspend",
                    value = CurrencyFormatter.format(displayedRecoverableOverspendCents),
                    valueColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ForecastMetaMetric(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    alignEnd: Boolean = false
) {
    Column(
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = valueColor,
            fontWeight = FontWeight.Bold
        )
    }
}
