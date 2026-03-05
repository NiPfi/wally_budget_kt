package net.loeu.wallybudget.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.loeu.wallybudget.util.CurrencyFormatter

/** 
 * Minimum range in cents to ensure a visible track even when confidence bounds are very narrow.
 * This prevents divide-by-zero and provides a minimum visual context.
 * Pads the view window to show context even when forecast is highly certain
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
    // We show a window that is 50% larger than the actual range to provide context.
    val actualRange = (upperBoundCents - lowerBoundCents).coerceAtLeast(MIN_VISUALIZATION_RANGE_CENTS)
    val minView = (lowerBoundCents - actualRange * 0.25).coerceAtLeast(0.0).toLong()
    val maxView = (upperBoundCents + actualRange * 0.25).toLong()
    val viewWidth = (maxView - minView).coerceAtLeast(1L)

    val lowPos = ((lowerBoundCents - minView).toDouble() / viewWidth).coerceIn(0.0, 1.0).toFloat()
    val highPos = ((upperBoundCents - minView).toDouble() / viewWidth).coerceIn(0.0, 1.0).toFloat()
    val projPos = ((projectedCents - minView).toDouble() / viewWidth).coerceIn(0.0, 1.0).toFloat()

    val accentColor = if (isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    
    // High contrast track for light mode: using a more solid neutral color
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val rangeShade = accentColor.copy(alpha = 0.35f)

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((64 * scale).dp) 
        ) {
            // 1. Background track - Thicker and higher contrast
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((16 * scale).dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(trackColor)
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val centerY = h / 2
                val barH = (16 * scale).dp.toPx()

                // 2. Shaded Confidence Area
                val startX = lowPos * w
                val endX = highPos * w
                drawRoundRect(
                    color = rangeShade,
                    topLeft = Offset(startX, centerY - (barH / 2)),
                    size = Size((endX - startX).coerceAtLeast(barH), barH),
                    cornerRadius = CornerRadius(barH / 2, barH / 2)
                )

                // 3. Bound Markers (Vertical ticks)
                val lineH = (28 * scale).dp.toPx()
                drawLine(
                    color = accentColor.copy(alpha = 0.6f),
                    start = Offset(startX, centerY - lineH / 2),
                    end = Offset(startX, centerY + lineH / 2),
                    strokeWidth = (3 * scale).dp.toPx()
                )
                drawLine(
                    color = accentColor.copy(alpha = 0.6f),
                    start = Offset(endX, centerY - lineH / 2),
                    end = Offset(endX, centerY + lineH / 2),
                    strokeWidth = (3 * scale).dp.toPx()
                )

                // 4. Expected Value Indicator (Needle)
                val needleX = projPos * w
                drawRoundRect(
                    color = accentColor,
                    topLeft = Offset(needleX - (4 * scale).dp.toPx(), centerY - (20 * scale).dp.toPx()),
                    size = Size((8 * scale).dp.toPx(), (40 * scale).dp.toPx()),
                    cornerRadius = CornerRadius((4 * scale).dp.toPx(), (4 * scale).dp.toPx())
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
                Text("Projected", style = MaterialTheme.typography.labelSmall.copy(fontSize = (11 * scale).sp), color = accentColor, fontWeight = FontWeight.Bold)
                Text(CurrencyFormatter.format(projectedCents), style = MaterialTheme.typography.titleMedium.copy(fontSize = (18 * scale).sp), color = accentColor, fontWeight = FontWeight.Black)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("High Pace", style = MaterialTheme.typography.labelSmall.copy(fontSize = (11 * scale).sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(CurrencyFormatter.format(upperBoundCents), style = MaterialTheme.typography.bodySmall.copy(fontSize = (12 * scale).sp), fontWeight = FontWeight.Bold)
            }
        }
    }
}
