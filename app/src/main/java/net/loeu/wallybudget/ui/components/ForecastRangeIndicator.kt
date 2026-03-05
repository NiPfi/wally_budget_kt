package net.loeu.wallybudget.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.loeu.wallybudget.util.CurrencyFormatter

/** 
 * Minimum range in cents to ensure a visible track even when confidence bounds are very narrow.
 */
private const val MIN_VISUALIZATION_RANGE_CENTS = 100L

@Composable
fun ForecastRangeIndicator(
    lowerBoundCents: Long,
    upperBoundCents: Long,
    projectedCents: Long,
    isOverBudget: Boolean,
    modifier: Modifier = Modifier,
    scale: Float = 1.0f
) {
    // Determine the visualization window. 
    val actualRange = (upperBoundCents - lowerBoundCents).coerceAtLeast(MIN_VISUALIZATION_RANGE_CENTS)
    val minView = (lowerBoundCents - actualRange * 0.25).coerceAtLeast(0.0).toLong()
    val maxView = (upperBoundCents + actualRange * 0.25).toLong()
    val viewWidth = (maxView - minView).coerceAtLeast(1L)

    val lowPos = ((lowerBoundCents - minView).toDouble() / viewWidth).coerceIn(0.0, 1.0).toFloat()
    val highPos = ((upperBoundCents - minView).toDouble() / viewWidth).coerceIn(0.0, 1.0).toFloat()
    val projPos = ((projectedCents - minView).toDouble() / viewWidth).coerceIn(0.0, 1.0).toFloat()

    // Color logic: neutral if not over budget, reddish if it is a deficit
    val indicatorColor = if (isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    
    // Visualization track and range use neutral colors to avoid conflicting implications in light/dark themes
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    val rangeShade = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((24 * scale).dp) // Slimmer visualization
        ) {
            // 1. Background track - Thin rectangle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((2 * scale).dp)
                    .align(Alignment.Center)
                    .background(trackColor)
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val centerY = h / 2

                // 2. Shaded Confidence Area - Rectangle (not rounded)
                val startX = lowPos * w
                val endX = highPos * w
                drawRect(
                    color = rangeShade,
                    topLeft = Offset(startX, centerY - (6 * scale).dp.toPx()),
                    size = Size((endX - startX).coerceAtLeast(1f), (12 * scale).dp.toPx())
                )

                // 3. Bound Markers (Vertical ticks) - Sharp rectangles
                val lineH = (10 * scale).dp.toPx()
                drawLine(
                    color = trackColor,
                    start = Offset(startX, centerY - lineH / 2),
                    end = Offset(startX, centerY + lineH / 2),
                    strokeWidth = (1.5 * scale).dp.toPx()
                )
                drawLine(
                    color = trackColor,
                    start = Offset(endX, centerY - lineH / 2),
                    end = Offset(endX, centerY + lineH / 2),
                    strokeWidth = (1.5 * scale).dp.toPx()
                )

                // 4. Expected Value Indicator (Needle) - Sharp rectangle
                val needleX = projPos * w
                drawRect(
                    color = indicatorColor,
                    topLeft = Offset(needleX - (1 * scale).dp.toPx(), centerY - (10 * scale).dp.toPx()),
                    size = Size((2 * scale).dp.toPx(), (20 * scale).dp.toPx())
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = (4 * scale).dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                Text("Conservative", style = MaterialTheme.typography.labelSmall.copy(fontSize = (11 * scale).sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(CurrencyFormatter.format(lowerBoundCents), style = MaterialTheme.typography.bodySmall.copy(fontSize = (12 * scale).sp), fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Projected", style = MaterialTheme.typography.labelSmall.copy(fontSize = (11 * scale).sp), color = indicatorColor, fontWeight = FontWeight.Bold)
                Text(CurrencyFormatter.format(projectedCents), style = MaterialTheme.typography.titleMedium.copy(fontSize = (18 * scale).sp), color = indicatorColor, fontWeight = FontWeight.Black)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("High Pace", style = MaterialTheme.typography.labelSmall.copy(fontSize = (11 * scale).sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(CurrencyFormatter.format(upperBoundCents), style = MaterialTheme.typography.bodySmall.copy(fontSize = (12 * scale).sp), fontWeight = FontWeight.Bold)
            }
        }
    }
}
