package net.loeu.wallybudget.ui.screens.overview

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.max

internal fun rollingAnimationSettleDelay(previousText: String, nextText: String): Int {
    val longestDigitRun = max(
        previousText.count { it.isDigit() },
        nextText.count { it.isDigit() }
    ).coerceAtLeast(1)
    return DIGIT_ROLL_DURATION_MS + ((longestDigitRun - 1) * DIGIT_STAGGER_MS)
}

internal fun findFittedFontSize(
    textMeasurer: TextMeasurer,
    text: String,
    textStyle: TextStyle,
    availableWidthPx: Int,
    minFontSize: TextUnit
): TextUnit {
    var candidateFontSize = if (textStyle.fontSize.isSpecified) textStyle.fontSize else 57.sp

    while (candidateFontSize > minFontSize) {
        val measured = textMeasurer.measure(
            text = text,
            style = textStyle.copy(fontSize = candidateFontSize),
            maxLines = 1,
            constraints = Constraints(maxWidth = availableWidthPx)
        )
        if (!measured.hasVisualOverflow) break
        candidateFontSize *= 0.92f
    }

    return candidateFontSize
}

@Composable
internal fun StaticNumberText(
    text: String,
    style: TextStyle,
    color: Color,
    textAlign: TextAlign,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
        textAlign = textAlign,
        modifier = modifier
    )
}

@Composable
internal fun RollingNumberText(
    animation: RollingAnimation,
    style: TextStyle,
    color: Color,
    textAlign: TextAlign,
    modifier: Modifier = Modifier
) {
    var slotCount by remember(animation.key, animation.fromText, animation.toText) {
        mutableIntStateOf(max(animation.fromText.length, animation.toText.length))
    }

    LaunchedEffect(animation.key, animation.fromText, animation.toText) {
        val newCount = max(animation.fromText.length, animation.toText.length)
        if (newCount >= slotCount) {
            slotCount = newCount
        } else {
            delay(CHARACTER_REMOVAL_DELAY_MS)
            slotCount = newCount
        }
    }

    val fromText = animation.fromText.padStart(slotCount)
    val toText = animation.toText.padStart(slotCount)
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = LocalDensity.current
    val slotWidths = remember(fromText, toText, style, density) {
        measureSlotWidths(
            textMeasurer = textMeasurer,
            density = density,
            style = style,
            fromText = fromText,
            toText = toText
        )
    }
    val digitOrders = toText.digitOrdersFromRight()

    Box(
        modifier = modifier,
        contentAlignment = textAlign.toPlaceholderAlignment()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            toText.indices.forEach { index ->
                RollingCharacter(
                    animationKey = animation.key,
                    initialChar = fromText[index],
                    targetChar = toText[index],
                    direction = animation.direction,
                    staggerOrder = digitOrders[index],
                    slotWidth = slotWidths[index],
                    style = style,
                    color = color
                )
            }
        }
    }
}

@Composable
private fun RollingCharacter(
    animationKey: Int,
    initialChar: Char,
    targetChar: Char,
    direction: Int,
    staggerOrder: Int,
    slotWidth: Dp,
    style: TextStyle,
    color: Color
) {
    val shouldAnimate = direction != 0 && initialChar != targetChar && targetChar.isDigit()
    var phaseTarget by remember(animationKey) { mutableStateOf(initialChar) }

    LaunchedEffect(animationKey, initialChar, targetChar, shouldAnimate) {
        phaseTarget = initialChar
        if (shouldAnimate) {
            delay((staggerOrder * DIGIT_STAGGER_MS).toLong())
        }
        phaseTarget = targetChar
    }

    Box(
        modifier = Modifier.width(slotWidth),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = phaseTarget,
            transitionSpec = {
                if (!shouldAnimate) {
                    fadeIn(animationSpec = tween(0)) togetherWith
                        fadeOut(animationSpec = tween(0)) using
                        SizeTransform(clip = true)
                } else {
                    val enter = if (direction >= 0) {
                        slideInVertically(animationSpec = tween(DIGIT_ROLL_DURATION_MS)) { height -> height } +
                            fadeIn(animationSpec = tween(DIGIT_ROLL_DURATION_MS / 2))
                    } else {
                        slideInVertically(animationSpec = tween(DIGIT_ROLL_DURATION_MS)) { height -> -height } +
                            fadeIn(animationSpec = tween(DIGIT_ROLL_DURATION_MS / 2))
                    }
                    val exit = if (direction >= 0) {
                        slideOutVertically(animationSpec = tween(DIGIT_ROLL_DURATION_MS)) { height -> -height } +
                            fadeOut(animationSpec = tween(DIGIT_ROLL_DURATION_MS / 2))
                    } else {
                        slideOutVertically(animationSpec = tween(DIGIT_ROLL_DURATION_MS)) { height -> height } +
                            fadeOut(animationSpec = tween(DIGIT_ROLL_DURATION_MS / 2))
                    }
                    enter togetherWith exit using SizeTransform(clip = true)
                }
            },
            label = "rolling-character"
        ) { character ->
            Text(
                text = if (character == ' ') " " else character.toString(),
                style = style,
                color = if (character == ' ') color.copy(alpha = 0f) else color,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

internal fun widestAnimationText(animation: RollingAnimation): String {
    return if (animation.fromText.length >= animation.toText.length) {
        animation.fromText
    } else {
        animation.toText
    }
}

private fun measureSlotWidths(
    textMeasurer: TextMeasurer,
    density: Density,
    style: TextStyle,
    fromText: String,
    toText: String
): List<Dp> {
    return List(toText.length) { index ->
        with(density) {
            max(
                measureSlotWidth(textMeasurer, style, fromText[index]),
                measureSlotWidth(textMeasurer, style, toText[index])
            ).toDp()
        }
    }
}

private fun measureSlotWidth(
    textMeasurer: TextMeasurer,
    style: TextStyle,
    character: Char
): Int {
    val sample = when {
        character.isDigit() || character == ' ' -> "8"
        else -> character.toString()
    }
    return textMeasurer.measure(
        text = sample,
        style = style,
        maxLines = 1
    ).size.width
}

private fun String.digitOrdersFromRight(): List<Int> {
    var digitOrder = 0
    return buildList(length) {
        for (index in this@digitOrdersFromRight.indices.reversed()) {
            val character = this@digitOrdersFromRight[index]
            if (character.isDigit()) {
                add(digitOrder)
                digitOrder += 1
            } else {
                add(0)
            }
        }
    }.asReversed()
}
