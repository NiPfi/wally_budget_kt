package net.loeu.wallybudget.ui.screens.analysis

internal enum class AnalysisVerdictLevel {
    Stable,
    Watchful,
    Caution,
    AtRisk
}

internal enum class AnalysisEvidenceTone {
    Neutral,
    Positive,
    Warning,
    Critical
}

/**
 * Optional visual gauge data for an evidence card.
 * Renders as a thin horizontal bar: fill to [valueCents], tick at [targetCents],
 * and (if present) a shaded band between [lowerCents] and [upperCents].
 */
internal data class EvidenceGauge(
    val valueCents: Long,
    val targetCents: Long,
    val maxCents: Long,
    val lowerCents: Long? = null,
    val upperCents: Long? = null
)

internal data class AnalysisEvidenceItem(
    val title: String,
    val value: String,
    val detail: String,
    val tone: AnalysisEvidenceTone,
    val gauge: EvidenceGauge? = null
)

internal data class AnalysisRecommendation(
    val text: String
)

internal data class AnalysisSnapshot(
    val verdictLevel: AnalysisVerdictLevel,
    val headline: String,
    val summary: String,
    val evidence: List<AnalysisEvidenceItem>,
    val recommendations: List<AnalysisRecommendation>,
    val confidenceLabel: String,
    val confidenceExplanation: String,
    val rangeExplanation: String,
    val monitorAfterDays: Int?,
    val showHistoryFallback: Boolean,
    val historyFallbackText: String? = null
)
