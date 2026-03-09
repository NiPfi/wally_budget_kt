package net.loeu.wallybudget.ui.screens.analysis

import net.loeu.wallybudget.data.model.BudgetState
import net.loeu.wallybudget.data.model.MonthlyHistory
import net.loeu.wallybudget.data.model.SpendingForecast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AnalysisSnapshotFactoryTest {

    @Test
    fun projectedOverBudget_highConfidence_isAtRisk() {
        val snapshot = AnalysisSnapshotFactory.create(
            budgetState = testBudgetState(),
            spendingForecast = testForecast(
                estimatedEndCycleRemainingCents = -12_000L,
                upperBoundCents = 118_000L,
                projectedDailySpendCents = 4_500L,
                confidenceScore = 0.82
            ),
            monthlyHistory = listOf(
                history(surplusCents = -6_000L, offsetDays = 1),
                history(surplusCents = -3_000L, offsetDays = 35),
                history(surplusCents = 8_000L, offsetDays = 70)
            ),
            timelineLockReason = null
        )

        assertEquals(AnalysisVerdictLevel.AtRisk, snapshot.verdictLevel)
    }

    @Test
    fun projectedOverBudget_lowConfidence_withoutHardRisk_isCaution() {
        val snapshot = AnalysisSnapshotFactory.create(
            budgetState = testBudgetState(),
            spendingForecast = testForecast(
                estimatedEndCycleRemainingCents = -8_000L,
                upperBoundCents = 110_000L,
                projectedDailySpendCents = 4_200L,
                confidenceScore = 0.40
            ),
            monthlyHistory = emptyList(),
            timelineLockReason = null
        )

        assertEquals(AnalysisVerdictLevel.Caution, snapshot.verdictLevel)
    }

    @Test
    fun underBudget_lowSafeTodayHeadroom_isCaution() {
        val snapshot = AnalysisSnapshotFactory.create(
            budgetState = testBudgetState(remainingTodayCents = 300L),
            spendingForecast = testForecast(
                estimatedEndCycleRemainingCents = 7_000L,
                upperBoundCents = 96_000L,
                projectedDailySpendCents = 4_000L,
                confidenceScore = 0.64,
                recoverableOverspendCents = 0L
            ),
            monthlyHistory = listOf(history(surplusCents = -2_000L)),
            timelineLockReason = null
        )

        assertEquals(AnalysisVerdictLevel.Caution, snapshot.verdictLevel)
    }

    @Test
    fun noHistory_omitsHistoryTile_andShowsFallback() {
        val snapshot = AnalysisSnapshotFactory.create(
            budgetState = testBudgetState(),
            spendingForecast = testForecast(
                estimatedEndCycleRemainingCents = 4_000L,
                upperBoundCents = 99_000L,
                projectedDailySpendCents = 3_100L,
                confidenceScore = 0.60
            ),
            monthlyHistory = emptyList(),
            timelineLockReason = null
        )

        assertEquals(3, snapshot.evidence.size)
        assertTrue(snapshot.showHistoryFallback)
    }

    @Test
    fun alreadyOverCycleBudget_isAtRisk_regardlessOfConfidence() {
        val snapshot = AnalysisSnapshotFactory.create(
            budgetState = testBudgetState(
                totalSpentThisCycleCents = 103_000L,
                remainingTodayCents = -800L
            ),
            spendingForecast = testForecast(
                estimatedEndCycleRemainingCents = -3_000L,
                upperBoundCents = 106_000L,
                projectedDailySpendCents = 4_000L,
                confidenceScore = 0.32,
                recoverableOverspendCents = 0L
            ),
            monthlyHistory = emptyList(),
            timelineLockReason = null
        )

        assertEquals(AnalysisVerdictLevel.AtRisk, snapshot.verdictLevel)
    }

    @Test
    fun recentHistory_twoOfLastThreeOverBudget_elevatesHistorySignal() {
        val snapshot = AnalysisSnapshotFactory.create(
            budgetState = testBudgetState(),
            spendingForecast = testForecast(
                estimatedEndCycleRemainingCents = 6_000L,
                upperBoundCents = 97_000L,
                projectedDailySpendCents = 3_000L,
                confidenceScore = 0.71
            ),
            monthlyHistory = listOf(
                history(surplusCents = -7_000L, offsetDays = 1),
                history(surplusCents = -2_500L, offsetDays = 35),
                history(surplusCents = 4_000L, offsetDays = 70)
            ),
            timelineLockReason = null
        )

        val historyEvidence = snapshot.evidence.last()
        assertEquals("Historical tendency", historyEvidence.title)
        assertTrue(historyEvidence.value.contains("2 of 3 over"))
        assertFalse(snapshot.showHistoryFallback)
    }

    private fun testBudgetState(
        totalSpentThisCycleCents: Long = 80_000L,
        remainingTodayCents: Long = 2_000L
    ) = BudgetState(
        monthlyBudgetCents = 100_000L,
        totalSpentThisCycleCents = totalSpentThisCycleCents,
        dailyBudgetCents = 3_000L,
        spentTodayCents = (3_000L - remainingTodayCents).coerceAtLeast(0L),
        remainingTodayCents = remainingTodayCents,
        daysRemainingInCycle = 10,
        cumulativeSavingsCents = 12_000L,
        paydayDate = 1,
        cycleStartDate = LocalDate.of(2026, 3, 1)
    )

    private fun testForecast(
        estimatedEndCycleRemainingCents: Long,
        upperBoundCents: Long,
        projectedDailySpendCents: Long,
        confidenceScore: Double,
        recoverableOverspendCents: Long = 1_500L
    ) = SpendingForecast(
        estimatedEndCycleRemainingCents = estimatedEndCycleRemainingCents,
        projectedTotalSpentCents = 100_000L - estimatedEndCycleRemainingCents,
        projectedDailySpendCents = projectedDailySpendCents,
        confidenceScore = confidenceScore,
        lowerBoundCents = 90_000L,
        upperBoundCents = upperBoundCents,
        dailyAverageWeightedCents = projectedDailySpendCents,
        recoverableOverspendCents = recoverableOverspendCents,
        grossRecoverableOverspendCents = recoverableOverspendCents
    )

    private fun history(
        surplusCents: Long,
        offsetDays: Long = 0
    ) = MonthlyHistory(
        cycleStartDate = LocalDate.of(2026, 2, 1).minusDays(offsetDays).toString(),
        budgetAmountCents = 100_000L,
        totalSpentCents = 100_000L - surplusCents,
        surplusCents = surplusCents,
        cycleEndDate = LocalDate.of(2026, 3, 1).minusDays(offsetDays).toString(),
        endTimestamp = LocalDate.of(2026, 3, 1).minusDays(offsetDays).toEpochDay()
    )
}
