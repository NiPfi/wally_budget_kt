package net.loeu.wallybudget.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import net.loeu.wallybudget.util.CurrencyFormatter
import kotlin.math.roundToInt

/**
 * Animated counter that displays currency amounts with rolling animation
 */
@Composable
fun AnimatedCounter(
    amount: Double,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = LocalTextStyle.current,
    color: Color = MaterialTheme.colorScheme.onBackground
) {
    val formattedAmount = CurrencyFormatter.format(amount)

    AnimatedCounterWithAnimation(
        amount = amount,
        formattedAmount = formattedAmount,
        textStyle = textStyle,
        color = color,
        modifier = modifier
    )
}

@Composable
private fun AnimatedCounterWithAnimation(
    amount: Double,
    formattedAmount: String,
    textStyle: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    val animatable = remember { Animatable(amount.toFloat()) }

    LaunchedEffect(amount) {
        animatable.animateTo(
            targetValue = amount.toFloat(),
            animationSpec = tween(
                durationMillis = 300,
                easing = FastOutSlowInEasing
            )
        )
    }

    val currentFormattedAmount = CurrencyFormatter.format(animatable.value.toDouble())

    Text(
        text = currentFormattedAmount,
        style = textStyle,
        color = color,
        modifier = modifier
    )
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

