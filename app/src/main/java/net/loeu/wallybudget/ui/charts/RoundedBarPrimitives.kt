package net.loeu.wallybudget.ui.charts

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

internal fun DrawScope.drawRoundedHorizontalTrackBar(
    trackColor: Color,
    fillColor: Color,
    topLeft: Offset,
    size: Size,
    fillWidth: Float,
    cornerRadius: CornerRadius
) {
    drawRoundRect(
        color = trackColor,
        topLeft = topLeft,
        size = size,
        cornerRadius = cornerRadius
    )

    val clampedFillWidth = fillWidth.coerceIn(0f, size.width)
    if (clampedFillWidth <= 0f) return

    drawRoundRect(
        color = fillColor,
        topLeft = topLeft,
        size = Size(clampedFillWidth, size.height),
        cornerRadius = cornerRadius
    )
}

internal fun DrawScope.drawRoundedVerticalTrackBar(
    trackColor: Color,
    fillColor: Color,
    x: Float,
    barWidth: Float,
    chartHeight: Float,
    fillHeight: Float,
    cornerRadius: CornerRadius
) {
    drawRoundRect(
        color = trackColor,
        topLeft = Offset(x, 0f),
        size = Size(barWidth, chartHeight),
        cornerRadius = cornerRadius
    )

    val clampedFillHeight = fillHeight.coerceIn(0f, chartHeight)
    if (clampedFillHeight <= 0f) return

    drawRoundRect(
        color = fillColor,
        topLeft = Offset(x, chartHeight - clampedFillHeight),
        size = Size(barWidth, clampedFillHeight),
        cornerRadius = cornerRadius
    )
}
