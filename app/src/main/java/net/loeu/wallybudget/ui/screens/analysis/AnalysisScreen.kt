@file:Suppress("LongMethod", "MaxLineLength", "TooManyFunctions")

package net.loeu.wallybudget.ui.screens.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.loeu.wallybudget.R
import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.domain.model.MonthlyHistory
import net.loeu.wallybudget.domain.model.SpendingForecast
import net.loeu.wallybudget.ui.CurrencyPlaceholderSamples
import net.loeu.wallybudget.ui.components.TimelineLockBanner
import net.loeu.wallybudget.ui.screens.overview.LoadingValuePlaceholder

@Composable
fun AnalysisScreen(
    budgetState: BudgetState?,
    spendingForecast: SpendingForecast?,
    monthlyHistory: List<MonthlyHistory>,
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

    LazyColumn(
        modifier = modifier
            .statusBarsPadding()
            .testTag("analysis_list"),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            VerdictHeroBlock(
                snapshot = snapshot,
                isLoading = isLoading,
                onNavigateBack = onNavigateBack,
                onNavigateToSettings = onNavigateToSettings
            )
        }

        timelineLockReason?.let { reason ->
            item { TimelineLockBanner(reason = reason) }
        }

        item {
            SectionDivider()
            SpendingTrajectorySection(
                budgetState = budgetState,
                spendingForecast = spendingForecast,
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

@Composable
private fun VerdictHeroBlock(
    snapshot: AnalysisSnapshot?,
    isLoading: Boolean,
    onNavigateBack: (() -> Unit)?,
    onNavigateToSettings: (() -> Unit)?
) {
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
            .testTag("analysis_verdict_section")
            .semantics { this.stateDescription = stateDescription }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = snapshot.headline,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
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
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
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
