package net.loeu.wallybudget.ui.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
internal fun rememberChartRevealProgress(
    durationMillis: Int,
    label: String
): Float {
    var started by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = durationMillis),
        label = label
    )

    LaunchedEffect(Unit) { started = true }

    return progress
}
