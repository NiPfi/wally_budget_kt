package net.loeu.wallybudget.ui.screens.overview

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.loeu.wallybudget.data.model.BudgetState
import net.loeu.wallybudget.data.model.Expense
import net.loeu.wallybudget.data.model.SpendingForecast
import net.loeu.wallybudget.domain.config.ForecastConfig
import net.loeu.wallybudget.util.CurrencyFormatter
import kotlin.math.abs

private const val STANDARD_AVAILABLE_HEIGHT_DP = 560f
private const val STANDARD_WIDTH_DP = 360f
private const val MIN_SCALE_FACTOR = 0.7f
private const val MAX_SCALE_FACTOR = 1.6f
// Factor to allow the UI to scale more based on width on tall devices, preventing excessive vertical stretching
private const val WIDTH_SCALE_BIAS = 1.3f 

@Composable
fun OverviewPage(
    budgetState: BudgetState,
    previousCycleExpenses: List<Expense>,
    spendingForecast: SpendingForecast,
    modifier: Modifier = Modifier
) {
    val previousExpensesTotal = remember(previousCycleExpenses) {
        previousCycleExpenses.sumOf { it.amountCents }
    }
    val adjustedDailyAllowanceCents = remember(budgetState.remainingTodayCents, budgetState.spentTodayCents) {
        budgetState.remainingTodayCents + budgetState.spentTodayCents
    }
    val dailyAdjustmentCents = remember(adjustedDailyAllowanceCents, budgetState.dailyBudgetCents) {
        adjustedDailyAllowanceCents - budgetState.dailyBudgetCents
    }

    val showForecastDetails = remember { mutableStateOf(false) }

    if (showForecastDetails.value) {
        AlertDialog(
            onDismissRequest = { showForecastDetails.value = false },
            title = { Text("Forecast Analysis") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "This projection analyzes your spending patterns using recent daily expenses and historical cycle data to estimate your final cycle balance.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            DetailRow("Confidence", "${(spendingForecast.confidenceScore * 100).toInt()}% (${spendingForecast.confidenceRating})")
                            DetailRow("Avg Daily Pace", CurrencyFormatter.format(spendingForecast.dailyAverageWeightedCents))
                            
                            val trendVal = spendingForecast.trendSlopeCents
                            val trendText = when {
                                trendVal > ForecastConfig.TREND_SIGNIFICANCE_THRESHOLD_CENTS -> "Increasing\n(+${CurrencyFormatter.format(trendVal.toLong())}/day)"
                                trendVal < -ForecastConfig.TREND_SIGNIFICANCE_THRESHOLD_CENTS -> "Decreasing\n(-${CurrencyFormatter.format(abs(trendVal.toLong()))}/day)"
                                else -> "Stable"
                            }
                            DetailRow("Spending Trend", trendText)
                            DetailRow("Analyzed Window", "${spendingForecast.usedDataPoints} days")
                            
                            if (spendingForecast.detectedOutlierCount > 0) {
                                DetailRow("Excluded Anomalies", "${spendingForecast.detectedOutlierCount}")
                            }
                        }
                    }
                    
                    Text(
                        "Consistency in tracking improves accuracy. More data points will refine these estimates automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showForecastDetails.value = false }) {
                    Text("Got it")
                }
            },
            icon = { Icon(Icons.Default.Info, contentDescription = null) }
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val totalHeight = maxHeight
        val totalWidth = maxWidth
        // Padding to avoid the bottom interactive handle and FAB area
        val safeBottomPadding = 88.dp
        
        val availableHeight = totalHeight.value - safeBottomPadding.value
        val scaleH = (availableHeight / STANDARD_AVAILABLE_HEIGHT_DP).coerceAtLeast(0.1f)
        val scaleW = (totalWidth.value / STANDARD_WIDTH_DP).coerceAtLeast(0.1f)
        
        // We calculate scale based on height/width ratio. WIDTH_SCALE_BIAS allows the UI 
        // to grow larger on modern tall screens while keeping layout proportional.
        val scale = minOf(scaleH, scaleW * WIDTH_SCALE_BIAS).coerceIn(MIN_SCALE_FACTOR, MAX_SCALE_FACTOR)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = safeBottomPadding),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // weights with fill = false allow cards to use their intrinsic scaled height 
            // but still be distributed flexibly within the available column space.
            SummaryCard(
                budgetState = budgetState,
                scale = scale,
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
            )

            SpendingDetailsCard(
                budgetState = budgetState,
                previousExpensesTotal = previousExpensesTotal,
                dailyAdjustmentCents = dailyAdjustmentCents,
                adjustedDailyAllowanceCents = adjustedDailyAllowanceCents,
                scale = scale,
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
            )

            ForecastCard(
                spendingForecast = spendingForecast,
                budgetState = budgetState,
                scale = scale,
                onClick = { showForecastDetails.value = true },
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(), 
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label, 
            style = MaterialTheme.typography.bodyMedium, 
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp)
        )
        Text(
            text = value, 
            style = MaterialTheme.typography.bodyLarge, 
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}
