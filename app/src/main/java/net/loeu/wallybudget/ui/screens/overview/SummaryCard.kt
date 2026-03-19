@file:Suppress("TooManyFunctions")

package net.loeu.wallybudget.ui.screens.overview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import net.loeu.wallybudget.R
import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.util.CurrencyFormatter
import kotlin.math.roundToInt

private val FlatSummaryCardShape = RoundedCornerShape(0.dp)

@Composable
fun SummaryCard(
    budgetState: BudgetState,
    collapseProgress: Float,
    modifier: Modifier = Modifier,
    recoverableOverspendCents: Long = 0L,
    isLoading: Boolean = false,
    animateCounters: Boolean = true,
    useWarningTint: Boolean = false,
    tagSecondaryMetrics: Boolean = false,
    onSafeTodayInfoClick: (() -> Unit)? = null,
    onNavigateToSettings: (() -> Unit)? = null
) {
    val progress = collapseProgress.coerceIn(0f, 1f)
    val density = LocalDensity.current
    val horizontalPadding = lerp(20.dp, 16.dp, progress)
    val verticalPadding = lerp(18.dp, 8.dp, progress)
    val contentSpacing = lerp(12.dp, 4.dp, progress)
    val iconAlpha = (1f - progress * 1.35f).coerceIn(0f, 1f)
    val secondaryMetricsProgress = 1f - progress
    val amountFontSize = lerp(22.sp, 17.sp, progress)
    val amountLineHeight = lerp(28.sp, 20.sp, progress)
    val safeTodayAlpha = (1f - progress * 1.5f).coerceIn(0f, 1f)
    val rightTopOffsetPx = with(density) { ((1f - progress) * 6.dp.toPx()) }
    val iconOffsetPx = with(density) { (progress * -4.dp.toPx()) }
    val bottomOffsetPx = with(density) { ((1f - secondaryMetricsProgress) * -6.dp.toPx()) }
    val colors = summaryCardColors(useWarningTint)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(14.dp, FlatSummaryCardShape),
        color = colors.container,
        tonalElevation = lerp(2.dp, 0.dp, progress),
        shape = FlatSummaryCardShape
    ) {
        SummaryCardBody(
            budgetState = budgetState,
            recoverableOverspendCents = recoverableOverspendCents,
            progress = progress,
            horizontalPadding = horizontalPadding,
            verticalPadding = verticalPadding,
            contentSpacing = contentSpacing,
            amountFontSize = amountFontSize,
            amountLineHeight = amountLineHeight,
            contentColor = colors.content,
            iconAlpha = iconAlpha,
            iconOffsetPx = iconOffsetPx,
            rightTopOffsetPx = rightTopOffsetPx,
            safeTodayAlpha = safeTodayAlpha,
            bottomOffsetPx = bottomOffsetPx,
            secondaryMetricsProgress = secondaryMetricsProgress,
            isLoading = isLoading,
            animateCounters = animateCounters,
            tagSecondaryMetrics = tagSecondaryMetrics,
            onSafeTodayInfoClick = onSafeTodayInfoClick,
            onNavigateToSettings = onNavigateToSettings
        )
    }
}

@Composable
internal fun SummaryCardBody(
    budgetState: BudgetState,
    recoverableOverspendCents: Long,
    progress: Float,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    verticalPadding: androidx.compose.ui.unit.Dp,
    contentSpacing: androidx.compose.ui.unit.Dp,
    amountFontSize: androidx.compose.ui.unit.TextUnit,
    amountLineHeight: androidx.compose.ui.unit.TextUnit,
    contentColor: Color,
    iconAlpha: Float,
    iconOffsetPx: Float,
    rightTopOffsetPx: Float,
    safeTodayAlpha: Float,
    bottomOffsetPx: Float,
    secondaryMetricsProgress: Float,
    isLoading: Boolean,
    animateCounters: Boolean,
    tagSecondaryMetrics: Boolean,
    onSafeTodayInfoClick: (() -> Unit)?,
    onNavigateToSettings: (() -> Unit)?
) {
    Column(
        modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(contentSpacing)
    ) {
        SummaryCardPrimaryRow(
            budgetState = budgetState,
            amountFontSize = amountFontSize,
            amountLineHeight = amountLineHeight,
            contentColor = contentColor,
            progress = progress,
            iconAlpha = iconAlpha,
            iconOffsetPx = iconOffsetPx,
            rightTopOffsetPx = rightTopOffsetPx,
            recoverableOverspendCents = recoverableOverspendCents,
            safeTodayAlpha = safeTodayAlpha,
            isLoading = isLoading,
            animateCounters = animateCounters,
            onSafeTodayInfoClick = onSafeTodayInfoClick,
            onNavigateToSettings = onNavigateToSettings
        )
        SummaryCardSecondaryMetrics(
            budgetState = budgetState,
            secondaryMetricsProgress = secondaryMetricsProgress,
            bottomOffsetPx = bottomOffsetPx,
            progress = progress,
            contentColor = contentColor,
            animateCounters = animateCounters,
            isLoading = isLoading,
            tagSecondaryMetrics = tagSecondaryMetrics
        )
    }
}

@Composable
internal fun MergedSummaryHeaderSurface(
    title: String,
    summaryColors: SummaryCardColors,
    modifier: Modifier = Modifier,
    onNavigateToSettings: (() -> Unit)? = null,
    headerRowTestTag: String? = null,
    titleTestTag: String? = null,
    settingsTestTag: String? = null,
    body: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(14.dp, FlatSummaryCardShape),
        color = summaryColors.container,
        shape = FlatSummaryCardShape
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            MergedSummaryHeaderRow(
                title = title,
                contentColor = summaryColors.content,
                onNavigateToSettings = onNavigateToSettings,
                modifier = Modifier.fillMaxWidth(),
                rowTestTag = headerRowTestTag,
                titleTestTag = titleTestTag,
                settingsTestTag = settingsTestTag
            )
            body()
        }
    }
}

@Composable
internal fun MergedSummaryHeaderRow(
    title: String,
    contentColor: Color,
    onNavigateToSettings: (() -> Unit)?,
    modifier: Modifier = Modifier,
    rowTestTag: String? = null,
    titleTestTag: String? = null,
    settingsTestTag: String? = null
) {
    Box(
        modifier = modifier
            .then(if (rowTestTag != null) Modifier.testTag(rowTestTag) else Modifier)
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = title,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .widthIn(max = 260.dp)
                .then(if (titleTestTag != null) Modifier.testTag(titleTestTag) else Modifier),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (onNavigateToSettings != null) {
            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .then(if (settingsTestTag != null) Modifier.testTag(settingsTestTag) else Modifier)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = "Open settings",
                    tint = contentColor
                )
            }
        }
    }
}

internal data class SummaryCardColors(
    val container: Color,
    val content: Color
)

@Composable
internal fun summaryCardColors(useWarningTint: Boolean): SummaryCardColors {
    return SummaryCardColors(
        container = if (useWarningTint) {
            blendedAlertContainer()
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        content = if (useWarningTint) {
            blendedAlertContent()
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        }
    )
}

@Composable
private fun SummaryCardPrimaryRow(
    budgetState: BudgetState,
    amountFontSize: androidx.compose.ui.unit.TextUnit,
    amountLineHeight: androidx.compose.ui.unit.TextUnit,
    contentColor: Color,
    progress: Float,
    iconAlpha: Float,
    iconOffsetPx: Float,
    rightTopOffsetPx: Float,
    recoverableOverspendCents: Long,
    safeTodayAlpha: Float,
    isLoading: Boolean,
    animateCounters: Boolean,
    onSafeTodayInfoClick: (() -> Unit)?,
    onNavigateToSettings: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = "TODAY LEFT",
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.72f)
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnimatedCounter(
                    amountCents = budgetState.remainingTodayCents,
                    signed = true,
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = amountFontSize,
                        lineHeight = amountLineHeight,
                        fontWeight = FontWeight.Black
                    ),
                    color = if (budgetState.remainingTodayCents >= 0L) {
                        contentColor
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    animate = animateCounters,
                    animateOnFirstResolvedValue = animateCounters,
                    textAlign = TextAlign.Start,
                    placeholder = isLoading,
                    placeholderText = "$8,888"
                )
                SafeTodayChip(
                    recoverableOverspendCents = recoverableOverspendCents,
                    safeTodayAlpha = safeTodayAlpha,
                    contentColor = contentColor,
                    isLoading = isLoading,
                    onSafeTodayInfoClick = onSafeTodayInfoClick
                )
            }
        }

        SummaryCardTrailingInfo(
            daysRemainingInCycle = budgetState.daysRemainingInCycle,
            contentColor = contentColor,
            progress = progress,
            iconAlpha = iconAlpha,
            iconOffsetPx = iconOffsetPx,
            rightTopOffsetPx = rightTopOffsetPx,
            isLoading = isLoading,
            animateCounters = animateCounters,
            onNavigateToSettings = onNavigateToSettings
        )
    }
}

@Composable
private fun SummaryCardTrailingInfo(
    daysRemainingInCycle: Int,
    contentColor: Color,
    progress: Float,
    iconAlpha: Float,
    iconOffsetPx: Float,
    rightTopOffsetPx: Float,
    isLoading: Boolean,
    animateCounters: Boolean,
    onNavigateToSettings: (() -> Unit)?
) {
    Box(contentAlignment = Alignment.CenterEnd) {
        if (onNavigateToSettings != null) {
            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier.graphicsLayer {
                    alpha = iconAlpha
                    translationY = iconOffsetPx
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = "Settings",
                    tint = contentColor
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.graphicsLayer {
                alpha = progress
                translationY = rightTopOffsetPx
            }
        ) {
            Text(
                text = "DAYS LEFT",
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.68f)
            )
            AnimatedIntegerCounter(
                value = daysRemainingInCycle,
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = contentColor,
                animate = animateCounters,
                animateOnFirstResolvedValue = animateCounters,
                placeholder = isLoading,
                placeholderText = "88"
            )
        }
    }
}

@Composable
private fun SafeTodayChip(
    recoverableOverspendCents: Long,
    safeTodayAlpha: Float,
    contentColor: Color,
    isLoading: Boolean,
    onSafeTodayInfoClick: (() -> Unit)?
) {
    if ((recoverableOverspendCents <= 0L && !isLoading) || onSafeTodayInfoClick == null) {
        return
    }

    val isVisible = safeTodayAlpha > 0f
    Row(
        modifier = Modifier
            .graphicsLayer { alpha = safeTodayAlpha }
            .clickable(
                enabled = isVisible && !isLoading,
                onClick = onSafeTodayInfoClick
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AnimatedCounter(
            amountCents = recoverableOverspendCents,
            formatter = { "+ ${CurrencyFormatter.format(it)}" },
            textStyle = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = contentColor,
            animate = false,
            placeholder = isLoading,
            placeholderText = "+ $888"
        )
        Icon(
            painter = painterResource(R.drawable.ic_info),
            contentDescription = "Safe today details",
            tint = contentColor.copy(alpha = if (isLoading) 0.32f else 0.72f),
            modifier = Modifier
                .padding(top = 1.dp)
                .size(16.dp)
        )
    }
}

@Composable
private fun SummaryCardSecondaryMetrics(
    budgetState: BudgetState,
    secondaryMetricsProgress: Float,
    bottomOffsetPx: Float,
    progress: Float,
    contentColor: Color,
    animateCounters: Boolean,
    isLoading: Boolean,
    tagSecondaryMetrics: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = secondaryMetricsProgress
                translationY = bottomOffsetPx
            }
            .then(if (tagSecondaryMetrics) Modifier.testTag("home_summary_secondary_metrics") else Modifier)
            .collapseHeight(secondaryMetricsProgress),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SummaryMetric("Cycle left", contentColor = contentColor) {
            AnimatedCounter(
                amountCents = budgetState.remainingCycleCents,
                signed = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = contentColor,
                animate = animateCounters,
                animateOnFirstResolvedValue = animateCounters,
                placeholder = isLoading,
                placeholderText = "$8,888"
            )
        }
        SummaryMetric(
            "Days left",
            alignment = Alignment.End,
            alpha = summaryMetricAlpha(progress),
            contentColor = contentColor
        ) {
            AnimatedIntegerCounter(
                value = budgetState.daysRemainingInCycle,
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = contentColor,
                animate = animateCounters,
                animateOnFirstResolvedValue = animateCounters,
                placeholder = isLoading,
                placeholderText = "88"
            )
        }
        SummaryMetric("Spent today", alignment = Alignment.End, contentColor = contentColor) {
            AnimatedCounter(
                amountCents = budgetState.spentTodayCents,
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = contentColor,
                animate = animateCounters,
                animateOnFirstResolvedValue = animateCounters,
                placeholder = isLoading,
                placeholderText = "$888"
            )
        }
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    alignment: Alignment.Horizontal = Alignment.Start,
    alpha: Float = 1f,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    valueContent: @Composable () -> Unit
) {
    Column(
        horizontalAlignment = alignment,
        modifier = Modifier.graphicsLayer { this.alpha = alpha }
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.68f)
        )
        valueContent()
    }
}

private fun summaryMetricAlpha(progress: Float): Float = (1f - progress * 1.6f).coerceIn(0f, 1f)

private fun Modifier.collapseHeight(progress: Float): Modifier = this
    .graphicsLayer { clip = true }
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val height = (placeable.height * progress.coerceIn(0f, 1f)).roundToInt()
        layout(placeable.width, height) {
            if (height > 0) {
                placeable.placeRelative(0, 0)
            }
        }
    }

@Composable
internal fun blendedAlertContainer(): Color {
    return lerp(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
        0.72f
    )
}

@Composable
internal fun blendedAlertContent(): Color {
    return lerp(
        MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.onTertiaryContainer,
        0.72f
    )
}
