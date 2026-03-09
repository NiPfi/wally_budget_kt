package net.loeu.wallybudget.ui.screens.overview

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val LocalPlaceholderShimmerProgress = staticCompositionLocalOf { 0f }

@Composable
internal fun PlaceholderShimmerProvider(
    content: @Composable () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "shared-placeholder-shimmer")
    val shimmerProgress = transition.animateFloat(
        initialValue = -1.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850)
        ),
        label = "shared-placeholder-shimmer-progress"
    )

    CompositionLocalProvider(
        LocalPlaceholderShimmerProgress provides shimmerProgress.value,
        content = content
    )
}

@Composable
internal fun LoadingValuePlaceholder(
    sampleText: String,
    textStyle: TextStyle,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = false,
    minWidth: Dp = 24.dp,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val size = remember(sampleText, textStyle, density) {
        measurePlaceholderSize(textMeasurer, sampleText, textStyle, density)
    }
    val shimmerProgress = LocalPlaceholderShimmerProgress.current
    val widthPx = with(density) { maxOf(size.width, minWidth).toPx() }
    val heightPx = with(density) { size.height.toPx() }
    val scheme = MaterialTheme.colorScheme
    val isDarkTheme = scheme.surface.luminance() < 0.5f
    val placeholderTint = if (isDarkTheme) {
        lerp(scheme.surfaceVariant, scheme.onSurface, 0.22f)
    } else {
        lerp(scheme.surfaceVariant, color, 0.3f)
    }
    val baseColor = placeholderTint.copy(alpha = if (isDarkTheme) 0.52f else 0.22f)
    val highlightColor = if (isDarkTheme) {
        lerp(scheme.onSurface, scheme.primary, 0.18f).copy(alpha = 0.88f)
    } else {
        lerp(color, scheme.surface, 0.35f).copy(alpha = 0.62f)
    }
    val shimmerCenter = shimmerProgress * widthPx
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(shimmerCenter - widthPx, 0f),
        end = Offset(shimmerCenter + widthPx, heightPx)
    )

    Box(
        modifier = if (fillWidth) modifier.fillMaxWidth() else modifier,
        contentAlignment = textAlign.toPlaceholderAlignment()
    ) {
        Box(
            modifier = Modifier
                .width(maxOf(size.width, minWidth))
                .height(size.height)
                .clip(RoundedCornerShape(6.dp))
                .background(shimmerBrush)
        )
    }
}

private data class PlaceholderSize(
    val width: Dp,
    val height: Dp
)

private fun measurePlaceholderSize(
    textMeasurer: TextMeasurer,
    sampleText: String,
    textStyle: TextStyle,
    density: Density
): PlaceholderSize {
    val measured = textMeasurer.measure(
        text = sampleText,
        style = textStyle,
        maxLines = 1
    ).size
    return with(density) {
        PlaceholderSize(
            width = measured.width.toDp(),
            height = measured.height.toDp().coerceAtLeast(18.dp)
        )
    }
}

internal fun TextAlign.toPlaceholderAlignment(): Alignment = when (this) {
    TextAlign.Center -> Alignment.Center
    TextAlign.End,
    TextAlign.Right -> Alignment.CenterEnd
    else -> Alignment.CenterStart
}
