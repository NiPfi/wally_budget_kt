package net.loeu.wallybudget.ui.screens.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import net.loeu.wallybudget.ui.charts.drawRoundedHorizontalTrackBar

@Composable
internal fun RecommendationRow(
    index: Int,
    text: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(22.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (index + 1).toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
internal fun verdictContainerColor(
    verdict: AnalysisVerdictLevel?,
    isLoading: Boolean
): Color = when {
    isLoading -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    verdict == AnalysisVerdictLevel.AtRisk -> MaterialTheme.colorScheme.errorContainer
    verdict == AnalysisVerdictLevel.Caution -> MaterialTheme.colorScheme.tertiaryContainer
    verdict == AnalysisVerdictLevel.Watchful -> MaterialTheme.colorScheme.secondaryContainer
    else -> MaterialTheme.colorScheme.primaryContainer
}

/** Tone-matched text color for evidence item values. */
@Composable
internal fun evidenceToneValueColor(tone: AnalysisEvidenceTone): Color = when (tone) {
    AnalysisEvidenceTone.Positive -> MaterialTheme.colorScheme.primary
    AnalysisEvidenceTone.Warning -> MaterialTheme.colorScheme.tertiary
    AnalysisEvidenceTone.Critical -> MaterialTheme.colorScheme.error
    AnalysisEvidenceTone.Neutral -> MaterialTheme.colorScheme.onSurface
}

/**
 * Compact horizontal bar visualising an [EvidenceGauge].
 *
 * Renders a track, an optional shaded band between lower/upper bounds, a filled
 * segment from 0 → value, and a vertical tick mark at the target position.
 */
@Composable
internal fun MiniGaugeBar(
    gauge: EvidenceGauge,
    tone: AnalysisEvidenceTone,
    modifier: Modifier = Modifier
) {
    val fillColor = when (tone) {
        AnalysisEvidenceTone.Positive -> MaterialTheme.colorScheme.primary
        AnalysisEvidenceTone.Warning -> MaterialTheme.colorScheme.tertiary
        AnalysisEvidenceTone.Critical -> MaterialTheme.colorScheme.error
        AnalysisEvidenceTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(7.dp)
    ) {
        if (gauge.maxCents <= 0L) return@Canvas
        val w = size.width
        val h = size.height
        val cornerR = CornerRadius(h / 2f)
        drawRoundedHorizontalTrackBar(
            trackColor = trackColor,
            fillColor = Color.Transparent,
            topLeft = Offset.Zero,
            size = Size(w, h),
            fillWidth = 0f,
            cornerRadius = cornerR
        )

        val lower = gauge.lowerCents
        val upper = gauge.upperCents
        if (lower != null && upper != null) {
            val lx = (lower.toFloat() / gauge.maxCents * w).coerceIn(0f, w)
            val ux = (upper.toFloat() / gauge.maxCents * w).coerceIn(lx, w)
            drawRoundRect(
                color = fillColor.copy(alpha = 0.18f),
                topLeft = Offset(lx, 0f),
                size = Size(ux - lx, h),
                cornerRadius = cornerR
            )
        }

        val valueX = (gauge.valueCents.toFloat() / gauge.maxCents * w).coerceIn(0f, w)
        drawRoundedHorizontalTrackBar(
            trackColor = Color.Transparent,
            fillColor = fillColor,
            topLeft = Offset.Zero,
            size = Size(w, h),
            fillWidth = valueX,
            cornerRadius = cornerR
        )

        val targetX = (gauge.targetCents.toFloat() / gauge.maxCents * w).coerceIn(0f, w)
        drawLine(
            color = tickColor,
            start = Offset(targetX, 0f),
            end = Offset(targetX, h),
            strokeWidth = 1.5.dp.toPx()
        )
    }
}
