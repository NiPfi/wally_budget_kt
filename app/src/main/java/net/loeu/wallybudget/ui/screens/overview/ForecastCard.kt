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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.loeu.wallybudget.data.model.BudgetState
import net.loeu.wallybudget.data.model.SpendingForecast
import net.loeu.wallybudget.domain.config.ForecastConfig

@Composable
fun ForecastCard(
    spendingForecast: SpendingForecast,
    displayedRecoverableOverspendCents: Long,
    budgetState: BudgetState,
    isLoading: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !isLoading, onClick = onClick)
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
                val isLowConfidence = spendingForecast.confidenceScore < ForecastConfig.MIN_CONFIDENCE_THRESHOLD
                val indicatorIcon = if (isLoading || !isLowConfidence) {
                    Icons.Default.Info
                } else {
                    Icons.Default.Warning
                }
                val indicatorTint = when {
                    isLoading -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    isLowConfidence -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                }
                Icon(
                    imageVector = indicatorIcon,
                    contentDescription = null,
                    tint = indicatorTint
                )
                Text(
                    text = "Details",
                    style = MaterialTheme.typography.labelLarge,
                    color = indicatorTint,
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
            AnimatedCounter(
                amountCents = spendingForecast.estimatedEndCycleRemainingCents,
                signed = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Black
                ),
                color = if (spendingForecast.isProjectedOverBudget) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                animate = false,
                textAlign = TextAlign.Start,
                placeholder = isLoading,
                placeholderText = "$8,888"
            )
        }

        ForecastRangeIndicator(
            lowerBoundCents = spendingForecast.lowerBoundCents,
            upperBoundCents = spendingForecast.upperBoundCents,
            projectedCents = spendingForecast.projectedTotalSpentCents,
            budgetLimitCents = budgetState.monthlyBudgetCents,
            isLoading = isLoading,
            scale = 1f
        )

        if (displayedRecoverableOverspendCents > 0L || isLoading) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.Top
            ) {
                ForecastMetaMetric(
                    label = "Recoverable overspend"
                ) {
                    AnimatedCounter(
                        amountCents = displayedRecoverableOverspendCents,
                        textStyle = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        animate = false,
                        placeholder = isLoading,
                        placeholderText = "$888"
                    )
                }
            }
        }
    }
}

@Composable
private fun ForecastMetaMetric(
    label: String,
    alignEnd: Boolean = false,
    valueContent: @Composable () -> Unit
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
        valueContent()
    }
}
