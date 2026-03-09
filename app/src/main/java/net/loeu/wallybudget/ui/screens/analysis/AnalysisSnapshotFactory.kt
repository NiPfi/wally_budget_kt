package net.loeu.wallybudget.ui.screens.analysis

import net.loeu.wallybudget.data.model.BudgetState
import net.loeu.wallybudget.data.model.MonthlyHistory
import net.loeu.wallybudget.data.model.SpendingForecast
import net.loeu.wallybudget.ui.calculateAvailableRecoverableOverspendCentsFromForecast
import net.loeu.wallybudget.ui.calculateSafeToSpendNowCents
import net.loeu.wallybudget.util.CurrencyFormatter
import kotlin.math.abs
import kotlin.math.max

internal object AnalysisSnapshotFactory {

    fun create(
        budgetState: BudgetState,
        spendingForecast: SpendingForecast,
        monthlyHistory: List<MonthlyHistory>,
        timelineLockReason: String?
    ): AnalysisSnapshot {
        val availableRecoverableOverspendCents = calculateAvailableRecoverableOverspendCentsFromForecast(
            remainingTodayCents = budgetState.remainingTodayCents,
            forecast = spendingForecast
        )
        val safeToSpendNowCents = calculateSafeToSpendNowCents(
            remainingTodayCents = budgetState.remainingTodayCents,
            availableRecoverableOverspendCents = availableRecoverableOverspendCents
        )
        val paceGapCents = spendingForecast.projectedDailySpendCents - budgetState.dailyBudgetCents
        val recentHistory = monthlyHistory.take(3)
        val historicalDeficitCount = recentHistory.count { it.surplusCents < 0L }
        val averageHistoricalSurplusCents = recentHistory
            .takeIf { it.isNotEmpty() }
            ?.let { history -> history.sumOf { it.surplusCents } / history.size }

        val confidenceBand = confidenceBand(spendingForecast.confidenceScore)
        val forecastRiskPoints = adjustedForecastRiskPoints(
            budgetState = budgetState,
            spendingForecast = spendingForecast,
            confidenceBand = confidenceBand
        )
        val paceRiskPoints = when {
            paceGapCents >= max(500L, budgetState.dailyBudgetCents / 10) -> 2
            paceGapCents > 0L -> 1
            else -> 0
        }
        val safeTodayRiskPoints = when {
            safeToSpendNowCents == 0L -> 2
            safeToSpendNowCents < max(1_000L, budgetState.dailyBudgetCents / 2) -> 1
            else -> 0
        }
        val historyRiskPoints = when {
            recentHistory.isEmpty() -> null
            historicalDeficitCount >= 2 -> 2
            historicalDeficitCount == 1 || (averageHistoricalSurplusCents ?: 0L) < 0L -> 1
            else -> 0
        }
        val hardRisk = budgetState.remainingCycleCents <= 0L ||
            (safeToSpendNowCents == 0L && budgetState.remainingTodayCents < 0L)
        val totalRiskPoints = forecastRiskPoints + paceRiskPoints + safeTodayRiskPoints + (historyRiskPoints ?: 0)
        val monitorAfterDays = monitorAfterDays(
            daysRemainingInCycle = budgetState.daysRemainingInCycle,
            confidenceBand = confidenceBand
        )

        var verdict = when {
            hardRisk || totalRiskPoints >= 6 -> AnalysisVerdictLevel.AtRisk
            totalRiskPoints >= 3 -> AnalysisVerdictLevel.Caution
            else -> AnalysisVerdictLevel.Stable
        }

        if (confidenceBand == ConfidenceBand.Low && !hardRisk && verdict == AnalysisVerdictLevel.AtRisk) {
            verdict = AnalysisVerdictLevel.Caution
        }

        val recommendations = buildRecommendations(
            budgetState = budgetState,
            spendingForecast = spendingForecast,
            safeToSpendNowCents = safeToSpendNowCents,
            paceGapCents = paceGapCents,
            monitorAfterDays = monitorAfterDays,
            timelineLockReason = timelineLockReason
        )
        val historyFallbackText = historyFallbackText(
            showHistoryFallback = recentHistory.isEmpty(),
            timelineLockReason = timelineLockReason
        )

        return AnalysisSnapshot(
            verdictLevel = verdict,
            headline = headline(verdict),
            summary = summary(
                verdict = verdict,
                confidenceBand = confidenceBand,
                budgetState = budgetState,
                safeToSpendNowCents = safeToSpendNowCents,
                monitorAfterDays = monitorAfterDays
            ),
            evidence = buildEvidence(
                budgetState = budgetState,
                spendingForecast = spendingForecast,
                paceGapCents = paceGapCents,
                safeToSpendNowCents = safeToSpendNowCents,
                availableRecoverableOverspendCents = availableRecoverableOverspendCents,
                recentHistory = recentHistory,
                historicalDeficitCount = historicalDeficitCount,
                averageHistoricalSurplusCents = averageHistoricalSurplusCents
            ),
            recommendations = recommendations,
            confidenceLabel = confidenceBand.label,
            confidenceExplanation = confidenceExplanation(
                confidenceBand = confidenceBand,
                monitorAfterDays = monitorAfterDays
            ),
            rangeExplanation = rangeExplanation(
                budgetState = budgetState,
                spendingForecast = spendingForecast
            ),
            monitorAfterDays = monitorAfterDays,
            showHistoryFallback = recentHistory.isEmpty(),
            historyFallbackText = historyFallbackText
        )
    }

    private fun adjustedForecastRiskPoints(
        budgetState: BudgetState,
        spendingForecast: SpendingForecast,
        confidenceBand: ConfidenceBand
    ): Int {
        val baseRisk = when {
            spendingForecast.estimatedEndCycleRemainingCents < 0L -> 3
            spendingForecast.upperBoundCents > budgetState.monthlyBudgetCents -> 2
            else -> 0
        }

        if (confidenceBand != ConfidenceBand.Low) return baseRisk
        if (baseRisk == 0) return 0
        if (spendingForecast.estimatedEndCycleRemainingCents < 0L) {
            return (baseRisk - 1).coerceAtLeast(1)
        }
        return (baseRisk - 1).coerceAtLeast(0)
    }

    private fun buildEvidence(
        budgetState: BudgetState,
        spendingForecast: SpendingForecast,
        paceGapCents: Long,
        safeToSpendNowCents: Long,
        availableRecoverableOverspendCents: Long,
        recentHistory: List<MonthlyHistory>,
        historicalDeficitCount: Int,
        averageHistoricalSurplusCents: Long?
    ): List<AnalysisEvidenceItem> {
        val items = mutableListOf<AnalysisEvidenceItem>()

        items += forecastEvidence(budgetState, spendingForecast)
        items += paceEvidence(budgetState, spendingForecast, paceGapCents)
        items += safeTodayEvidence(safeToSpendNowCents, availableRecoverableOverspendCents)

        if (recentHistory.isNotEmpty()) {
            items += historyEvidence(recentHistory, historicalDeficitCount, averageHistoricalSurplusCents ?: 0L)
        }

        return items
    }

    private fun forecastEvidence(
        budgetState: BudgetState,
        spendingForecast: SpendingForecast
    ): AnalysisEvidenceItem {
        return when {
            spendingForecast.estimatedEndCycleRemainingCents < 0L -> AnalysisEvidenceItem(
                title = "Forecast pressure",
                value = "Over by ${CurrencyFormatter.format(abs(spendingForecast.estimatedEndCycleRemainingCents))}",
                detail = "Current projection ends above your ${CurrencyFormatter.format(budgetState.monthlyBudgetCents)} cycle budget.",
                tone = AnalysisEvidenceTone.Critical
            )
            spendingForecast.upperBoundCents > budgetState.monthlyBudgetCents -> AnalysisEvidenceItem(
                title = "Forecast pressure",
                value = "Range crosses budget",
                detail = "Best estimate stays under, but the upper range reaches ${CurrencyFormatter.format(spendingForecast.upperBoundCents)}.",
                tone = AnalysisEvidenceTone.Warning
            )
            else -> AnalysisEvidenceItem(
                title = "Forecast pressure",
                value = "Within budget",
                detail = "Projected finish leaves ${CurrencyFormatter.format(spendingForecast.estimatedEndCycleRemainingCents.coerceAtLeast(0L))} in the cycle.",
                tone = AnalysisEvidenceTone.Positive
            )
        }
    }

    private fun paceEvidence(
        budgetState: BudgetState,
        spendingForecast: SpendingForecast,
        paceGapCents: Long
    ): AnalysisEvidenceItem {
        return if (paceGapCents > 0L) {
            AnalysisEvidenceItem(
                title = "Daily pace",
                value = "${CurrencyFormatter.format(paceGapCents)} above target",
                detail = "Projected pace is ${CurrencyFormatter.format(spendingForecast.projectedDailySpendCents)} vs ${CurrencyFormatter.format(budgetState.dailyBudgetCents)} daily budget.",
                tone = if (paceGapCents >= max(500L, budgetState.dailyBudgetCents / 10)) {
                    AnalysisEvidenceTone.Warning
                } else {
                    AnalysisEvidenceTone.Neutral
                }
            )
        } else {
            AnalysisEvidenceItem(
                title = "Daily pace",
                value = "At or below target",
                detail = "Projected pace is ${CurrencyFormatter.format(spendingForecast.projectedDailySpendCents)} against ${CurrencyFormatter.format(budgetState.dailyBudgetCents)} per day.",
                tone = AnalysisEvidenceTone.Positive
            )
        }
    }

    private fun safeTodayEvidence(
        safeToSpendNowCents: Long,
        availableRecoverableOverspendCents: Long
    ): AnalysisEvidenceItem {
        val detail = if (availableRecoverableOverspendCents > 0L) {
            "${CurrencyFormatter.format(availableRecoverableOverspendCents)} of recoverable headroom is still available today."
        } else {
            "There is no recoverable buffer left beyond today's remaining allowance."
        }

        return AnalysisEvidenceItem(
            title = "Safe today",
            value = CurrencyFormatter.format(safeToSpendNowCents),
            detail = detail,
            tone = when {
                safeToSpendNowCents == 0L -> AnalysisEvidenceTone.Critical
                availableRecoverableOverspendCents > 0L -> AnalysisEvidenceTone.Positive
                else -> AnalysisEvidenceTone.Warning
            }
        )
    }

    private fun historyEvidence(
        recentHistory: List<MonthlyHistory>,
        historicalDeficitCount: Int,
        averageHistoricalSurplusCents: Long
    ): AnalysisEvidenceItem {
        val averageText = if (averageHistoricalSurplusCents >= 0L) {
            "Average recent finish is ${CurrencyFormatter.format(averageHistoricalSurplusCents)} under budget."
        } else {
            "Average recent finish is ${CurrencyFormatter.format(abs(averageHistoricalSurplusCents))} over budget."
        }

        val value = when {
            historicalDeficitCount >= 2 -> "${historicalDeficitCount} of ${recentHistory.size} over"
            historicalDeficitCount == 1 -> "Mixed recent cycles"
            else -> "Recent history supportive"
        }

        return AnalysisEvidenceItem(
            title = "Historical tendency",
            value = value,
            detail = averageText,
            tone = when {
                historicalDeficitCount >= 2 -> AnalysisEvidenceTone.Warning
                historicalDeficitCount == 1 || averageHistoricalSurplusCents < 0L -> AnalysisEvidenceTone.Neutral
                else -> AnalysisEvidenceTone.Positive
            }
        )
    }

    private fun buildRecommendations(
        budgetState: BudgetState,
        spendingForecast: SpendingForecast,
        safeToSpendNowCents: Long,
        paceGapCents: Long,
        monitorAfterDays: Int?,
        timelineLockReason: String?
    ): List<AnalysisRecommendation> {
        val recommendations = mutableListOf<AnalysisRecommendation>()
        val timelineLocked = timelineLockReason != null

        when {
            safeToSpendNowCents == 0L -> {
                recommendations += AnalysisRecommendation(
                    text = "Pause non-essential spending today until headroom recovers."
                )
            }
            safeToSpendNowCents < budgetState.dailyBudgetCents -> {
                recommendations += AnalysisRecommendation(
                    text = "Keep discretionary spend under ${CurrencyFormatter.format(safeToSpendNowCents)} today."
                )
            }
        }

        if (paceGapCents > 0L) {
            recommendations += AnalysisRecommendation(
                text = "Pull daily pace down by about ${CurrencyFormatter.format(paceGapCents)} to get closer to target."
            )
        }

        if (spendingForecast.estimatedEndCycleRemainingCents < 0L || spendingForecast.upperBoundCents > budgetState.monthlyBudgetCents) {
            val timingSuffix = monitorAfterDays?.let { " and check back in $it day${if (it == 1) "" else "s"}." }
                ?: "."
            recommendations += AnalysisRecommendation(
                text = "Treat the forecast range as a warning while you tighten spending$timingSuffix"
            )
        }

        if (recommendations.isEmpty()) {
            recommendations += AnalysisRecommendation(
                text = if (timelineLocked) {
                    "Stay near or below the current projected daily pace."
                } else {
                    "Stay near or below the current projected daily pace to hold this trajectory."
                }
            )
        }

        return recommendations.take(3)
    }

    private fun historyFallbackText(
        showHistoryFallback: Boolean,
        timelineLockReason: String?
    ): String? {
        if (!showHistoryFallback) return null

        return if (timelineLockReason != null) {
            "History is still building. Guidance will sharpen after your first completed cycle is archived."
        } else {
            "Keep recording through this cycle. Guidance will sharpen after a completed cycle closes."
        }
    }

    private fun headline(verdict: AnalysisVerdictLevel): String = when (verdict) {
        AnalysisVerdictLevel.Stable -> "On track"
        AnalysisVerdictLevel.Caution -> "Needs attention"
        AnalysisVerdictLevel.AtRisk -> "Risk of overspending"
    }

    private fun summary(
        verdict: AnalysisVerdictLevel,
        confidenceBand: ConfidenceBand,
        budgetState: BudgetState,
        safeToSpendNowCents: Long,
        monitorAfterDays: Int?
    ): String {
        if (budgetState.remainingCycleCents <= 0L) {
            return "You have already exhausted this cycle's budget, so further spend increases the deficit."
        }

        return when (verdict) {
            AnalysisVerdictLevel.Stable -> {
                "Current pace and safe-today headroom still support an on-budget finish."
            }

            AnalysisVerdictLevel.Caution -> {
                if (confidenceBand == ConfidenceBand.Low) {
                    val days = monitorAfterDays ?: 2
                    "The forecast is still building, but current pace and headroom need a closer watch. Check back in $days day${if (days == 1) "" else "s"}."
                } else if (safeToSpendNowCents == 0L) {
                    "You can still recover, but today's cushion is gone and the rest of the cycle is tighter."
                } else {
                    "You can still recover, but the current pace is running tighter than your budget allows."
                }
            }

            AnalysisVerdictLevel.AtRisk -> {
                if (confidenceBand == ConfidenceBand.High) {
                    "Your current pace is likely to finish over budget unless you tighten spending now."
                } else {
                    val days = monitorAfterDays ?: 2
                    "Signals are pointing high, but the forecast is still building. Keep spending tight and re-check in $days day${if (days == 1) "" else "s"}."
                }
            }
        }
    }

    private fun confidenceExplanation(
        confidenceBand: ConfidenceBand,
        monitorAfterDays: Int?
    ): String {
        return when (confidenceBand) {
            ConfidenceBand.High -> "Confidence is high. The forecast has enough cycle data to anchor your current pace."
            ConfidenceBand.Medium -> {
                val days = monitorAfterDays ?: 3
                "Confidence is moderate. The signal is usable, but the range can still move over the next $days day${if (days == 1) "" else "s"}."
            }
            ConfidenceBand.Low -> {
                val days = monitorAfterDays ?: 2
                "Confidence is low because this cycle still has limited signal. Use this as direction, not certainty, and check back in $days day${if (days == 1) "" else "s"}."
            }
        }
    }

    private fun rangeExplanation(
        budgetState: BudgetState,
        spendingForecast: SpendingForecast
    ): String {
        val base = "Forecast range runs from ${CurrencyFormatter.format(spendingForecast.lowerBoundCents)} to ${CurrencyFormatter.format(spendingForecast.upperBoundCents)} total spend by cycle end."
        return if (spendingForecast.upperBoundCents > budgetState.monthlyBudgetCents) {
            "$base The upper end crosses your ${CurrencyFormatter.format(budgetState.monthlyBudgetCents)} budget."
        } else {
            base
        }
    }

    private fun monitorAfterDays(
        daysRemainingInCycle: Int,
        confidenceBand: ConfidenceBand
    ): Int? = when {
        daysRemainingInCycle <= 3 -> 1
        confidenceBand == ConfidenceBand.Low -> 2
        confidenceBand == ConfidenceBand.Medium -> 3
        else -> null
    }

    private fun confidenceBand(confidenceScore: Double): ConfidenceBand = when {
        confidenceScore >= 0.70 -> ConfidenceBand.High
        confidenceScore >= 0.55 -> ConfidenceBand.Medium
        else -> ConfidenceBand.Low
    }

    private enum class ConfidenceBand(val label: String) {
        High("High"),
        Medium("Medium"),
        Low("Low")
    }
}
