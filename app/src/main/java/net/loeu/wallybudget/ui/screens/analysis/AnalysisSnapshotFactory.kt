package net.loeu.wallybudget.ui.screens.analysis

import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.domain.model.MonthlyHistory
import net.loeu.wallybudget.domain.model.SpendingForecast
import net.loeu.wallybudget.ui.calculateAvailableRecoverableOverspendCentsFromForecast
import net.loeu.wallybudget.ui.calculateSafeToSpendNowCents
import net.loeu.wallybudget.util.CurrencyFormatter
import kotlin.math.abs
import kotlin.math.max

private const val PERSONALIZATION_MIN_CYCLES = 3

internal object AnalysisSnapshotFactory {

    private const val MAX_BEHAVIOR_CYCLES = 6
    // Beta priors keep historical risk estimates usable with a small number of cycles.
    private const val OVERSPEND_RATE_PRIOR_SUCCESSES = 1.0
    private const val OVERSPEND_RATE_PRIOR_TOTAL = 4.0
    private const val LARGE_OVERSPEND_RATE_PRIOR_SUCCESSES = 0.5
    private const val LARGE_OVERSPEND_RATE_PRIOR_TOTAL = 3.0

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
        val behaviorHistory = monthlyHistory.take(MAX_BEHAVIOR_CYCLES)
        val behaviorProfile = buildHistoricalBehaviorProfile(
            history = behaviorHistory,
            budgetState = budgetState,
            spendingForecast = spendingForecast
        )

        val confidenceBand = confidenceBand(spendingForecast.confidenceScore)
        val forecastRiskPoints = adjustedForecastRiskPoints(
            budgetState = budgetState,
            spendingForecast = spendingForecast,
            confidenceBand = confidenceBand,
            behaviorProfile = behaviorProfile
        )
        val behaviorRiskPoints = behaviorRiskPoints(
            budgetState = budgetState,
            spendingForecast = spendingForecast,
            behaviorProfile = behaviorProfile
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
        val hardRisk = budgetState.remainingCycleCents <= 0L ||
            (safeToSpendNowCents == 0L && budgetState.remainingTodayCents < 0L) ||
            spendingForecast.estimatedEndCycleRemainingCents < 0L
        val totalRiskPoints = forecastRiskPoints + behaviorRiskPoints + paceRiskPoints + safeTodayRiskPoints
        val monitorAfterDays = monitorAfterDays(
            daysRemainingInCycle = budgetState.daysRemainingInCycle,
            confidenceBand = confidenceBand
        )

        val verdict = when {
            hardRisk || totalRiskPoints >= 5 -> AnalysisVerdictLevel.AtRisk
            totalRiskPoints >= 3 -> AnalysisVerdictLevel.Caution
            totalRiskPoints >= 1 -> AnalysisVerdictLevel.Watchful
            else -> AnalysisVerdictLevel.Stable
        }

        val recommendations = buildRecommendations(
            verdict = verdict,
            budgetState = budgetState,
            spendingForecast = spendingForecast,
            safeToSpendNowCents = safeToSpendNowCents,
            paceGapCents = paceGapCents,
            monitorAfterDays = monitorAfterDays,
            timelineLockReason = timelineLockReason
        )
        val historyFallbackText = historyFallbackText(
            showHistoryFallback = behaviorHistory.isEmpty(),
            timelineLockReason = timelineLockReason
        )

        return AnalysisSnapshot(
            verdictLevel = verdict,
            headline = headline(verdict),
            summary = summary(
                verdict = verdict,
                confidenceBand = confidenceBand,
                budgetState = budgetState,
                spendingForecast = spendingForecast,
                behaviorProfile = behaviorProfile,
                safeToSpendNowCents = safeToSpendNowCents,
                monitorAfterDays = monitorAfterDays
            ),
            evidence = buildEvidence(
                budgetState = budgetState,
                spendingForecast = spendingForecast,
                paceGapCents = paceGapCents,
                safeToSpendNowCents = safeToSpendNowCents,
                availableRecoverableOverspendCents = availableRecoverableOverspendCents,
                behaviorProfile = behaviorProfile
            ),
            recommendations = recommendations,
            confidenceLabel = confidenceBand.label,
            confidenceExplanation = confidenceExplanation(
                confidenceBand = confidenceBand,
                monitorAfterDays = monitorAfterDays
            ),
            rangeExplanation = rangeExplanation(
                budgetState = budgetState,
                spendingForecast = spendingForecast,
                behaviorProfile = behaviorProfile
            ),
            monitorAfterDays = monitorAfterDays,
            showHistoryFallback = behaviorHistory.isEmpty(),
            historyFallbackText = historyFallbackText
        )
    }

    private fun buildHistoricalBehaviorProfile(
        history: List<MonthlyHistory>,
        budgetState: BudgetState,
        spendingForecast: SpendingForecast
    ): HistoricalBehaviorProfile? {
        if (history.isEmpty()) return null

        val requiredExtraSpendToMissBudgetCents = if (
            spendingForecast.estimatedEndCycleRemainingCents > 0L &&
            spendingForecast.upperBoundCents > budgetState.monthlyBudgetCents
        ) {
            spendingForecast.estimatedEndCycleRemainingCents
        } else {
            0L
        }
        val historicalOverspendThresholdCents =
            (spendingForecast.upperBoundCents - budgetState.monthlyBudgetCents).coerceAtLeast(0L)
        val overspendAmounts = history.map { max(0L, -it.surplusCents) }
        val overspendCycles = overspendAmounts.count { it > 0L }
        val largeOverspendCycles = overspendAmounts.count {
            historicalOverspendThresholdCents > 0L && it >= historicalOverspendThresholdCents
        }

        return HistoricalBehaviorProfile(
            cycleCount = history.size,
            overspendCycles = overspendCycles,
            averageSurplusCents = history.sumOf { it.surplusCents } / history.size,
            maxOverspendCents = overspendAmounts.maxOrNull() ?: 0L,
            smoothedOverspendRate = (overspendCycles + OVERSPEND_RATE_PRIOR_SUCCESSES) /
                (history.size + OVERSPEND_RATE_PRIOR_TOTAL),
            smoothedLargeOverspendRate = (largeOverspendCycles + LARGE_OVERSPEND_RATE_PRIOR_SUCCESSES) /
                (history.size + LARGE_OVERSPEND_RATE_PRIOR_TOTAL),
            requiredExtraSpendToMissBudgetCents = requiredExtraSpendToMissBudgetCents,
            historicalOverspendThresholdCents = historicalOverspendThresholdCents,
            largeOverspendCycles = largeOverspendCycles
        )
    }

    private fun adjustedForecastRiskPoints(
        budgetState: BudgetState,
        spendingForecast: SpendingForecast,
        confidenceBand: ConfidenceBand,
        behaviorProfile: HistoricalBehaviorProfile?
    ): Int {
        val projectedBufferCents = spendingForecast.estimatedEndCycleRemainingCents.coerceAtLeast(0L)
        val upperRangeOverrunCents = (spendingForecast.upperBoundCents - budgetState.monthlyBudgetCents)
            .coerceAtLeast(0L)
        val baseRisk = when {
            spendingForecast.estimatedEndCycleRemainingCents < 0L -> 3
            upperRangeOverrunCents > 0L && behaviorProfile?.hasPersonalizedHistory == true -> when {
                behaviorProfile.smoothedLargeOverspendRate < 0.10 -> 0
                behaviorProfile.smoothedLargeOverspendRate < 0.25 -> 1
                else -> 2
            }
            upperRangeOverrunCents > 0L -> {
                if (projectedBufferCents <= max(budgetState.dailyBudgetCents, upperRangeOverrunCents)) {
                    2
                } else {
                    1
                }
            }
            else -> 0
        }

        return when {
            confidenceBand != ConfidenceBand.Low -> baseRisk
            baseRisk == 0 -> 0
            spendingForecast.estimatedEndCycleRemainingCents < 0L -> {
                (baseRisk - 1).coerceAtLeast(1)
            }
            else -> (baseRisk - 1).coerceAtLeast(0)
        }
    }

    private fun behaviorRiskPoints(
        budgetState: BudgetState,
        spendingForecast: SpendingForecast,
        behaviorProfile: HistoricalBehaviorProfile?
    ): Int {
        if (behaviorProfile?.hasPersonalizedHistory != true) return 0
        if (
            spendingForecast.estimatedEndCycleRemainingCents <= 0L ||
            spendingForecast.upperBoundCents <= budgetState.monthlyBudgetCents ||
            behaviorProfile.requiredExtraSpendToMissBudgetCents <= 0L
        ) {
            return 0
        }

        return if (behaviorProfile.smoothedLargeOverspendRate >= 0.25) 1 else 0
    }

    private fun buildEvidence(
        budgetState: BudgetState,
        spendingForecast: SpendingForecast,
        paceGapCents: Long,
        safeToSpendNowCents: Long,
        availableRecoverableOverspendCents: Long,
        behaviorProfile: HistoricalBehaviorProfile?
    ): List<AnalysisEvidenceItem> {
        val items = mutableListOf<AnalysisEvidenceItem>()

        items += forecastEvidence(
            budgetState = budgetState,
            spendingForecast = spendingForecast,
            behaviorProfile = behaviorProfile
        )
        items += paceEvidence(budgetState, spendingForecast, paceGapCents)
        items += safeTodayEvidence(safeToSpendNowCents, availableRecoverableOverspendCents)

        behaviorEvidence(behaviorProfile)?.let { items += it }

        return items
    }

    private fun forecastEvidence(
        budgetState: BudgetState,
        spendingForecast: SpendingForecast,
        behaviorProfile: HistoricalBehaviorProfile?
    ): AnalysisEvidenceItem {
        val projectedBufferCents = spendingForecast.estimatedEndCycleRemainingCents.coerceAtLeast(0L)
        val upperRangeOverrunCents = (spendingForecast.upperBoundCents - budgetState.monthlyBudgetCents)
            .coerceAtLeast(0L)

        return when {
            spendingForecast.estimatedEndCycleRemainingCents < 0L -> AnalysisEvidenceItem(
                title = "Forecast pressure",
                value = "Over by ${CurrencyFormatter.format(abs(spendingForecast.estimatedEndCycleRemainingCents))}",
                detail = "Current projection ends above your ${CurrencyFormatter.format(budgetState.monthlyBudgetCents)} cycle budget.",
                tone = AnalysisEvidenceTone.Critical
            )
            upperRangeOverrunCents > 0L -> {
                val value = when {
                    projectedBufferCents == 0L -> "Right at budget"
                    behaviorProfile?.hasPersonalizedHistory == true &&
                        behaviorProfile.requiredExtraSpendToMissBudgetCents > 0L -> {
                        "Needs ${CurrencyFormatter.format(behaviorProfile.requiredExtraSpendToMissBudgetCents)} more to miss"
                    }
                    else -> "Best estimate still under"
                }
                val detail = when {
                    projectedBufferCents == 0L -> {
                        "Current projection lands on budget, while the high side reaches ${CurrencyFormatter.format(spendingForecast.upperBoundCents)}."
                    }
                    behaviorProfile?.hasPersonalizedHistory == true &&
                        behaviorProfile.requiredExtraSpendToMissBudgetCents > 0L -> {
                        "Current projection still leaves ${CurrencyFormatter.format(projectedBufferCents)}. Budget is only missed if spending finishes about ${CurrencyFormatter.format(behaviorProfile.requiredExtraSpendToMissBudgetCents)} above the current path."
                    }
                    else -> {
                        "Current projection still leaves ${CurrencyFormatter.format(projectedBufferCents)}, while the high side reaches ${CurrencyFormatter.format(spendingForecast.upperBoundCents)}."
                    }
                }
                AnalysisEvidenceItem(
                    title = "Forecast range",
                    value = value,
                    detail = detail,
                    tone = AnalysisEvidenceTone.Neutral
                )
            }
            else -> AnalysisEvidenceItem(
                title = "Forecast pressure",
                value = "Within budget",
                detail = buildString {
                    append("Projected finish leaves ")
                    append(
                        CurrencyFormatter.format(
                            spendingForecast.estimatedEndCycleRemainingCents.coerceAtLeast(0L)
                        )
                    )
                    append(" in the cycle.")
                },
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
                detail = buildString {
                    append("Projected pace is ")
                    append(CurrencyFormatter.format(spendingForecast.projectedDailySpendCents))
                    append(" vs ")
                    append(CurrencyFormatter.format(budgetState.dailyBudgetCents))
                    append(" daily budget.")
                },
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
                detail = buildString {
                    append("Projected pace is ")
                    append(CurrencyFormatter.format(spendingForecast.projectedDailySpendCents))
                    append(" against ")
                    append(CurrencyFormatter.format(budgetState.dailyBudgetCents))
                    append(" per day.")
                },
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

    private fun behaviorEvidence(
        behaviorProfile: HistoricalBehaviorProfile?
    ): AnalysisEvidenceItem? {
        behaviorProfile ?: return null

        if (!behaviorProfile.hasPersonalizedHistory) {
            return AnalysisEvidenceItem(
                title = "Overspend behavior",
                value = "History still building",
                detail = buildString {
                    append("Only ${behaviorProfile.cycleCount} completed cycle")
                    append(if (behaviorProfile.cycleCount == 1) "" else "s")
                    append(" available. Guidance will personalize after 3 completed cycles.")
                },
                tone = AnalysisEvidenceTone.Neutral
            )
        }

        if (behaviorProfile.requiredExtraSpendToMissBudgetCents > 0L) {
            return when {
                behaviorProfile.largeOverspendCycles == 0 -> AnalysisEvidenceItem(
                    title = "Overspend behavior",
                    value = "Miss of this size is rare",
                    detail = buildString {
                        append("0 of last ${behaviorProfile.cycleCount} cycles finished at least ")
                        append(
                            CurrencyFormatter.format(
                                behaviorProfile.historicalOverspendThresholdCents
                            )
                        )
                        append(" over budget. Worst miss was ")
                        append(CurrencyFormatter.format(behaviorProfile.maxOverspendCents))
                        append('.')
                    },
                    tone = AnalysisEvidenceTone.Positive
                )
                behaviorProfile.largeOverspendCycles == 1 -> AnalysisEvidenceItem(
                    title = "Overspend behavior",
                    value = "Some precedent",
                    detail = "1 of last ${behaviorProfile.cycleCount} cycles finished at least this far over budget.",
                    tone = AnalysisEvidenceTone.Neutral
                )
                else -> AnalysisEvidenceItem(
                    title = "Overspend behavior",
                    value = "History supports this risk",
                    detail = "${behaviorProfile.largeOverspendCycles} of last ${behaviorProfile.cycleCount} cycles finished at least this far over budget.",
                    tone = AnalysisEvidenceTone.Warning
                )
            }
        }

        val averageText = if (behaviorProfile.averageSurplusCents >= 0L) {
            "Average recent finish is ${CurrencyFormatter.format(behaviorProfile.averageSurplusCents)} under budget."
        } else {
            "Average recent finish is ${CurrencyFormatter.format(abs(behaviorProfile.averageSurplusCents))} over budget."
        }

        return when {
            behaviorProfile.smoothedOverspendRate < 0.25 && behaviorProfile.averageSurplusCents >= 0L -> AnalysisEvidenceItem(
                title = "Overspend behavior",
                value = "Mostly under budget",
                detail = averageText,
                tone = AnalysisEvidenceTone.Positive
            )
            behaviorProfile.smoothedOverspendRate >= 0.45 -> AnalysisEvidenceItem(
                title = "Overspend behavior",
                value = "Frequently over budget",
                detail = averageText,
                tone = AnalysisEvidenceTone.Warning
            )
            else -> AnalysisEvidenceItem(
                title = "Overspend behavior",
                value = "Mixed",
                detail = averageText,
                tone = AnalysisEvidenceTone.Neutral
            )
        }
    }

    private fun buildRecommendations(
        verdict: AnalysisVerdictLevel,
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

        if (spendingForecast.estimatedEndCycleRemainingCents < 0L) {
            val timingSuffix = monitorAfterDays?.let { " Re-check in $it day${if (it == 1) "" else "s"}." } ?: ""
            recommendations += AnalysisRecommendation(
                text = "Treat the forecast as a real warning and tighten spending now.$timingSuffix"
            )
        } else if (spendingForecast.upperBoundCents > budgetState.monthlyBudgetCents) {
            val timingSuffix = monitorAfterDays?.let { " Re-check in $it day${if (it == 1) "" else "s"}." } ?: ""
            when (verdict) {
                AnalysisVerdictLevel.Caution -> recommendations += AnalysisRecommendation(
                    text = "Treat the upper range as a warning signal while you tighten spending.$timingSuffix"
                )
                AnalysisVerdictLevel.Watchful -> recommendations += AnalysisRecommendation(
                    text = "Hold close to the current pace while more cycle data comes in.$timingSuffix"
                )
                else -> Unit
            }
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

}

internal data class HistoricalBehaviorProfile(
    val cycleCount: Int,
    val overspendCycles: Int,
    val averageSurplusCents: Long,
    val maxOverspendCents: Long,
    val smoothedOverspendRate: Double,
    val smoothedLargeOverspendRate: Double,
    val requiredExtraSpendToMissBudgetCents: Long,
    val historicalOverspendThresholdCents: Long,
    val largeOverspendCycles: Int
) {
    val hasPersonalizedHistory: Boolean
        get() = cycleCount >= PERSONALIZATION_MIN_CYCLES
}
