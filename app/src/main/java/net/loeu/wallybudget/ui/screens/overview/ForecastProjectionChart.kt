package net.loeu.wallybudget.ui.screens.overview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.loeu.wallybudget.ui.charts.rememberChartRevealProgress
import net.loeu.wallybudget.util.CurrencyFormatter

private const val CHART_HEIGHT_DP = 108
private const val BOTTOM_PAD_DP = 22
private const val ANIM_DURATION_MS = 1400
private const val TOP_MARGIN_RATIO = 1.10
private const val MIN_VISUALIZATION_MAX_CENTS = 1.0

internal data class ChartLayout(
    val chartW: Float,
    val chartH: Float,
    val todayX: Float,
    val budgetY: Float,
    val spentY: Float,
    val projectedEndY: Float,
    val lowerEndY: Float,
    val upperEndY: Float,
)

private data class ChartColors(
    val history: Color,
    val projection: Color,
    val cone: Color,
    val lowerBound: Color,
    val upperBound: Color,
    val budgetLine: Color,
    val todayMarker: Color,
    val endDotOuter: Color,
    val endDotInner: Color,
    val labelSecondary: Color,
)

internal data class ChartParams(
    val totalDays: Int,
    val daysElapsed: Int,
    val budgetCents: Long,
    val totalSpentCents: Long,
    val projectedCents: Long,
    val lowerBoundCents: Long,
    val upperBoundCents: Long,
)

@Composable
private fun chartColors(isOverBudget: Boolean): ChartColors {
    val primary = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error
    val outline = MaterialTheme.colorScheme.outlineVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val active = if (isOverBudget) error else primary
    return ChartColors(
        history = active,
        projection = active,
        cone = active.copy(alpha = 0.10f),
        lowerBound = onSurfaceVariant.copy(alpha = 0.45f),
        upperBound = if (isOverBudget) error.copy(alpha = 0.40f) else onSurfaceVariant.copy(alpha = 0.45f),
        budgetLine = error.copy(alpha = 0.55f),
        todayMarker = outline,
        endDotOuter = active,
        endDotInner = surface,
        labelSecondary = onSurfaceVariant,
    )
}

internal fun computeChartLayout(params: ChartParams, chartW: Float, chartH: Float): ChartLayout {
    val maxAmount = maxOf(params.budgetCents, params.upperBoundCents, params.totalSpentCents, params.projectedCents)
    val maxCents = maxOf(maxAmount.toDouble() * TOP_MARGIN_RATIO, MIN_VISUALIZATION_MAX_CENTS)
    val inclusiveTodayDayIndex = (params.daysElapsed + 1).coerceAtMost(params.totalDays)
    fun dayToX(day: Int): Float = (day.toFloat() / params.totalDays) * chartW
    fun centsToY(cents: Long): Float = (chartH * (1.0 - cents.toDouble() / maxCents)).toFloat()
    return ChartLayout(
        chartW = chartW,
        chartH = chartH,
        todayX = dayToX(inclusiveTodayDayIndex),
        budgetY = centsToY(params.budgetCents),
        spentY = centsToY(params.totalSpentCents),
        projectedEndY = centsToY(params.projectedCents),
        lowerEndY = centsToY(params.lowerBoundCents),
        upperEndY = centsToY(params.upperBoundCents),
    )
}

@Suppress("LongParameterList")
@Composable
fun ForecastProjectionChart(
    totalSpentCents: Long,
    daysElapsed: Int,
    totalDaysInCycle: Int,
    budgetLimitCents: Long,
    projectedCents: Long,
    lowerBoundCents: Long,
    upperBoundCents: Long,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
) {
    if (isLoading) {
        ForecastProjectionChartPlaceholder(modifier)
        return
    }
    val animProgress = rememberChartRevealProgress(
        durationMillis = ANIM_DURATION_MS,
        label = "forecast_chart_progress"
    )
    val isOverBudget = projectedCents > budgetLimitCents
    val colors = chartColors(isOverBudget)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = Color.Unspecified)
    val budgetLabel = "Budget ${CurrencyFormatter.format(budgetLimitCents)}"
    val params = ChartParams(
        totalDays = totalDaysInCycle.coerceAtLeast(1),
        daysElapsed = daysElapsed.coerceIn(0, totalDaysInCycle.coerceAtLeast(1)),
        budgetCents = budgetLimitCents,
        totalSpentCents = totalSpentCents,
        projectedCents = projectedCents,
        lowerBoundCents = lowerBoundCents,
        upperBoundCents = upperBoundCents,
    )
    Canvas(modifier = modifier.fillMaxWidth().height(CHART_HEIGHT_DP.dp)) {
        val bottomPad = BOTTOM_PAD_DP.dp.toPx()
        val layout = computeChartLayout(params, size.width, size.height - bottomPad)
        clipRect(right = layout.chartW * animProgress) {
            drawCone(layout, colors)
            drawBudgetLine(layout, colors.budgetLine)
            drawHistorical(layout, colors.history)
            drawProjection(layout, colors)
            drawTodayMarker(layout, colors.todayMarker)
            drawEndpointDot(layout, colors)
        }
        if (animProgress > 0.92f) {
            drawChartLabels(layout, params, colors, textMeasurer, labelStyle, budgetLabel)
        }
    }
}

@Composable
private fun ForecastProjectionChartPlaceholder(modifier: Modifier = Modifier) {
    LoadingValuePlaceholder(
        sampleText = "MMMMMMMMMMMMMMMMMMMMMMMMMMMMM",
        textStyle = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().height(CHART_HEIGHT_DP.dp),
        fillWidth = true,
    )
}

private fun DrawScope.drawCone(layout: ChartLayout, colors: ChartColors) {
    val path = Path().apply {
        moveTo(layout.todayX, layout.spentY)
        lineTo(layout.chartW, layout.upperEndY)
        lineTo(layout.chartW, layout.lowerEndY)
        close()
    }
    drawPath(path, colors.cone)
}

private fun DrawScope.drawBudgetLine(layout: ChartLayout, color: Color) {
    drawLine(
        color = color,
        start = Offset(0f, layout.budgetY),
        end = Offset(layout.chartW, layout.budgetY),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(
            intervals = floatArrayOf(8.dp.toPx(), 5.dp.toPx()),
            phase = 0f,
        ),
    )
}

private fun DrawScope.drawHistorical(layout: ChartLayout, color: Color) {
    drawLine(
        color = color,
        start = Offset(0f, layout.chartH),
        end = Offset(layout.todayX, layout.spentY),
        strokeWidth = 2.5.dp.toPx(),
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawProjection(layout: ChartLayout, colors: ChartColors) {
    val shortDash = PathEffect.dashPathEffect(
        intervals = floatArrayOf(5.dp.toPx(), 5.dp.toPx()),
        phase = 0f,
    )
    val longDash = PathEffect.dashPathEffect(
        intervals = floatArrayOf(10.dp.toPx(), 6.dp.toPx()),
        phase = 0f,
    )
    val origin = Offset(layout.todayX, layout.spentY)
    drawLine(
        color = colors.lowerBound,
        start = origin,
        end = Offset(layout.chartW, layout.lowerEndY),
        strokeWidth = 1.dp.toPx(),
        pathEffect = shortDash,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = colors.upperBound,
        start = origin,
        end = Offset(layout.chartW, layout.upperEndY),
        strokeWidth = 1.dp.toPx(),
        pathEffect = shortDash,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = colors.projection,
        start = origin,
        end = Offset(layout.chartW, layout.projectedEndY),
        strokeWidth = 2.dp.toPx(),
        pathEffect = longDash,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawTodayMarker(layout: ChartLayout, color: Color) {
    drawLine(
        color = color,
        start = Offset(layout.todayX, 0f),
        end = Offset(layout.todayX, layout.chartH),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(
            intervals = floatArrayOf(3.dp.toPx(), 3.dp.toPx()),
            phase = 0f,
        ),
    )
}

private fun DrawScope.drawEndpointDot(layout: ChartLayout, colors: ChartColors) {
    val center = Offset(layout.chartW, layout.projectedEndY)
    drawCircle(color = colors.endDotOuter, radius = 4.dp.toPx(), center = center)
    drawCircle(color = colors.endDotInner, radius = 2.dp.toPx(), center = center)
}

@Suppress("LongMethod")
private fun DrawScope.drawChartLabels(
    layout: ChartLayout,
    params: ChartParams,
    colors: ChartColors,
    measurer: TextMeasurer,
    style: TextStyle,
    budgetLabel: String,
) {
    val yAxis = layout.chartH + 5.dp.toPx()

    val startResult = measurer.measure("Day 1", style)
    drawText(startResult, color = colors.labelSecondary, topLeft = Offset(0f, yAxis))

    val todayResult = measurer.measure("Today", style)
    val minTodayLabelX = startResult.size.width.toFloat() + 4.dp.toPx()
    val maxTodayLabelX = layout.chartW - todayResult.size.width
    val safeMinTodayLabelX = minOf(minTodayLabelX, maxTodayLabelX)
    val safeMaxTodayLabelX = maxOf(minTodayLabelX, maxTodayLabelX)
    val todayLabelX = (layout.todayX - todayResult.size.width / 2f)
        .coerceIn(safeMinTodayLabelX, safeMaxTodayLabelX)
    drawText(todayResult, color = colors.labelSecondary, topLeft = Offset(todayLabelX, yAxis))

    val endResult = measurer.measure("Day ${params.totalDays}", style)
    drawText(
        endResult,
        color = colors.labelSecondary,
        topLeft = Offset(layout.chartW - endResult.size.width, yAxis),
    )

    val budgetResult = measurer.measure(budgetLabel, style)
    val budgetLabelY = (layout.budgetY - budgetResult.size.height - 3.dp.toPx()).coerceAtLeast(0f)
    drawText(
        budgetResult,
        color = colors.budgetLine,
        topLeft = Offset(layout.chartW - budgetResult.size.width - 6.dp.toPx(), budgetLabelY),
    )
}
