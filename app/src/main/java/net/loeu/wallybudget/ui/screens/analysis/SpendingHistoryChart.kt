@file:Suppress("LongMethod")

package net.loeu.wallybudget.ui.screens.analysis

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import net.loeu.wallybudget.domain.model.MonthlyHistory

private const val CHART_HEIGHT_DP = 108
private const val BOTTOM_PAD_DP = 22
private const val ANIM_DURATION_MS = 900
private const val BAR_GAP_DP = 7
private const val MIN_MAX_RATIO = 1.2
private const val NEAR_BUDGET_THRESHOLD = 1.05

@Composable
internal fun SpendingHistoryChart(
    history: List<MonthlyHistory>,
    modifier: Modifier = Modifier
) {
    val chronological = remember(history) { history.reversed() }

    var started by remember { mutableStateOf(false) }
    val animProgress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = ANIM_DURATION_MS),
        label = "history_bars_progress"
    )
    LaunchedEffect(Unit) { started = true }

    val underColor = MaterialTheme.colorScheme.primary
    val nearColor = MaterialTheme.colorScheme.tertiary
    val overColor = MaterialTheme.colorScheme.error
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT_DP.dp)
    ) {
        if (chronological.isEmpty()) return@Canvas
        val bottomPad = BOTTOM_PAD_DP.dp.toPx()
        val chartH = size.height - bottomPad
        val chartW = size.width
        val n = chronological.size
        val gapPx = BAR_GAP_DP.dp.toPx()
        val barWidth = (chartW - gapPx * (n - 1)) / n

        val maxRatio = chronological.maxOf { h ->
            if (h.budgetAmountCents > 0L) h.totalSpentCents.toDouble() / h.budgetAmountCents else 1.0
        }.coerceAtLeast(MIN_MAX_RATIO)

        chronological.forEachIndexed { i, cycle ->
            val barLeft = i * (barWidth + gapPx)
            val ratio = if (cycle.budgetAmountCents > 0L) {
                cycle.totalSpentCents.toDouble() / cycle.budgetAmountCents
            } else {
                0.0
            }
            val fillColor = when {
                cycle.surplusCents >= 0L -> underColor
                ratio < NEAR_BUDGET_THRESHOLD -> nearColor
                else -> overColor
            }
            drawHistoryBar(barLeft, barWidth, chartH, ratio, maxRatio, animProgress, fillColor, trackColor)
            drawMonthLabel(textMeasurer, labelStyle, labelColor, cycle.cycleStartDate, barLeft, barWidth, chartH)
        }

        val budgetLineY = (chartH * (1.0 - 1.0 / maxRatio)).toFloat()
        drawLine(
            color = lineColor,
            start = Offset(0f, budgetLineY),
            end = Offset(chartW, budgetLineY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(
                intervals = floatArrayOf(6.dp.toPx(), 4.dp.toPx()),
                phase = 0f
            )
        )
    }
}

private fun DrawScope.drawHistoryBar(
    x: Float,
    barWidth: Float,
    chartH: Float,
    ratio: Double,
    maxRatio: Double,
    animProgress: Float,
    fillColor: Color,
    trackColor: Color
) {
    val corner = CornerRadius(3.dp.toPx())
    drawRoundRect(color = trackColor, topLeft = Offset(x, 0f), size = Size(barWidth, chartH), cornerRadius = corner)
    val barH = (ratio / maxRatio * chartH * animProgress).toFloat().coerceAtLeast(0f)
    if (barH > 0f) {
        drawRoundRect(
            color = fillColor,
            topLeft = Offset(x, chartH - barH),
            size = Size(barWidth, barH),
            cornerRadius = corner
        )
    }
}

private fun DrawScope.drawMonthLabel(
    textMeasurer: TextMeasurer,
    labelStyle: androidx.compose.ui.text.TextStyle,
    labelColor: Color,
    dateStr: String,
    barLeft: Float,
    barWidth: Float,
    chartH: Float
) {
    val label = cycleMonthLabel(dateStr)
    val result = textMeasurer.measure(label, labelStyle)
    val labelX = barLeft + barWidth / 2f - result.size.width / 2f
    drawText(result, color = labelColor, topLeft = Offset(labelX, chartH + 4.dp.toPx()))
}

@Suppress("ReturnCount")
private fun cycleMonthLabel(dateStr: String): String {
    return try {
        val month = dateStr.split("-").getOrNull(1)?.toIntOrNull() ?: return ""
        when (month) {
            1 -> "Jan"; 2 -> "Feb"; 3 -> "Mar"; 4 -> "Apr"
            5 -> "May"; 6 -> "Jun"; 7 -> "Jul"; 8 -> "Aug"
            9 -> "Sep"; 10 -> "Oct"; 11 -> "Nov"; else -> "Dec"
        }
    } catch (_: Exception) {
        ""
    }
}

@Composable
internal fun RecentHistorySection(monthlyHistory: List<MonthlyHistory>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("analysis_history_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Recent cycles",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        SpendingHistoryChart(history = monthlyHistory.take(6))
        Text(
            text = "Green · under budget   Amber · near budget   Red · over budget",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
