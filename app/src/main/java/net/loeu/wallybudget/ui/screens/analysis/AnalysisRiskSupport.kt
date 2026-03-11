package net.loeu.wallybudget.ui.screens.analysis

import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.domain.model.SpendingForecast

internal fun resolveAnalysisVerdict(
    budgetState: BudgetState,
    spendingForecast: SpendingForecast,
    safeToSpendNowCents: Long,
    totalRiskPoints: Int
): AnalysisVerdictLevel {
    val hasHardRisk = budgetState.remainingCycleCents <= 0L ||
        (safeToSpendNowCents == 0L && budgetState.remainingTodayCents < 0L) ||
        spendingForecast.estimatedEndCycleRemainingCents < 0L

    return when {
        hasHardRisk || totalRiskPoints >= 5 -> AnalysisVerdictLevel.AtRisk
        totalRiskPoints >= 3 -> AnalysisVerdictLevel.Caution
        totalRiskPoints >= 1 -> AnalysisVerdictLevel.Watchful
        else -> AnalysisVerdictLevel.Stable
    }
}
