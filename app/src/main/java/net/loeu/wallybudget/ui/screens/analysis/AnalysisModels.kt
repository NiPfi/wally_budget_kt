package net.loeu.wallybudget.ui.screens.analysis

internal enum class AnalysisVerdictLevel {
    Stable,
    Caution,
    AtRisk
}

internal enum class AnalysisEvidenceTone {
    Neutral,
    Positive,
    Warning,
    Critical
}

internal data class AnalysisEvidenceItem(
    val title: String,
    val value: String,
    val detail: String,
    val tone: AnalysisEvidenceTone
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
