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

/**
 * Minimum scale factor to prevent UI from becoming unreadable on extremely small screens.
 */
private const val MIN_SCALE_FACTOR = 0.7f

/**
 * Maximum scale factor to prevent UI from appearing bloated on very large screens.
 */
private const val MAX_SCALE_FACTOR = 1.6f

/**
 * Baseline aggregate height of all cards and their vertical margins at scale 1.0.
 * Estimated as: SummaryCard (~200dp) + SpendingDetailsCard (~230dp) + ForecastCard (~170dp) + spacing (~40dp) = 640dp.
 * Note: These are estimated values and should be validated against actual card implementations.
 * Increasing this value makes the scale more conservative, preventing content cutoff.
 */
private const val REFERENCE_CONTENT_HEIGHT_DP = 640f

/**
 * Baseline width of a standard smartphone screen. Used to calculate horizontal scale factor.
 */
private const val REFERENCE_CONTENT_WIDTH_DP = 360f

/**
 * Factor allowing the UI to scale slightly more on tall devices to fill vertical space 
 * without breaking proportions.
 */
private const val WIDTH_SCALE_BIAS = 1.3f

/**
 * Proportion of total screen height reserved for bottom interactive elements.
 * For illustration, 12% of a standard 800dp screen is ~96dp, providing ample 
 * clearance for the Pull handle (48dp) and FAB (56dp) area.
 */
private const val SAFE_ZONE_HEIGHT_RATIO = 0.12f

/**
 * Minimum absolute height for the bottom safe zone to ensure interactive elements are always clear.
 */
private const val MIN_SAFE_ZONE_HEIGHT_DP = 72f

/**
 * Maximum absolute height for the bottom safe zone to prevent wasting space on extremely tall screens.
 */
private const val MAX_SAFE_ZONE_HEIGHT_DP = 100f

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
        
        // Dynamically calculate bottom padding to protect the FAB and pull-handle area.
        // Dp arithmetic is used directly to maintain type safety throughout the calculation.
        val safeBottomPadding = (totalHeight * SAFE_ZONE_HEIGHT_RATIO)
            .coerceIn(MIN_SAFE_ZONE_HEIGHT_DP.dp, MAX_SAFE_ZONE_HEIGHT_DP.dp)
        
        val availableHeight = totalHeight - safeBottomPadding
        
        // Calculate scale factors using raw Dp values for explicit Float conversion
        val scaleH = availableHeight.value / REFERENCE_CONTENT_HEIGHT_DP
        val scaleW = totalWidth.value / REFERENCE_CONTENT_WIDTH_DP
        
        // The final scale favors height utilization while respecting width constraints.
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
