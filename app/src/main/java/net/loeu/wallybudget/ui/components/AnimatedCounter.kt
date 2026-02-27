package net.loeu.wallybudget.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import net.loeu.wallybudget.util.CurrencyFormatter

/**
 * Animated counter that displays currency amounts with rolling animation
 */
@Composable
fun AnimatedCounter(
    amountCents: Long,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = LocalTextStyle.current,
    color: Color = MaterialTheme.colorScheme.onBackground
) {
    val formattedAmount = CurrencyFormatter.format(amountCents)

    AnimatedCounterWithAnimation(
        amountCents = amountCents,
        formattedAmount = formattedAmount,
        textStyle = textStyle,
        color = color,
        modifier = modifier
    )
}

@Composable
private fun AnimatedCounterWithAnimation(
    amountCents: Long,
    formattedAmount: String,
    textStyle: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    val animatable = remember { Animatable(amountCents.toFloat()) }

    LaunchedEffect(amountCents) {
        animatable.animateTo(
            targetValue = amountCents.toFloat(),
            animationSpec = tween(
                durationMillis = 300,
                easing = FastOutSlowInEasing
            )
        )
    }

    val currentFormattedAmount = CurrencyFormatter.format(animatable.value.toLong())
    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(modifier = modifier) {
        val availableWidthPx = with(LocalDensity.current) { maxWidth.toPx() }.roundToInt().coerceAtLeast(1)
        val minFontSize = 20.sp
        var candidateFontSize = if (textStyle.fontSize.isSpecified) textStyle.fontSize else 57.sp

        while (candidateFontSize > minFontSize) {
            val measured = textMeasurer.measure(
                text = currentFormattedAmount,
                style = textStyle.copy(fontSize = candidateFontSize),
                maxLines = 1,
                constraints = Constraints(maxWidth = availableWidthPx)
            )
            if (!measured.hasVisualOverflow) break
            candidateFontSize *= 0.92f
        }

        Text(
            text = currentFormattedAmount,
            style = textStyle.copy(fontSize = candidateFontSize),
            color = color,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier
        )
    }
}

/**
 * Animated digit that rolls like an odometer
 */
@Composable
fun AnimatedDigit(
    digit: Char,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = LocalTextStyle.current,
    color: Color = MaterialTheme.colorScheme.onBackground
) {
    val animatable = remember { Animatable(0f) }

    LaunchedEffect(digit) {
        if (digit.isDigit()) {
            val targetValue = digit.digitToInt().toFloat()
            animatable.animateTo(
                targetValue = targetValue,
                animationSpec = tween(
                    durationMillis = 250,
                    easing = FastOutSlowInEasing
                )
            )
        }
    }

    Text(
        text = digit.toString(),
        style = textStyle,
        color = color,
        modifier = modifier
    )
}