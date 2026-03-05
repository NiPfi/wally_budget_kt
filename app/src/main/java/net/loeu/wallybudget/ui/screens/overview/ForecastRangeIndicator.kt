package net.loeu.wallybudget.ui.screens.overview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.loeu.wallybudget.util.CurrencyFormatter

private const val MIN_VISUALIZATION_RANGE_CENTS = 100L

@Composable
fun ForecastRangeIndicator(
    lowerBoundCents: Long,
    upperBoundCents: Long,
    projectedCents: Long,
    budgetLimitCents: Long,
    modifier: Modifier = Modifier,
    scale: Float = 1.0f
) {
    val actualRange = (upperBoundCents - lowerBoundCents).coerceAtLeast(MIN_VISUALIZATION_RANGE_CENTS)
    val minView = (lowerBoundCents - actualRange * 0.25).coerceAtLeast(0.0).toLong()
    val maxView = (upperBoundCents + actualRange * 0.25).toLong()
    val viewWidth = (maxView - minView).coerceAtLeast(1L)

    val lowPos = ((lowerBoundCents - minView).toDouble() / viewWidth).coerceIn(0.0, 1.0).toFloat()
    val highPos = ((upperBoundCents - minView).toDouble() / viewWidth).coerceIn(0.0, 1.0).toFloat()
    val projPos = ((projectedCents - minView).toDouble() / viewWidth).coerceIn(0.0, 1.0).toFloat()

    val projectedColor = if (projectedCents > budgetLimitCents) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val lowerValueColor = if (lowerBoundCents > budgetLimitCents) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val lowerLabelColor = if (lowerBoundCents > budgetLimitCents) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    val upperValueColor = if (upperBoundCents > budgetLimitCents) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val upperLabelColor = if (upperBoundCents > budgetLimitCents) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    val rangeShade = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((24 * scale).dp)
        ) {
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

                val startX = lowPos * w
                val endX = highPos * w
                drawRect(
                    color = rangeShade,
                    topLeft = Offset(startX, centerY - (6 * scale).dp.toPx()),
                    size = Size((endX - startX).coerceAtLeast(1f), (12 * scale).dp.toPx())
                )

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

                val needleX = projPos * w
                drawRect(
                    color = projectedColor,
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
                Text(
                    text = "Conservative", 
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = (11 * scale).sp), 
                    color = lowerLabelColor
                )
                Text(
                    text = CurrencyFormatter.format(lowerBoundCents), 
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = (12 * scale).sp), 
                    color = lowerValueColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Projected", 
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = (11 * scale).sp), 
                    color = projectedColor, 
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = CurrencyFormatter.format(projectedCents), 
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = (18 * scale).sp), 
                    color = projectedColor, 
                    fontWeight = FontWeight.Black
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "High Pace", 
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = (11 * scale).sp), 
                    color = upperLabelColor
                )
                Text(
                    text = CurrencyFormatter.format(upperBoundCents), 
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = (12 * scale).sp), 
                    color = upperValueColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
