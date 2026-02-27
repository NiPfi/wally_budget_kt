package net.loeu.wallybudget.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

@Stable
class VerticalSnapController internal constructor(
    val dragState: DraggableState,
    val offsetPx: Float,
    val progress: Float,
    val isExpanded: Boolean,
    val onDragStarted: () -> Unit,
    val onDragStopped: (Float) -> Unit,
    val toggle: () -> Unit
)

@Composable
fun rememberVerticalSnapController(
    containerHeightPx: Float,
    thresholdRatio: Float = 0.28f,
    settleDurationMs: Int = 260
): VerticalSnapController {
    val threshold = containerHeightPx * thresholdRatio

    var isExpanded by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val offsetAnim = remember { Animatable(0f) }

    LaunchedEffect(containerHeightPx) {
        offsetAnim.updateBounds(lowerBound = 0f, upperBound = containerHeightPx)
        if (!dragging) {
            offsetAnim.snapTo(if (isExpanded) containerHeightPx else 0f)
        }
    }

    LaunchedEffect(isExpanded, dragging, containerHeightPx) {
        if (!dragging) {
            val targetOffset = if (isExpanded) containerHeightPx else 0f
            offsetAnim.animateTo(
                targetValue = targetOffset,
                animationSpec = tween(durationMillis = settleDurationMs, easing = FastOutSlowInEasing)
            )
        }
    }

    val dragState = rememberDraggableState { delta ->
        coroutineScope.launch {
            val nextOffset = (offsetAnim.value - delta).coerceIn(0f, containerHeightPx)
            offsetAnim.snapTo(nextOffset)
        }
    }

    val onDragStarted = {
        dragging = true
        coroutineScope.launch {
            offsetAnim.stop()
        }
        Unit
    }

    val onDragStopped: (Float) -> Unit = { velocity ->
        val currentOffset = offsetAnim.value
        val shouldExpand = when {
            velocity < -1000f -> true
            velocity > 1000f -> false
            else -> currentOffset > if (isExpanded) containerHeightPx - threshold else threshold
        }

        isExpanded = shouldExpand
        dragging = false
    }

    return VerticalSnapController(
        dragState = dragState,
        offsetPx = offsetAnim.value,
        progress = (offsetAnim.value / containerHeightPx).coerceIn(0f, 1f),
        isExpanded = isExpanded,
        onDragStarted = onDragStarted,
        onDragStopped = onDragStopped,
        toggle = { isExpanded = !isExpanded }
    )
}
