package net.loeu.wallybudget.data.model

data class SpendingForecast(
    val estimatedEndCycleRemainingCents: Long,
    val projectedTotalSpentCents: Long,
    val projectedDailySpendCents: Long,
    val historicalAdjustmentPercent: Int,
    val historyCyclesUsed: Int
) {
    val isProjectedOverBudget: Boolean
        get() = estimatedEndCycleRemainingCents < 0L
}
