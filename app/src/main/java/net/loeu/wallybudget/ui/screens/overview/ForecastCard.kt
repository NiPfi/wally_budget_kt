package net.loeu.wallybudget.ui.screens.overview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.loeu.wallybudget.R
import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.domain.config.ForecastConfig
import net.loeu.wallybudget.domain.model.SpendingForecast
import net.loeu.wallybudget.ui.CurrencyPlaceholderSamples

@Composable
fun ForecastCard(
    spendingForecast: SpendingForecast,
    budgetState: BudgetState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false
) {
    val isLowConfidence = spendingForecast.confidenceScore < ForecastConfig.MIN_CONFIDENCE_THRESHOLD
    val detailsStateDescription = when {
        isLoading -> "Forecast details loading"
        isLowConfidence -> "Low confidence forecast"
        else -> "Forecast confidence normal"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("forecast_card")
            .semantics { stateDescription = detailsStateDescription }
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ForecastCardHeader(
            isLoading = isLoading,
            isLowConfidence = isLowConfidence
        )
        ForecastCardSummary(
            spendingForecast = spendingForecast,
            isLoading = isLoading
        )

        ForecastRangeIndicator(
            lowerBoundCents = spendingForecast.lowerBoundCents,
            upperBoundCents = spendingForecast.upperBoundCents,
            projectedCents = spendingForecast.projectedTotalSpentCents,
            budgetLimitCents = budgetState.monthlyBudgetCents,
            isLoading = isLoading,
            scale = 1f
        )
    }
}

@Composable
private fun ForecastCardHeader(
    isLoading: Boolean,
    isLowConfidence: Boolean
) {
    val indicatorIconRes = if (isLoading || !isLowConfidence) {
        R.drawable.ic_info
    } else {
        R.drawable.ic_warning
    }
    val indicatorTint = when {
        isLoading -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        isLowConfidence -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

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
            Icon(
                painter = painterResource(indicatorIconRes),
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
}

@Composable
private fun ForecastCardSummary(
    spendingForecast: SpendingForecast,
    isLoading: Boolean
) {
    val isProjectedOverBudget = spendingForecast.isProjectedOverBudget

    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = if (isProjectedOverBudget) {
                "PROJECTED DEFICIT"
            } else {
                "PROJECTED LEFT"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (isProjectedOverBudget) {
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
            color = if (isProjectedOverBudget) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            animate = true,
            textAlign = TextAlign.Start,
            placeholder = isLoading,
            placeholderText = CurrencyPlaceholderSamples.amount(888_800L)
        )
    }
}
