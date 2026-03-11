package net.loeu.wallybudget.ui.screens.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import net.loeu.wallybudget.util.CurrencyFormatter

internal const val DIGIT_ROLL_DURATION_MS = 240
internal const val DIGIT_STAGGER_MS = 35
internal const val CHARACTER_REMOVAL_DELAY_MS = 180L

internal data class RollingAnimation(
    val key: Int,
    val fromText: String,
    val toText: String,
    val direction: Int
)

@Composable
fun AnimatedCounter(
    amountCents: Long,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = LocalTextStyle.current,
    color: Color = MaterialTheme.colorScheme.onBackground,
    animate: Boolean = true,
    animateOnFirstResolvedValue: Boolean = false,
    textAlign: TextAlign = TextAlign.Start,
    minFontSize: TextUnit = 12.sp,
    signed: Boolean = false,
    fitToWidth: Boolean = true,
    placeholder: Boolean = false,
    placeholderText: String = "$8,888",
    formatter: ((Long) -> String)? = null
) {
    val displayFormatter = formatter ?: { value ->
        if (signed) {
            CurrencyFormatter.formatSigned(value)
        } else {
            CurrencyFormatter.format(value)
        }
    }

    AnimatedValueText(
        value = amountCents,
        formatter = displayFormatter,
        textStyle = textStyle,
        color = color,
        animate = animate,
        animateOnFirstResolvedValue = animateOnFirstResolvedValue,
        modifier = modifier,
        textAlign = textAlign,
        minFontSize = minFontSize,
        fitToWidth = fitToWidth,
        placeholder = placeholder,
        placeholderText = placeholderText
    )
}

@Composable
fun AnimatedIntegerCounter(
    value: Int,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = LocalTextStyle.current,
    color: Color = MaterialTheme.colorScheme.onBackground,
    animate: Boolean = true,
    animateOnFirstResolvedValue: Boolean = false,
    textAlign: TextAlign = TextAlign.Start,
    placeholder: Boolean = false,
    placeholderText: String = "88"
) {
    AnimatedValueText(
        value = value.toLong(),
        formatter = Long::toString,
        textStyle = textStyle,
        color = color,
        animate = animate,
        animateOnFirstResolvedValue = animateOnFirstResolvedValue,
        modifier = modifier,
        textAlign = textAlign,
        placeholder = placeholder,
        placeholderText = placeholderText
    )
}

@Composable
private fun AnimatedValueText(
    value: Long,
    formatter: (Long) -> String,
    textStyle: TextStyle,
    color: Color,
    animate: Boolean,
    animateOnFirstResolvedValue: Boolean,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    minFontSize: TextUnit = 12.sp,
    fitToWidth: Boolean = true,
    placeholder: Boolean = false,
    placeholderText: String = "$8,888"
) {
    val resolvedTextStyle = textStyle.copy(fontFeatureSettings = "tnum")
    val displayText = formatter(value)
    var previousValue by remember { mutableLongStateOf(value) }
    var hadPlaceholder by remember { mutableStateOf(placeholder) }
    var animationsArmed by remember { mutableStateOf(false) }
    var animationKey by remember { mutableIntStateOf(0) }
    var activeAnimation by remember { mutableStateOf<RollingAnimation?>(null) }

    AnimatedValueEffect(
        value = value,
        placeholder = placeholder,
        animate = animate,
        animateOnFirstResolvedValue = animateOnFirstResolvedValue,
        formatter = formatter,
        previousValue = previousValue,
        hadPlaceholder = hadPlaceholder,
        animationsArmed = animationsArmed,
        animationKey = animationKey,
        activeAnimationKey = { activeAnimation?.key },
        onPreviousValueChange = { previousValue = it },
        onHadPlaceholderChange = { hadPlaceholder = it },
        onAnimationsArmedChange = { animationsArmed = it },
        onAnimationKeyChange = { animationKey = it },
        onActiveAnimationChange = { activeAnimation = it }
    )

    if (!fitToWidth) {
        if (placeholder) {
            LoadingValuePlaceholder(
                sampleText = placeholderText,
                textStyle = resolvedTextStyle,
                textAlign = textAlign,
                modifier = modifier
            )
        } else {
            AnimatedValueTextWithoutWidthFitting(
                displayText = displayText,
                resolvedTextStyle = resolvedTextStyle,
                color = color,
                textAlign = textAlign,
                modifier = modifier,
                activeAnimation = activeAnimation
            )
        }
        return
    }

    AnimatedValueTextWithWidthFittingLayout(
        modifier = modifier,
        textAlign = textAlign,
        placeholder = placeholder,
        placeholderText = placeholderText,
        displayText = displayText,
        resolvedTextStyle = resolvedTextStyle,
        minFontSize = minFontSize,
        color = color,
        activeAnimation = activeAnimation
    )
}

private fun animatedMeasurementText(
    placeholder: Boolean,
    placeholderText: String,
    displayText: String,
    activeAnimation: RollingAnimation?
): String {
    return when {
        placeholder -> placeholderText
        activeAnimation != null -> widestAnimationText(activeAnimation)
        else -> displayText
    }
}

@Composable
private fun AnimatedValueTextWithWidthFittingLayout(
    modifier: Modifier,
    textAlign: TextAlign,
    placeholder: Boolean,
    placeholderText: String,
    displayText: String,
    resolvedTextStyle: TextStyle,
    minFontSize: TextUnit,
    color: Color,
    activeAnimation: RollingAnimation?
) {
    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = textAlign.toPlaceholderAlignment()
    ) {
        val availableWidthPx =
            with(LocalDensity.current) { maxWidth.toPx() }.roundToInt().coerceAtLeast(1)
        val measurementText = animatedMeasurementText(
            placeholder = placeholder,
            placeholderText = placeholderText,
            displayText = displayText,
            activeAnimation = activeAnimation
        )
        val candidateFontSize = remember(
            availableWidthPx,
            measurementText,
            resolvedTextStyle,
            minFontSize,
            textMeasurer
        ) {
            findFittedFontSize(
                textMeasurer = textMeasurer,
                text = measurementText,
                textStyle = resolvedTextStyle,
                availableWidthPx = availableWidthPx,
                minFontSize = minFontSize
            )
        }

        AnimatedValueTextWithWidthFitting(
            placeholder = placeholder,
            placeholderText = placeholderText,
            displayText = displayText,
            resolvedTextStyle = resolvedTextStyle,
            candidateFontSize = candidateFontSize,
            color = color,
            textAlign = textAlign,
            activeAnimation = activeAnimation
        )
    }
}

@Composable
private fun AnimatedValueEffect(
    value: Long,
    placeholder: Boolean,
    animate: Boolean,
    animateOnFirstResolvedValue: Boolean,
    formatter: (Long) -> String,
    previousValue: Long,
    hadPlaceholder: Boolean,
    animationsArmed: Boolean,
    animationKey: Int,
    activeAnimationKey: () -> Int?,
    onPreviousValueChange: (Long) -> Unit,
    onHadPlaceholderChange: (Boolean) -> Unit,
    onAnimationsArmedChange: (Boolean) -> Unit,
    onAnimationKeyChange: (Int) -> Unit,
    onActiveAnimationChange: (RollingAnimation?) -> Unit
) {
    LaunchedEffect(value, placeholder, animationsArmed, animate, animateOnFirstResolvedValue) {
        if (placeholder) {
            onPreviousValueChange(value)
            onActiveAnimationChange(null)
            onHadPlaceholderChange(true)
            onAnimationsArmedChange(false)
            return@LaunchedEffect
        }

        if (!animate) {
            onPreviousValueChange(value)
            onActiveAnimationChange(null)
            onHadPlaceholderChange(false)
            onAnimationsArmedChange(true)
            return@LaunchedEffect
        }

        val resolvedPreviousValue = if (!animationsArmed) {
            onActiveAnimationChange(null)
            val shouldAnimateResolvedValue = animateOnFirstResolvedValue && hadPlaceholder
            onHadPlaceholderChange(false)
            onAnimationsArmedChange(true)
            if (!shouldAnimateResolvedValue) {
                onPreviousValueChange(value)
                return@LaunchedEffect
            }
            0L
        } else {
            previousValue
        }

        val direction = when {
            value > resolvedPreviousValue -> 1
            value < resolvedPreviousValue -> -1
            else -> 0
        }
        if (direction == 0) return@LaunchedEffect

        val previousText = formatter(resolvedPreviousValue)
        val nextText = formatter(value)
        onPreviousValueChange(value)
        val nextAnimationKey = animationKey + 1
        onAnimationKeyChange(nextAnimationKey)
        val animation = RollingAnimation(
            key = nextAnimationKey,
            fromText = previousText,
            toText = nextText,
            direction = direction
        )
        onActiveAnimationChange(animation)

        val settleDelay = rollingAnimationSettleDelay(previousText, nextText)
        delay(settleDelay.toLong())
        if (activeAnimationKey() == animation.key) {
            onActiveAnimationChange(null)
        }
    }
}

@Composable
private fun AnimatedValueTextWithoutWidthFitting(
    displayText: String,
    resolvedTextStyle: TextStyle,
    color: Color,
    textAlign: TextAlign,
    modifier: Modifier,
    activeAnimation: RollingAnimation?
) {
    if (activeAnimation == null) {
        StaticNumberText(
            text = displayText,
            style = resolvedTextStyle,
            color = color,
            textAlign = textAlign,
            modifier = modifier
        )
    } else {
        RollingNumberText(
            animation = activeAnimation,
            style = resolvedTextStyle,
            color = color,
            textAlign = textAlign,
            modifier = modifier
        )
    }
}

@Composable
private fun AnimatedValueTextWithWidthFitting(
    placeholder: Boolean,
    placeholderText: String,
    displayText: String,
    resolvedTextStyle: TextStyle,
    candidateFontSize: TextUnit,
    color: Color,
    textAlign: TextAlign,
    activeAnimation: RollingAnimation?
) {
    val fittedStyle = resolvedTextStyle.copy(fontSize = candidateFontSize)
    when {
        placeholder -> LoadingValuePlaceholder(
            sampleText = placeholderText,
            textStyle = fittedStyle,
            textAlign = textAlign,
            modifier = Modifier
        )
        activeAnimation == null -> StaticNumberText(
            text = displayText,
            style = fittedStyle,
            color = color,
            textAlign = textAlign,
            modifier = Modifier
        )
        else -> RollingNumberText(
            animation = activeAnimation,
            style = fittedStyle,
            color = color,
            textAlign = textAlign,
            modifier = Modifier
        )
    }
}
