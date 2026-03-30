package net.loeu.wallybudget.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import net.loeu.wallybudget.domain.model.SelectedBucketOverview
import net.loeu.wallybudget.ui.screens.overview.LocalCollapsingHeaderIsForMeasurement
import net.loeu.wallybudget.ui.screens.overview.MergedSummaryHeaderSurface
import net.loeu.wallybudget.ui.screens.overview.summaryCardColors
import net.loeu.wallybudget.util.CurrencyFormatter
import kotlin.math.roundToInt

@Composable
internal fun DailyPacingSummaryCard(
    selectedBucketOverview: SelectedBucketOverview,
    collapseProgress: Float,
    onNavigateToAnalysis: (() -> Unit)?,
    onNavigateToSettings: (() -> Unit)?
) {
    val showTestTags = !LocalCollapsingHeaderIsForMeasurement.current
    val summary = selectedBucketOverview.summary
    TopSummaryCard(
        title = selectedBucketOverview.bucket.name,
        amountText = CurrencyFormatter.formatSigned(summary.remainingThisCycleCents),
        subtitleText = null,
        collapseProgress = collapseProgress,
        useWarningTint = summary.remainingThisCycleCents < 0L || summary.overspentCents > 0L,
        onNavigateToAnalysis = onNavigateToAnalysis,
        onNavigateToSettings = onNavigateToSettings,
        headerRowTestTag = if (showTestTags) "home_page_header_row" else null,
        titleTestTag = if (showTestTags) "home_page_header_title" else null,
        analysisTestTag = if (showTestTags) "home_page_header_analysis" else null,
        settingsTestTag = if (showTestTags) "home_page_header_settings" else null
    ) { contentColor, progress ->
        BucketCollapsingMetricsRow(
            visibilityProgress = (1f - progress * 1.15f).coerceIn(0f, 1f)
        ) {
            BucketSummaryMetricColumn(
                "Allocated",
                CurrencyFormatter.format(summary.allocatedThisCycleCents),
                contentColor
            )
            BucketSummaryMetricColumn(
                "Spent",
                CurrencyFormatter.format(summary.spentThisCycleCents),
                contentColor
            )
            if (summary.earmarkedBalanceCents > 0L) {
                BucketSummaryMetricColumn(
                    "Earmarked",
                    CurrencyFormatter.format(summary.earmarkedBalanceCents),
                    contentColor
                )
            }
        }
    }
}

@Composable
internal fun MonthlyTotalSummaryCard(
    selectedBucketOverview: SelectedBucketOverview,
    collapseProgress: Float,
    onNavigateToAnalysis: (() -> Unit)?,
    onNavigateToSettings: (() -> Unit)?
) {
    val showTestTags = !LocalCollapsingHeaderIsForMeasurement.current
    val summary = selectedBucketOverview.summary
    val budgetState = selectedBucketOverview.budgetState
    val useWarningTint = summary.remainingThisCycleCents < 0L || summary.overspentCents > 0L
    TopSummaryCard(
        title = selectedBucketOverview.bucket.name,
        amountText = CurrencyFormatter.formatSigned(summary.remainingThisCycleCents),
        subtitleText = "CYCLE LEFT",
        collapseProgress = collapseProgress,
        useWarningTint = useWarningTint,
        onNavigateToAnalysis = onNavigateToAnalysis,
        onNavigateToSettings = onNavigateToSettings,
        headerRowTestTag = if (showTestTags) "home_page_header_row" else null,
        titleTestTag = if (showTestTags) "home_page_header_title" else null,
        analysisTestTag = if (showTestTags) "home_page_header_analysis" else null,
        settingsTestTag = if (showTestTags) "home_page_header_settings" else null
    ) { contentColor, progress ->
        BucketCollapsingMetricsRow(
            visibilityProgress = (1f - progress * 1.15f).coerceIn(0f, 1f)
        ) {
            BucketSummaryMetricColumn(
                "Allocated",
                CurrencyFormatter.format(summary.allocatedThisCycleCents),
                contentColor
            )
            BucketSummaryMetricColumn(
                "Spent",
                CurrencyFormatter.format(summary.spentThisCycleCents),
                contentColor
            )
            BucketSummaryMetricColumn(
                "Days left",
                budgetState?.daysRemainingInCycle?.toString() ?: "—",
                contentColor
            )
        }
    }
}

@Composable
internal fun TopSummaryCard(
    title: String,
    amountText: String,
    subtitleText: String?,
    collapseProgress: Float,
    useWarningTint: Boolean,
    onNavigateToAnalysis: (() -> Unit)?,
    onNavigateToSettings: (() -> Unit)?,
    headerRowTestTag: String? = null,
    titleTestTag: String? = null,
    analysisTestTag: String? = null,
    settingsTestTag: String? = null,
    metrics: @Composable RowScope.(contentColor: Color, progress: Float) -> Unit
) {
    val colors = summaryCardColors(useWarningTint = useWarningTint)
    val progress = collapseProgress.coerceIn(0f, 1f)
    val horizontalPadding = lerp(20.dp, 16.dp, progress)
    val verticalPadding = lerp(18.dp, 10.dp, progress)
    val mergedHeaderBodyOffset = lerp(0.dp, (-6).dp, progress)
    val contentSpacing = lerp(12.dp, 6.dp, progress)
    val amountFontSize = lerp(34.sp, 24.sp, progress)

    MergedSummaryHeaderSurface(
        title = title,
        summaryColors = colors,
        modifier = Modifier.fillMaxWidth(),
        headerHorizontalPadding = horizontalPadding,
        headerBottomPadding = 0.dp,
        onNavigateToAnalysis = onNavigateToAnalysis,
        onNavigateToSettings = onNavigateToSettings,
        headerRowTestTag = headerRowTestTag,
        titleTestTag = titleTestTag,
        analysisTestTag = analysisTestTag,
        settingsTestTag = settingsTestTag
    ) {
        Column(
            modifier = Modifier
                .padding(
                    start = horizontalPadding,
                    top = 0.dp,
                    end = horizontalPadding,
                    bottom = verticalPadding
                )
                .offset(y = mergedHeaderBodyOffset),
            verticalArrangement = Arrangement.spacedBy(contentSpacing)
        ) {
            Text(
                text = amountText,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = amountFontSize,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Black
                ),
                fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                color = colors.content
            )
            subtitleText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.content.copy(alpha = 0.72f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                metrics(colors.content, progress)
            }
        }
    }
}

@Composable
internal fun BucketSummaryMetricColumn(label: String, value: String, contentColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = contentColor
        )
    }
}

@Composable
internal fun PlainMetricRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        }
        HorizontalDivider()
    }
}

@Composable
internal fun BucketCollapsingMetricsRow(
    visibilityProgress: Float,
    content: @Composable RowScope.() -> Unit
) {
    val progress = visibilityProgress.coerceIn(0f, 1f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = progress
            }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val targetHeight = (placeable.height * progress).roundToInt()
                layout(placeable.width, targetHeight) {
                    placeable.placeRelative(0, ((targetHeight - placeable.height) / 2f).roundToInt())
                }
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
        content = content
    )
}
