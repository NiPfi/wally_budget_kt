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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val MIN_VISUALIZATION_RANGE_CENTS = 100L

private data class ForecastRangePositions(
    val low: Float,
    val high: Float,
    val projected: Float
)

private data class ForecastRangeColors(
    val projected: androidx.compose.ui.graphics.Color,
    val lowerValue: androidx.compose.ui.graphics.Color,
    val lowerLabel: androidx.compose.ui.graphics.Color,
    val upperValue: androidx.compose.ui.graphics.Color,
    val upperLabel: androidx.compose.ui.graphics.Color
)

@Composable
fun ForecastRangeIndicator(
    lowerBoundCents: Long,
    upperBoundCents: Long,
    projectedCents: Long,
    budgetLimitCents: Long,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    scale: Float = 1.0f
) {
    if (isLoading) {
        ForecastRangeIndicatorPlaceholder(
            modifier = modifier,
            scale = scale
        )
        return
    }

    val positions = forecastRangePositions(lowerBoundCents, upperBoundCents, projectedCents)
    val colors = forecastRangeColors(
        lowerBoundCents = lowerBoundCents,
        upperBoundCents = upperBoundCents,
        projectedCents = projectedCents,
        budgetLimitCents = budgetLimitCents
    )

    Column(modifier = modifier.fillMaxWidth()) {
        ForecastRangeTrack(
            positions = positions,
            projectedColor = colors.projected,
            modifier = Modifier.fillMaxWidth(),
            scale = scale
        )
        ForecastRangeLabels(
            lowerBoundCents = lowerBoundCents,
            upperBoundCents = upperBoundCents,
            projectedCents = projectedCents,
            colors = colors,
            scale = scale
        )
    }
}

private fun forecastRangePositions(
    lowerBoundCents: Long,
    upperBoundCents: Long,
    projectedCents: Long
): ForecastRangePositions {
    val actualRange =
        (upperBoundCents - lowerBoundCents).coerceAtLeast(MIN_VISUALIZATION_RANGE_CENTS)
    val minView = (lowerBoundCents - actualRange * 0.25).coerceAtLeast(0.0).toLong()
    val maxView = (upperBoundCents + actualRange * 0.25).toLong()
    val viewWidth = (maxView - minView).coerceAtLeast(1L)
    return ForecastRangePositions(
        low = ((lowerBoundCents - minView).toDouble() / viewWidth).coerceIn(0.0, 1.0).toFloat(),
        high = ((upperBoundCents - minView).toDouble() / viewWidth).coerceIn(0.0, 1.0).toFloat(),
        projected = ((projectedCents - minView).toDouble() / viewWidth).coerceIn(0.0, 1.0)
            .toFloat()
    )
}

@Composable
private fun forecastRangeColors(
    lowerBoundCents: Long,
    upperBoundCents: Long,
    projectedCents: Long,
    budgetLimitCents: Long
): ForecastRangeColors {
    val projectedColor = if (projectedCents > budgetLimitCents) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val lowerColor = if (lowerBoundCents > budgetLimitCents) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val lowerLabelColor = if (lowerBoundCents > budgetLimitCents) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val upperColor = if (upperBoundCents > budgetLimitCents) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val upperLabelColor = if (upperBoundCents > budgetLimitCents) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    return ForecastRangeColors(
        projected = projectedColor,
        lowerValue = lowerColor,
        lowerLabel = lowerLabelColor,
        upperValue = upperColor,
        upperLabel = upperLabelColor
    )
}

@Composable
private fun ForecastRangeTrack(
    positions: ForecastRangePositions,
    projectedColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    scale: Float
) {
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    val rangeShade = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    Box(
        modifier = modifier.height((24 * scale).dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((2 * scale).dp)
                .align(Alignment.Center)
                .background(trackColor)
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerY = size.height / 2
            val startX = positions.low * size.width
            val endX = positions.high * size.width
            drawRect(
                color = rangeShade,
                topLeft = Offset(startX, centerY - (6 * scale).dp.toPx()),
                size = Size((endX - startX).coerceAtLeast(1f), (12 * scale).dp.toPx())
            )

            val lineHeight = (10 * scale).dp.toPx()
            drawLine(
                color = trackColor,
                start = Offset(startX, centerY - lineHeight / 2),
                end = Offset(startX, centerY + lineHeight / 2),
                strokeWidth = (1.5 * scale).dp.toPx()
            )
            drawLine(
                color = trackColor,
                start = Offset(endX, centerY - lineHeight / 2),
                end = Offset(endX, centerY + lineHeight / 2),
                strokeWidth = (1.5 * scale).dp.toPx()
            )

            val needleX = positions.projected * size.width
            drawRect(
                color = projectedColor,
                topLeft = Offset(needleX - (1 * scale).dp.toPx(), centerY - (10 * scale).dp.toPx()),
                size = Size((2 * scale).dp.toPx(), (20 * scale).dp.toPx())
            )
        }
    }
}

@Composable
private fun ForecastRangeLabels(
    lowerBoundCents: Long,
    upperBoundCents: Long,
    projectedCents: Long,
    colors: ForecastRangeColors,
    scale: Float
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = (4 * scale).dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ForecastRangeLabel(
            title = "Conservative",
            amountCents = lowerBoundCents,
            labelColor = colors.lowerLabel,
            valueColor = colors.lowerValue,
            textAlign = TextAlign.Start,
            fontSize = (12 * scale).sp,
            amountStyle = MaterialTheme.typography.bodySmall.copy(
                fontSize = (12 * scale).sp,
                fontWeight = FontWeight.Bold
            ),
            horizontalAlignment = Alignment.Start
        )
        ForecastRangeLabel(
            title = "Projected",
            amountCents = projectedCents,
            labelColor = colors.projected,
            valueColor = colors.projected,
            textAlign = TextAlign.Center,
            fontSize = (18 * scale).sp,
            amountStyle = MaterialTheme.typography.titleMedium.copy(
                fontSize = (18 * scale).sp,
                fontWeight = FontWeight.Black
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            emphasizeLabel = true
        )
        ForecastRangeLabel(
            title = "High Pace",
            amountCents = upperBoundCents,
            labelColor = colors.upperLabel,
            valueColor = colors.upperValue,
            textAlign = TextAlign.End,
            fontSize = (12 * scale).sp,
            amountStyle = MaterialTheme.typography.bodySmall.copy(
                fontSize = (12 * scale).sp,
                fontWeight = FontWeight.Bold
            ),
            horizontalAlignment = Alignment.End
        )
    }
}

@Composable
private fun ForecastRangeLabel(
    title: String,
    amountCents: Long,
    labelColor: androidx.compose.ui.graphics.Color,
    valueColor: androidx.compose.ui.graphics.Color,
    textAlign: TextAlign,
    fontSize: androidx.compose.ui.unit.TextUnit,
    amountStyle: androidx.compose.ui.text.TextStyle,
    horizontalAlignment: Alignment.Horizontal,
    emphasizeLabel: Boolean = false
) {
    Column(horizontalAlignment = horizontalAlignment) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = (11 * (fontSize.value / 12f)).sp),
            color = labelColor,
            fontWeight = if (emphasizeLabel) FontWeight.Bold else null
        )
        AnimatedCounter(
            amountCents = amountCents,
            textStyle = amountStyle,
            color = valueColor,
            animate = true,
            textAlign = textAlign
        )
    }
}

@Composable
private fun ForecastRangeIndicatorPlaceholder(
    modifier: Modifier = Modifier,
    scale: Float = 1f
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height((24 * scale).dp), contentAlignment = Alignment.Center) {
            LoadingValuePlaceholder(
                sampleText = "MMMMMMMMMMMM",
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = (12 * scale).sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                fillWidth = true
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = (4 * scale).dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = "Conservative",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = (11 * scale).sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LoadingValuePlaceholder(
                    sampleText = "$8,888",
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = (12 * scale).sp),
                    textAlign = TextAlign.Start
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Projected",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = (11 * scale).sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LoadingValuePlaceholder(
                    sampleText = "$8,888",
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontSize = (18 * scale).sp),
                    textAlign = TextAlign.Center
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "High Pace",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = (11 * scale).sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LoadingValuePlaceholder(
                    sampleText = "$8,888",
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = (12 * scale).sp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}
