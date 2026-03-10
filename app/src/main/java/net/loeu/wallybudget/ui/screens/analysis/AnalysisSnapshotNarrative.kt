package net.loeu.wallybudget.ui.screens.analysis

import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.domain.model.SpendingForecast
import net.loeu.wallybudget.util.CurrencyFormatter

internal fun historyFallbackText(
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

internal fun headline(verdict: AnalysisVerdictLevel): String = when (verdict) {
    AnalysisVerdictLevel.Stable -> "On track"
    AnalysisVerdictLevel.Watchful -> "Watch the upper range"
    AnalysisVerdictLevel.Caution -> "Needs attention"
    AnalysisVerdictLevel.AtRisk -> "Risk of overspending"
}

internal fun summary(
    verdict: AnalysisVerdictLevel,
    confidenceBand: ConfidenceBand,
    budgetState: BudgetState,
    spendingForecast: SpendingForecast,
    behaviorProfile: HistoricalBehaviorProfile?,
    safeToSpendNowCents: Long,
    monitorAfterDays: Int?
): String {
    if (budgetState.remainingCycleCents <= 0L) {
        return "You have already exhausted this cycle's budget, so further spend increases the deficit."
    }

    val hasRangeOnlyRisk = spendingForecast.estimatedEndCycleRemainingCents > 0L &&
        spendingForecast.upperBoundCents > budgetState.monthlyBudgetCents

    return when (verdict) {
        AnalysisVerdictLevel.Stable -> stableSummary(hasRangeOnlyRisk, behaviorProfile)
        AnalysisVerdictLevel.Watchful -> watchfulSummary(hasRangeOnlyRisk)
        AnalysisVerdictLevel.Caution -> cautionSummary(
            hasRangeOnlyRisk = hasRangeOnlyRisk,
            behaviorProfile = behaviorProfile,
            confidenceBand = confidenceBand,
            safeToSpendNowCents = safeToSpendNowCents,
            monitorAfterDays = monitorAfterDays
        )
        AnalysisVerdictLevel.AtRisk -> atRiskSummary(confidenceBand, monitorAfterDays)
    }
}

private fun stableSummary(
    hasRangeOnlyRisk: Boolean,
    behaviorProfile: HistoricalBehaviorProfile?
): String {
    return if (
        hasRangeOnlyRisk &&
        behaviorProfile?.hasPersonalizedHistory == true &&
        behaviorProfile.largeOverspendCycles == 0
    ) {
        "Your current plan still finishes under budget, and a miss would require a larger overspend than your recent cycles usually show."
    } else {
        "Current pace and safe-today headroom still support an on-budget finish."
    }
}

private fun watchfulSummary(hasRangeOnlyRisk: Boolean): String {
    return if (hasRangeOnlyRisk) {
        "You are still on track, but the upper range is worth watching if spending speeds up."
    } else {
        "The budget still has room, but one signal is leaning tighter than the rest."
    }
}

private fun cautionSummary(
    hasRangeOnlyRisk: Boolean,
    behaviorProfile: HistoricalBehaviorProfile?,
    confidenceBand: ConfidenceBand,
    safeToSpendNowCents: Long,
    monitorAfterDays: Int?
): String {
    return when {
        hasRangeOnlyRisk &&
            behaviorProfile?.hasPersonalizedHistory == true &&
            behaviorProfile.smoothedLargeOverspendRate >= 0.25 -> {
            "You are still under budget, but history shows misses of this size can happen if spending speeds up."
        }
        confidenceBand == ConfidenceBand.Low -> {
            val days = monitorAfterDays ?: 2
            "The forecast is still building, but current pace and headroom need a closer watch. Check back in $days day${if (days == 1) "" else "s"}."
        }
        safeToSpendNowCents == 0L -> {
            "You can still recover, but today's cushion is gone and the rest of the cycle is tighter."
        }
        else -> "You can still recover, but the current pace is running tighter than your budget allows."
    }
}

private fun atRiskSummary(
    confidenceBand: ConfidenceBand,
    monitorAfterDays: Int?
): String {
    return if (confidenceBand == ConfidenceBand.High) {
        "Your current pace is likely to finish over budget unless you tighten spending now."
    } else {
        val days = monitorAfterDays ?: 2
        "Signals are pointing high, but the forecast is still building. Keep spending tight and re-check in $days day${if (days == 1) "" else "s"}."
    }
}

internal fun confidenceExplanation(
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

internal fun rangeExplanation(
    budgetState: BudgetState,
    spendingForecast: SpendingForecast,
    behaviorProfile: HistoricalBehaviorProfile?
): String {
    val base = buildString {
        append("Forecast range runs from ")
        append(CurrencyFormatter.format(spendingForecast.lowerBoundCents))
        append(" to ")
        append(CurrencyFormatter.format(spendingForecast.upperBoundCents))
        append(" total spend by cycle end.")
    }
    return when {
        spendingForecast.upperBoundCents <= budgetState.monthlyBudgetCents -> base
        behaviorProfile?.hasPersonalizedHistory == true &&
            behaviorProfile.requiredExtraSpendToMissBudgetCents > 0L -> {
            val precedentText = when (behaviorProfile.largeOverspendCycles) {
                0 -> "has not happened in ${behaviorProfile.cycleCount} recent completed cycles."
                1 -> "has happened in 1 of ${behaviorProfile.cycleCount} recent completed cycles."
                else -> {
                    "has happened in ${behaviorProfile.largeOverspendCycles} of " +
                        "${behaviorProfile.cycleCount} recent completed cycles."
                }
            }
            buildString {
                append(base)
                append(" Missing budget would require about ")
                append(
                    CurrencyFormatter.format(
                        behaviorProfile.requiredExtraSpendToMissBudgetCents
                    )
                )
                append(" more spending than the current projection, which ")
                append(precedentText)
            }
        }
        else -> {
            val projectedBufferCents =
                spendingForecast.estimatedEndCycleRemainingCents.coerceAtLeast(0L)
            buildString {
                append(base)
                append(" Best estimate still leaves ")
                append(CurrencyFormatter.format(projectedBufferCents))
                append(", and the budget is only crossed if spending finishes about ")
                append("that much above the current path.")
            }
        }
    }
}

internal fun monitorAfterDays(
    daysRemainingInCycle: Int,
    confidenceBand: ConfidenceBand
): Int? = when {
    daysRemainingInCycle <= 3 -> 1
    confidenceBand == ConfidenceBand.Low -> 2
    confidenceBand == ConfidenceBand.Medium -> 3
    else -> null
}

internal fun confidenceBand(confidenceScore: Double): ConfidenceBand = when {
    confidenceScore >= 0.70 -> ConfidenceBand.High
    confidenceScore >= 0.55 -> ConfidenceBand.Medium
    else -> ConfidenceBand.Low
}

internal enum class ConfidenceBand(val label: String) {
    High("High"),
    Medium("Medium"),
    Low("Low")
}
