@file:Suppress("CyclomaticComplexMethod", "LongMethod", "MaxLineLength", "TooManyFunctions")

package net.loeu.wallybudget.ui.screens.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import net.loeu.wallybudget.R
import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.domain.model.MonthlyHistory
import net.loeu.wallybudget.domain.model.SpendingForecast
import net.loeu.wallybudget.ui.CurrencyPlaceholderSamples
import net.loeu.wallybudget.ui.components.TimelineLockBanner
import net.loeu.wallybudget.ui.screens.overview.CollapsingSummaryLayout
import net.loeu.wallybudget.ui.screens.overview.CollapsingSummaryLayoutConfig
import net.loeu.wallybudget.ui.screens.overview.LocalCollapsingHeaderIsForMeasurement
import net.loeu.wallybudget.ui.screens.overview.LoadingValuePlaceholder
import net.loeu.wallybudget.ui.screens.overview.rememberOverviewPageLayoutState
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun AnalysisScreen(
    budgetState: BudgetState?,
    spendingForecast: SpendingForecast?,
    monthlyHistory: List<MonthlyHistory>,
    effectiveCurrentDate: LocalDate,
    timelineLockReason: String?,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    onNavigateToSettings: (() -> Unit)? = null
) {
    val snapshot = remember(
        budgetState, spendingForecast, monthlyHistory, timelineLockReason, isLoading
    ) {
        if (!isLoading && budgetState != null && spendingForecast != null) {
            AnalysisSnapshotFactory.create(
                budgetState = budgetState,
                spendingForecast = spendingForecast,
                monthlyHistory = monthlyHistory,
                timelineLockReason = timelineLockReason
            )
        } else {
            null
        }
    }
    val layoutState = rememberOverviewPageLayoutState(
        defaultCollapsedHeader = false,
        enableHeaderCollapse = true
    )

    CollapsingSummaryLayout(
        layoutState = layoutState,
        config = CollapsingSummaryLayoutConfig(
            modifier = modifier,
            enableHeaderCollapse = true,
            bottomContentPadding = 24.dp,
            headerHorizontalPadding = 0.dp,
            headerTopPadding = 0.dp,
            headerBottomSpacing = 0.dp
        ),
        header = { collapseProgress ->
            VerdictHeroBlock(
                snapshot = snapshot,
                isLoading = isLoading,
                onNavigateBack = onNavigateBack,
                onNavigateToSettings = onNavigateToSettings,
                collapseProgress = collapseProgress,
                showTestTags = !LocalCollapsingHeaderIsForMeasurement.current
            )
        }
    ) { listState, contentPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("analysis_list"),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            timelineLockReason?.let { reason ->
                item { TimelineLockBanner(reason = reason) }
            }

            item {
                SectionDivider()
                SpendingTrajectorySection(
                    budgetState = budgetState,
                    spendingForecast = spendingForecast,
                    effectiveCurrentDate = effectiveCurrentDate,
                    isLoading = isLoading
                )
            }

            item {
                SectionDivider()
                EvidenceSection(snapshot = snapshot, isLoading = isLoading)
            }

            if (!isLoading && monthlyHistory.isNotEmpty()) {
                item {
                    SectionDivider()
                    RecentHistorySection(monthlyHistory = monthlyHistory)
                }
            }

            item {
                SectionDivider()
                RecommendationsSection(snapshot = snapshot, isLoading = isLoading)
            }

            item {
                SectionDivider()
                ConfidenceSection(snapshot = snapshot, isLoading = isLoading)
            }
        }
    }
}

@Composable
private fun VerdictHeroBlock(
    snapshot: AnalysisSnapshot?,
    isLoading: Boolean,
    onNavigateBack: (() -> Unit)?,
    onNavigateToSettings: (() -> Unit)?,
    collapseProgress: Float,
    showTestTags: Boolean
) {
    val progress = collapseProgress.coerceIn(0f, 1f)
    val contentOffset = lerp(0.dp, (-6).dp, progress)
    val contentSpacing = lerp(8.dp, 4.dp, progress)
    val bodyBottomPadding = lerp(20.dp, 10.dp, progress)
    val headlineSize = lerp(28.sp, 22.sp, progress)
    val detailsVisibility = (1f - progress * 1.2f).coerceIn(0f, 1f)
    val stateDescription = when {
        isLoading -> "Analysis loading"
        snapshot?.verdictLevel == AnalysisVerdictLevel.AtRisk -> "Analysis verdict at risk"
        snapshot?.verdictLevel == AnalysisVerdictLevel.Caution -> "Analysis verdict caution"
        snapshot?.verdictLevel == AnalysisVerdictLevel.Watchful -> "Analysis verdict watchful"
        else -> "Analysis verdict stable"
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(verdictContainerColor(snapshot?.verdictLevel, isLoading))
            .then(if (showTestTags) Modifier.testTag("analysis_verdict_section") else Modifier)
            .semantics { this.stateDescription = stateDescription }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = bodyBottomPadding),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = "Go back"
                            )
                        }
                    }
                    Text(
                        text = "Analysis",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = if (onNavigateBack != null) 0.dp else 12.dp)
                    )
                }
                if (onNavigateToSettings != null) {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = "Open settings"
                        )
                    }
                }
            }

            if (isLoading || snapshot == null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .offset(y = contentOffset),
                    verticalArrangement = Arrangement.spacedBy(contentSpacing)
                ) {
                    LoadingValuePlaceholder(
                        sampleText = "Needs attention",
                        textStyle = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = headlineSize,
                            fontWeight = FontWeight.Bold
                        ),
                        textAlign = TextAlign.Start
                    )
                    Column(
                        modifier = Modifier.collapseHeight(detailsVisibility),
                        verticalArrangement = Arrangement.spacedBy(contentSpacing)
                    ) {
                        LoadingValuePlaceholder(
                            sampleText = "Current pace and safe-today headroom still support an on-budget finish.",
                            textStyle = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Start,
                            fillWidth = true
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .offset(y = contentOffset),
                    verticalArrangement = Arrangement.spacedBy(contentSpacing)
                ) {
                    Text(
                        text = snapshot.headline,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = headlineSize,
                            fontWeight = FontWeight.Bold
                        ),
                        fontWeight = FontWeight.Bold,
                        maxLines = if (progress >= 0.5f) 1 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Column(
                        modifier = Modifier.collapseHeight(detailsVisibility),
                        verticalArrangement = Arrangement.spacedBy(contentSpacing)
                    ) {
                        Row(
                            modifier = Modifier.graphicsLayer { alpha = detailsVisibility },
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Confidence",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "·",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                            Text(
                                text = snapshot.confidenceLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = snapshot.summary,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.graphicsLayer { alpha = detailsVisibility },
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

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
private fun EvidenceSection(
    snapshot: AnalysisSnapshot?,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("analysis_evidence_section"),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Text(
            text = "Why this verdict",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        if (isLoading || snapshot == null) {
            repeat(4) {
                ItemDivider()
                PlaceholderEvidenceRow()
            }
        } else {
            snapshot.evidence.forEach { item ->
                ItemDivider()
                EvidenceItemRow(item = item)
            }
        }
    }
}

@Composable
private fun EvidenceItemRow(item: AnalysisEvidenceItem) {
    val valueColor = evidenceToneValueColor(item.tone)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = item.value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        item.gauge?.let { gauge ->
            MiniGaugeBar(gauge = gauge, tone = item.tone)
        }
        Text(
            text = item.detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecommendationsSection(
    snapshot: AnalysisSnapshot?,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("analysis_actions_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "What to do now",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        if (isLoading || snapshot == null) {
            repeat(3) {
                LoadingValuePlaceholder(
                    sampleText = "Keep discretionary spend under your current safe-today headroom.",
                    textStyle = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                    fillWidth = true
                )
            }
        } else {
            snapshot.recommendations.forEachIndexed { index, recommendation ->
                RecommendationRow(index = index, text = recommendation.text)
            }
            snapshot.historyFallbackText?.let { historyFallbackText ->
                Text(
                    text = historyFallbackText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("analysis_history_fallback")
                )
            }
        }
    }
}

@Composable
private fun ConfidenceSection(
    snapshot: AnalysisSnapshot?,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("analysis_confidence_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Confidence and range",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        if (isLoading || snapshot == null) {
            LoadingValuePlaceholder(
                sampleText =
                    "Confidence is moderate. The signal is usable, but the " +
                        "range can still move over the next 3 days.",
                textStyle = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                fillWidth = true
            )
            LoadingValuePlaceholder(
                sampleText = CurrencyPlaceholderSamples.forecastRangeSummary(
                    lowerAmountCents = 180_000L,
                    upperAmountCents = 245_000L
                ),
                textStyle = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                fillWidth = true
            )
        } else {
            Text(
                text = snapshot.confidenceExplanation,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = snapshot.rangeExplanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlaceholderEvidenceRow() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LoadingValuePlaceholder(
            sampleText = "Forecast range",
            textStyle = MaterialTheme.typography.labelLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Start
        )
        LoadingValuePlaceholder(
            sampleText = "Best estimate still under",
            textStyle = MaterialTheme.typography.titleMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Start
        )
        LoadingValuePlaceholder(
            sampleText = CurrencyPlaceholderSamples.forecastRangeDetail(
                remainingAmountCents = 20_000L,
                upperAmountCents = 245_000L
            ),
            textStyle = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Start,
            fillWidth = true
        )
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun ItemDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(top = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    )
}
