package net.loeu.wallybudget.ui.screens.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.domain.model.MonthlyHistory
import net.loeu.wallybudget.domain.model.SpendingForecast
import net.loeu.wallybudget.ui.components.TimelineLockBanner
import net.loeu.wallybudget.ui.screens.overview.LoadingValuePlaceholder

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AnalysisScreen(
    budgetState: BudgetState,
    spendingForecast: SpendingForecast,
    monthlyHistory: List<MonthlyHistory>,
    timelineLockReason: String?,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    onNavigateToSettings: (() -> Unit)? = null
) {
    val isWideLayout = currentWindowAdaptiveInfo()
        .windowSizeClass
        .isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)

    val snapshot = remember(
        budgetState,
        spendingForecast,
        monthlyHistory,
        timelineLockReason,
        isLoading
    ) {
        if (!isLoading) {
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

    LazyColumn(
        modifier = modifier
            .statusBarsPadding()
            .testTag("analysis_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HeaderRow(onNavigateToSettings = onNavigateToSettings)
        }

        timelineLockReason?.let { reason ->
            item {
                TimelineLockBanner(reason = reason)
            }
        }

        item {
            VerdictSection(snapshot = snapshot, isLoading = isLoading)
        }

        item {
            EvidenceSection(snapshot = snapshot, isLoading = isLoading, isWideLayout = isWideLayout)
        }

        item {
            RecommendationsSection(snapshot = snapshot, isLoading = isLoading)
        }

        item {
            ConfidenceSection(snapshot = snapshot, isLoading = isLoading)
        }
    }
}

@Composable
private fun HeaderRow(
    onNavigateToSettings: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Analysis",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "Verdict, evidence, and next steps from your current budget signals.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onNavigateToSettings != null) {
            IconButton(onClick = onNavigateToSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Open settings"
                )
            }
        }
    }
}

@Composable
private fun VerdictSection(
    snapshot: AnalysisSnapshot?,
    isLoading: Boolean
) {
    val stateDescription = when {
        isLoading -> "Analysis loading"
        snapshot?.verdictLevel == AnalysisVerdictLevel.AtRisk -> "Analysis verdict at risk"
        snapshot?.verdictLevel == AnalysisVerdictLevel.Caution -> "Analysis verdict caution"
        snapshot?.verdictLevel == AnalysisVerdictLevel.Watchful -> "Analysis verdict watchful"
        else -> "Analysis verdict stable"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("analysis_verdict_section")
            .semantics { this.stateDescription = stateDescription },
        color = verdictContainerColor(snapshot?.verdictLevel, isLoading),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isLoading || snapshot == null) {
                LoadingValuePlaceholder(
                    sampleText = "Needs attention",
                    textStyle = MaterialTheme.typography.headlineSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start
                )
                LoadingValuePlaceholder(
                    sampleText = "Current pace and safe-today headroom still support an on-budget finish.",
                    textStyle = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                    fillWidth = true
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = snapshot.headline,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    ConfidenceBadge(
                        label = snapshot.confidenceLabel,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Text(
                        text = snapshot.summary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfidenceBadge(
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Confidence",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun EvidenceSection(
    snapshot: AnalysisSnapshot?,
    isLoading: Boolean,
    isWideLayout: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("analysis_evidence_section"),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Why this verdict",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            if (isLoading || snapshot == null) {
                repeat(4) {
                    PlaceholderEvidenceCard()
                }
            } else if (isWideLayout) {
                val rows = snapshot.evidence.chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    rows.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            rowItems.forEach { item ->
                                EvidenceCard(
                                    item = item,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowItems.size == 1) {
                                Box(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    snapshot.evidence.forEach { item ->
                        EvidenceCard(item = item)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationsSection(
    snapshot: AnalysisSnapshot?,
    isLoading: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("analysis_actions_section"),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
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
                    RecommendationRow(
                        index = index,
                        text = recommendation.text
                    )
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
}

@Composable
private fun ConfidenceSection(
    snapshot: AnalysisSnapshot?,
    isLoading: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("analysis_confidence_section"),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Confidence and range",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            if (isLoading || snapshot == null) {
                LoadingValuePlaceholder(
                    sampleText = "Confidence is moderate. The signal is usable, but the range can still move over the next 3 days.",
                    textStyle = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                    fillWidth = true
                )
                LoadingValuePlaceholder(
                    sampleText = "Forecast range runs from $1,800.00 to $2,450.00 total spend by cycle end.",
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
}

@Composable
private fun EvidenceCard(
    item: AnalysisEvidenceItem,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = evidenceContainerColor(item.tone),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = item.value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = item.detail,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun PlaceholderEvidenceCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                sampleText = "Current projection still leaves $200.00, but the upper range reaches $2,450.00.",
                textStyle = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                fillWidth = true
            )
        }
    }
}

@Composable
private fun RecommendationRow(
    index: Int,
    text: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(22.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (index + 1).toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun verdictContainerColor(
    verdict: AnalysisVerdictLevel?,
    isLoading: Boolean
): Color = when {
    isLoading -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    verdict == AnalysisVerdictLevel.AtRisk -> MaterialTheme.colorScheme.errorContainer
    verdict == AnalysisVerdictLevel.Caution -> MaterialTheme.colorScheme.tertiaryContainer
    verdict == AnalysisVerdictLevel.Watchful -> MaterialTheme.colorScheme.secondaryContainer
    else -> MaterialTheme.colorScheme.primaryContainer
}

@Composable
private fun evidenceContainerColor(
    tone: AnalysisEvidenceTone
): Color = when (tone) {
    AnalysisEvidenceTone.Positive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    AnalysisEvidenceTone.Warning -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
    AnalysisEvidenceTone.Critical -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
    AnalysisEvidenceTone.Neutral -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
}
