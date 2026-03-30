package net.loeu.wallybudget.ui.screens.analysis

import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.domain.model.SpendingForecast
import net.loeu.wallybudget.util.CurrencyFormatter
import kotlin.math.abs

internal fun buildForecastEvidence(
    budgetState: BudgetState,
    spendingForecast: SpendingForecast,
    behaviorProfile: HistoricalBehaviorProfile?
): AnalysisEvidenceItem {
    val projectedBufferCents = spendingForecast.estimatedEndCycleRemainingCents.coerceAtLeast(0L)
    val upperRangeOverrunCents =
        (spendingForecast.upperBoundCents - budgetState.monthlyBudgetCents).coerceAtLeast(0L)

    return when {
        spendingForecast.estimatedEndCycleRemainingCents < 0L -> AnalysisEvidenceItem(
            title = "Forecast pressure",
            value = "Over by ${CurrencyFormatter.format(abs(spendingForecast.estimatedEndCycleRemainingCents))}",
            detail =
                "Current projection ends above your " +
                    "${CurrencyFormatter.format(budgetState.monthlyBudgetCents)} cycle budget.",
            tone = AnalysisEvidenceTone.Critical,
            gauge = forecastGauge(budgetState, spendingForecast)
        )

        upperRangeOverrunCents > 0L -> AnalysisEvidenceItem(
            title = "Forecast range",
            value = buildForecastRangeValue(projectedBufferCents, behaviorProfile),
            detail = buildForecastRangeDetail(
                projectedBufferCents = projectedBufferCents,
                upperBoundCents = spendingForecast.upperBoundCents,
                behaviorProfile = behaviorProfile
            ),
            tone = AnalysisEvidenceTone.Neutral,
            gauge = forecastGauge(budgetState, spendingForecast)
        )

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
            tone = AnalysisEvidenceTone.Positive,
            gauge = forecastGauge(budgetState, spendingForecast)
        )
    }
}

private fun forecastGauge(
    budgetState: BudgetState,
    spendingForecast: SpendingForecast
): EvidenceGauge {
    val maxCents = maxOf(
        spendingForecast.upperBoundCents,
        spendingForecast.projectedTotalSpentCents,
        budgetState.monthlyBudgetCents
    ) * 11 / 10
    return EvidenceGauge(
        valueCents = spendingForecast.projectedTotalSpentCents,
        targetCents = budgetState.monthlyBudgetCents,
        maxCents = maxCents,
        lowerCents = spendingForecast.lowerBoundCents,
        upperCents = spendingForecast.upperBoundCents
    )
}

private fun buildForecastRangeValue(
    projectedBufferCents: Long,
    behaviorProfile: HistoricalBehaviorProfile?
): String {
    return when {
        projectedBufferCents == 0L -> "Right at budget"
        behaviorProfile?.hasPersonalizedHistory == true &&
            behaviorProfile.requiredExtraSpendToMissBudgetCents > 0L -> {
            "Needs " +
                CurrencyFormatter.format(
                    behaviorProfile.requiredExtraSpendToMissBudgetCents
                ) +
                " more to miss"
        }

        else -> "Best estimate still under"
    }
}

private fun buildForecastRangeDetail(
    projectedBufferCents: Long,
    upperBoundCents: Long,
    behaviorProfile: HistoricalBehaviorProfile?
): String {
    return when {
        projectedBufferCents == 0L -> {
            "Current projection lands on budget, while the high side " +
                "reaches ${CurrencyFormatter.format(upperBoundCents)}."
        }

        behaviorProfile?.hasPersonalizedHistory == true &&
            behaviorProfile.requiredExtraSpendToMissBudgetCents > 0L -> {
            buildString {
                append("Current projection still leaves ")
                append(CurrencyFormatter.format(projectedBufferCents))
                append(". Budget is only missed if spending finishes about ")
                append(
                    CurrencyFormatter.format(
                        behaviorProfile.requiredExtraSpendToMissBudgetCents
                    )
                )
                append(" above the current path.")
            }
        }

        else -> {
            "Current projection still leaves " +
                "${CurrencyFormatter.format(projectedBufferCents)}, while " +
                "the high side reaches ${CurrencyFormatter.format(upperBoundCents)}."
        }
    }
}
