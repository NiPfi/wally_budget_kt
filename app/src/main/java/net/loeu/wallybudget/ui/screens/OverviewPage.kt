package net.loeu.wallybudget.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.loeu.wallybudget.data.model.BudgetState
import net.loeu.wallybudget.data.model.Expense
import net.loeu.wallybudget.data.model.SpendingForecast
import net.loeu.wallybudget.domain.config.ForecastConfig
import net.loeu.wallybudget.ui.components.AnimatedCounter
import net.loeu.wallybudget.ui.components.ForecastRangeIndicator
import net.loeu.wallybudget.util.CurrencyFormatter
import kotlin.math.abs

private const val STANDARD_AVAILABLE_HEIGHT_DP = 640f
private const val STANDARD_WIDTH_DP = 360f

/**
 * Minimum scale factor to prevent UI elements from becoming unreadable or 
 * disappearing on tiny screens.
 */
private const val MIN_SCALE_FACTOR = 0.5f

/**
 * Maximum scale factor to avoid excessive oversized elements on very large 
 * screens, maintaining a balanced layout.
 */
private const val MAX_SCALE_FACTOR = 1.2f

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
                                trendVal > ForecastConfig.TREND_SIGNIFICANCE_THRESHOLD_CENTS -> "Increasing (+${CurrencyFormatter.format(trendVal.toLong())}/day)"
                                trendVal < -ForecastConfig.TREND_SIGNIFICANCE_THRESHOLD_CENTS -> "Decreasing (-${CurrencyFormatter.format(abs(trendVal.toLong()))}/day)"
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

    // RESPONSIVE CONTAINER (NON-SCROLLABLE)
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val totalHeight = maxHeight
        val totalWidth = maxWidth
        
        // SAFE ZONE: The area occupied by the FAB and "Pull up" text.
        // We use a significant bottom padding to clear the UI overlays.
        val safeBottomPadding = 160.dp 
        
        // Calculate a responsive scale factor based on available space.
        val availableHeight = totalHeight.value - safeBottomPadding.value
        val scaleH = (availableHeight / STANDARD_AVAILABLE_HEIGHT_DP).coerceAtLeast(0.1f)
        val scaleW = (totalWidth.value / STANDARD_WIDTH_DP).coerceAtLeast(0.1f)
        
        // Scale factor that responds to both dimensions.
        // We allow it to go quite low to ensure it fits on small screens without scrolling.
        val scale = minOf(scaleH, scaleW).coerceIn(MIN_SCALE_FACTOR, MAX_SCALE_FACTOR)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = (16 * scale).dp, bottom = safeBottomPadding),
            verticalArrangement = Arrangement.spacedBy((10 * scale).dp)
        ) {
            // 1. Summary Card - Core Stats
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding((16 * scale).dp)) {
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
                    
                    Spacer(Modifier.height((10 * scale).dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
                    Spacer(Modifier.height((10 * scale).dp))
                    
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
                        Spacer(Modifier.height((4 * scale).dp))
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

            // 2. Spending Details Card
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding((12 * scale).dp)) {
                    Text(
                        text = "Cycle Progress", 
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = (15 * scale).sp), 
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height((2 * scale).dp))
                    Text(
                        text = CurrencyFormatter.format(budgetState.totalSpentThisCycleCents), 
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = (19 * scale).sp), 
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(Modifier.height((10 * scale).dp))
                    DetailRowSmall("Past Days", CurrencyFormatter.format(previousExpensesTotal), scale)
                    DetailRowSmall("Spent Today", CurrencyFormatter.format(budgetState.spentTodayCents), scale)
                    
                    Spacer(Modifier.height((6 * scale).dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(Modifier.height((6 * scale).dp))
                    
                    DetailRowSmall("Base Daily Allowance", CurrencyFormatter.format(budgetState.dailyBudgetCents), scale)
                    val adjColor = if (dailyAdjustmentCents >= 0L) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                    DetailRowSmall("Budget Adjustment", "${if (dailyAdjustmentCents >= 0L) "+" else "-"}${CurrencyFormatter.format(abs(dailyAdjustmentCents))}", scale, valueColor = adjColor)
                    
                    Spacer(Modifier.height((4 * scale).dp))
                    DetailRowSmall("Effective Allowance", CurrencyFormatter.format(adjustedDailyAllowanceCents), scale, fontWeight = FontWeight.ExtraBold)
                }
            }

            // 3. Forecast Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.2f, fill = false)
                    .clickable { showForecastDetails.value = true },
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding((12 * scale).dp)) {
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
                                Text("Uncertain", style = MaterialTheme.typography.labelMedium.copy(fontSize = (10 * scale).sp), color = MaterialTheme.colorScheme.error, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Bold)
                            } else {
                                Text("Details", style = MaterialTheme.typography.labelMedium.copy(fontSize = (10 * scale).sp), color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.Info, null, Modifier.size((16 * scale).dp), MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    
                    Spacer(Modifier.height((6 * scale).dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = CurrencyFormatter.format(abs(spendingForecast.estimatedEndCycleRemainingCents)),
                            style = MaterialTheme.typography.headlineMedium.copy(fontSize = (22 * scale).sp),
                            color = if (spendingForecast.isProjectedOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "est. ${if (spendingForecast.isProjectedOverBudget) "deficit" else "remaining"}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = (11 * scale).sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                    
                    Spacer(Modifier.height((10 * scale).dp))
                    ForecastRangeIndicator(
                        lowerBoundCents = spendingForecast.lowerBoundCents,
                        upperBoundCents = spendingForecast.upperBoundCents,
                        projectedCents = spendingForecast.projectedTotalSpentCents,
                        isOverBudget = spendingForecast.isProjectedOverBudget,
                        scale = scale
                    )
                    
                    Spacer(Modifier.height((12 * scale).dp))
                    
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding((10 * scale).dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val trendIcon = when {
                                spendingForecast.trendSlopeCents > ForecastConfig.TREND_SIGNIFICANCE_THRESHOLD_CENTS -> Icons.AutoMirrored.Filled.TrendingUp
                                spendingForecast.trendSlopeCents < -ForecastConfig.TREND_SIGNIFICANCE_THRESHOLD_CENTS -> Icons.AutoMirrored.Filled.TrendingDown
                                else -> Icons.AutoMirrored.Filled.TrendingFlat
                            }
                            val trendColor = when {
                                spendingForecast.trendSlopeCents > ForecastConfig.TREND_SIGNIFICANCE_THRESHOLD_CENTS -> MaterialTheme.colorScheme.error
                                spendingForecast.trendSlopeCents < -ForecastConfig.TREND_SIGNIFICANCE_THRESHOLD_CENTS -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            
                            Icon(
                                imageVector = trendIcon,
                                contentDescription = null,
                                modifier = Modifier.size((24 * scale).dp),
                                tint = trendColor
                            )
                            Spacer(Modifier.width((10 * scale).dp))
                            Text(
                                text = "${spendingForecast.confidenceRating} accuracy based on ${spendingForecast.usedDataPoints} days",
                                style = MaterialTheme.typography.labelLarge.copy(fontSize = (11 * scale).sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DetailRowSmall(
    label: String, 
    value: String, 
    scale: Float,
    valueColor: Color = Color.Unspecified, 
    fontWeight: FontWeight = FontWeight.Normal
) {
    Row(
        modifier = Modifier.fillMaxWidth(), 
        horizontalArrangement = Arrangement.SpaceBetween, 
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label, 
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = (12 * scale).sp), 
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value, 
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = (13 * scale).sp),
            color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor, 
            fontWeight = fontWeight,
            textAlign = TextAlign.End
        )
    }
}
