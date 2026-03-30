@file:Suppress("LongMethod")

package net.loeu.wallybudget.ui.screens.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import net.loeu.wallybudget.domain.model.MonthlyHistory
import net.loeu.wallybudget.ui.charts.drawRoundedVerticalTrackBar
import net.loeu.wallybudget.ui.charts.rememberChartRevealProgress

private const val CHART_HEIGHT_DP = 108
private const val BOTTOM_PAD_DP = 22
private const val ANIM_DURATION_MS = 900
private const val BAR_GAP_DP = 7
private const val MIN_MAX_RATIO = 1.2
private const val NEAR_BUDGET_THRESHOLD = 1.05

internal enum class SpendingHistoryBarTone {
    UnderBudget,
    NearBudget,
    OverBudget
}

internal data class SpendingHistoryBarLayout(
    val left: Float,
    val width: Float,
    val fillHeight: Float,
    val tone: SpendingHistoryBarTone,
    val monthLabel: String
)

internal data class SpendingHistoryChartLayout(
    val chartWidth: Float,
    val chartHeight: Float,
    val budgetLineY: Float,
    val bars: List<SpendingHistoryBarLayout>
)

@Composable
internal fun SpendingHistoryChart(
    history: List<MonthlyHistory>,
    modifier: Modifier = Modifier
) {
    val chronological = remember(history) { history.reversed() }
    val animProgress = rememberChartRevealProgress(
        durationMillis = ANIM_DURATION_MS,
        label = "history_bars_progress"
    )

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
        val gapPx = BAR_GAP_DP.dp.toPx()
        val layout = computeSpendingHistoryChartLayout(
            history = chronological,
            chartWidth = size.width,
            chartHeight = size.height - bottomPad,
            gapPx = gapPx,
            animProgress = animProgress
        )

        layout.bars.forEach { bar ->
            val fillColor = when (bar.tone) {
                SpendingHistoryBarTone.UnderBudget -> underColor
                SpendingHistoryBarTone.NearBudget -> nearColor
                else -> overColor
            }
            drawHistoryBar(
                bar = bar,
                chartHeight = layout.chartHeight,
                fillColor = fillColor,
                trackColor = trackColor
            )
            drawMonthLabel(
                textMeasurer = textMeasurer,
                labelStyle = labelStyle,
                labelColor = labelColor,
                label = bar.monthLabel,
                barLeft = bar.left,
                barWidth = bar.width,
                chartHeight = layout.chartHeight
            )
        }

        drawLine(
            color = lineColor,
            start = Offset(0f, layout.budgetLineY),
            end = Offset(layout.chartWidth, layout.budgetLineY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(
                intervals = floatArrayOf(6.dp.toPx(), 4.dp.toPx()),
                phase = 0f
            )
        )
    }
}

internal fun computeSpendingHistoryChartLayout(
    history: List<MonthlyHistory>,
    chartWidth: Float,
    chartHeight: Float,
    gapPx: Float,
    animProgress: Float = 1f
): SpendingHistoryChartLayout {
    if (history.isEmpty()) {
        return SpendingHistoryChartLayout(
            chartWidth = chartWidth,
            chartHeight = chartHeight,
            budgetLineY = chartHeight,
            bars = emptyList()
        )
    }

    val safeChartWidth = chartWidth.coerceAtLeast(0f)
    val safeChartHeight = chartHeight.coerceAtLeast(0f)
    val barCount = history.size
    val barWidth = ((safeChartWidth - gapPx * (barCount - 1)) / barCount).coerceAtLeast(0f)
    val maxRatio = history.maxOf { cycle ->
        spendingToBudgetScaleRatio(cycle)
    }.coerceAtLeast(MIN_MAX_RATIO)
    val clampedAnimProgress = animProgress.coerceIn(0f, 1f)

    return SpendingHistoryChartLayout(
        chartWidth = safeChartWidth,
        chartHeight = safeChartHeight,
        budgetLineY = (safeChartHeight * (1.0 - 1.0 / maxRatio)).toFloat(),
        bars = history.mapIndexed { index, cycle ->
            val ratio = spendingToBudgetBarRatio(cycle)
            SpendingHistoryBarLayout(
                left = index * (barWidth + gapPx),
                width = barWidth,
                fillHeight = (ratio / maxRatio * safeChartHeight * clampedAnimProgress).toFloat().coerceAtLeast(0f),
                tone = spendingHistoryBarTone(cycle, ratio),
                monthLabel = spendingHistoryMonthLabel(cycle.cycleStartDate)
            )
        }
    )
}

internal fun spendingHistoryBarTone(
    cycle: MonthlyHistory,
    ratio: Double = spendingToBudgetBarRatio(cycle)
): SpendingHistoryBarTone = when {
    cycle.surplusCents >= 0L -> SpendingHistoryBarTone.UnderBudget
    ratio < NEAR_BUDGET_THRESHOLD -> SpendingHistoryBarTone.NearBudget
    else -> SpendingHistoryBarTone.OverBudget
}

internal fun spendingHistoryMonthLabel(dateStr: String): String {
    return dateStr.split("-")
        .getOrNull(1)
        ?.toIntOrNull()
        ?.let(::monthNumberToLabel)
        .orEmpty()
}

private fun spendingToBudgetScaleRatio(cycle: MonthlyHistory): Double {
    return if (cycle.budgetAmountCents > 0L) {
        cycle.totalSpentCents.toDouble() / cycle.budgetAmountCents
    } else {
        1.0
    }
}

private fun spendingToBudgetBarRatio(cycle: MonthlyHistory): Double {
    return if (cycle.budgetAmountCents > 0L) {
        cycle.totalSpentCents.toDouble() / cycle.budgetAmountCents
    } else {
        0.0
    }
}

private fun DrawScope.drawHistoryBar(
    bar: SpendingHistoryBarLayout,
    chartHeight: Float,
    fillColor: Color,
    trackColor: Color
) {
    val corner = CornerRadius(3.dp.toPx())
    drawRoundedVerticalTrackBar(
        trackColor = trackColor,
        fillColor = fillColor,
        x = bar.left,
        barWidth = bar.width,
        chartHeight = chartHeight,
        fillHeight = bar.fillHeight,
        cornerRadius = corner
    )
}

private fun DrawScope.drawMonthLabel(
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
    labelColor: Color,
    label: String,
    barLeft: Float,
    barWidth: Float,
    chartHeight: Float
) {
    val result = textMeasurer.measure(label, labelStyle)
    val labelX = barLeft + barWidth / 2f - result.size.width / 2f
    drawText(result, color = labelColor, topLeft = Offset(labelX, chartHeight + 4.dp.toPx()))
}

private fun monthNumberToLabel(month: Int): String = when (month) {
    1 -> "Jan"
    2 -> "Feb"
    3 -> "Mar"
    4 -> "Apr"
    5 -> "May"
    6 -> "Jun"
    7 -> "Jul"
    8 -> "Aug"
    9 -> "Sep"
    10 -> "Oct"
    11 -> "Nov"
    12 -> "Dec"
    else -> ""
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
