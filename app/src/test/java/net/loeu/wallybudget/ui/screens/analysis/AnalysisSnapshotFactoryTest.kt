package net.loeu.wallybudget.ui.screens.analysis

import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.domain.model.MonthlyHistory
import net.loeu.wallybudget.domain.model.SpendingForecast
import net.loeu.wallybudget.util.CurrencyFormatter
import org.junit.Assert.assertEquals
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
            monthlyHistory = histories(8_000L, 4_000L, -1_500L),
            timelineLockReason = null
        )

        assertEquals(AnalysisVerdictLevel.AtRisk, snapshot.verdictLevel)
    }

    @Test
    fun supportiveHistory_rangeOnlyRisk_isStable() {
        val snapshot = AnalysisSnapshotFactory.create(
            budgetState = testBudgetState(),
            spendingForecast = testForecast(
                estimatedEndCycleRemainingCents = 13_180L,
                upperBoundCents = 105_249L,
                projectedDailySpendCents = 2_856L,
                confidenceScore = 0.59,
                recoverableOverspendCents = 3_473L
            ),
            monthlyHistory = histories(2_100L, 90_400L, 42_500L, 0L, 66_200L, -1_500L),
            timelineLockReason = null
        )

        assertEquals(AnalysisVerdictLevel.Stable, snapshot.verdictLevel)
        assertEquals("On track", snapshot.headline)
        assertTrue(snapshot.summary.contains("larger overspend"))
        assertEquals("Overspend behavior", snapshot.evidence.last().title)
        assertEquals("Miss of this size is rare", snapshot.evidence.last().value)
        assertTrue(snapshot.evidence.last().detail.contains("0 of last 6 cycles"))
    }

    @Test
    fun mixedHistory_rangeOnlyRisk_isWatchful() {
        val snapshot = AnalysisSnapshotFactory.create(
            budgetState = testBudgetState(),
            spendingForecast = testForecast(
                estimatedEndCycleRemainingCents = 13_180L,
                upperBoundCents = 105_249L,
                projectedDailySpendCents = 2_856L,
                confidenceScore = 0.72,
                recoverableOverspendCents = 3_473L
            ),
            monthlyHistory = histories(2_100L, 90_400L, 42_500L, -16_000L, 66_200L, -1_500L),
            timelineLockReason = null
        )

        assertEquals(AnalysisVerdictLevel.Watchful, snapshot.verdictLevel)
        assertEquals("Watch the upper range", snapshot.headline)
        assertTrue(snapshot.summary.contains("worth watching"))
        assertEquals("Some precedent", snapshot.evidence.last().value)
    }

    @Test
    fun repeatedLargeOverspends_rangeOnlyRisk_isCaution() {
        val snapshot = AnalysisSnapshotFactory.create(
            budgetState = testBudgetState(),
            spendingForecast = testForecast(
                estimatedEndCycleRemainingCents = 13_180L,
                upperBoundCents = 105_249L,
                projectedDailySpendCents = 2_856L,
                confidenceScore = 0.72,
                recoverableOverspendCents = 3_473L
            ),
            monthlyHistory = histories(2_100L, -18_000L, 42_500L, -16_000L, 66_200L, -1_500L),
            timelineLockReason = null
        )

        assertEquals(AnalysisVerdictLevel.Caution, snapshot.verdictLevel)
        assertEquals("History supports this risk", snapshot.evidence.last().value)
    }

    @Test
    fun underBudget_lowSafeTodayHeadroom_isCaution_evenWithSupportiveHistory() {
        val snapshot = AnalysisSnapshotFactory.create(
            budgetState = testBudgetState(remainingTodayCents = 300L),
            spendingForecast = testForecast(
                estimatedEndCycleRemainingCents = 7_000L,
                upperBoundCents = 96_000L,
                projectedDailySpendCents = 4_000L,
                confidenceScore = 0.64,
                recoverableOverspendCents = 0L
            ),
            monthlyHistory = histories(2_100L, 90_400L, 42_500L, 0L, 66_200L, -1_500L),
            timelineLockReason = null
        )

        assertEquals(AnalysisVerdictLevel.Caution, snapshot.verdictLevel)
    }

    @Test
    fun supportiveHistory_withFastCurrentPace_isWatchful() {
        val snapshot = AnalysisSnapshotFactory.create(
            budgetState = testBudgetState(),
            spendingForecast = testForecast(
                estimatedEndCycleRemainingCents = 9_000L,
                upperBoundCents = 103_000L,
                projectedDailySpendCents = 3_200L,
                confidenceScore = 0.72
            ),
            monthlyHistory = histories(2_100L, 90_400L, 42_500L, 0L, 66_200L, -1_500L),
            timelineLockReason = null
        )

        assertEquals(AnalysisVerdictLevel.Watchful, snapshot.verdictLevel)
    }

    @Test
    fun insufficientHistory_keepsFallbackRangeOnlyBehavior() {
        val snapshot = AnalysisSnapshotFactory.create(
            budgetState = testBudgetState(),
            spendingForecast = testForecast(
                estimatedEndCycleRemainingCents = 13_180L,
                upperBoundCents = 105_249L,
                projectedDailySpendCents = 2_856L,
                confidenceScore = 0.72
            ),
            monthlyHistory = histories(4_000L, -1_500L),
            timelineLockReason = null
        )

        assertEquals(AnalysisVerdictLevel.Watchful, snapshot.verdictLevel)
        assertEquals("History still building", snapshot.evidence.last().value)
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
    fun rangeExplanation_mentionsRequiredExtraSpend_andHistoricalPrecedent() {
        val snapshot = AnalysisSnapshotFactory.create(
            budgetState = testBudgetState(),
            spendingForecast = testForecast(
                estimatedEndCycleRemainingCents = 13_180L,
                upperBoundCents = 105_249L,
                projectedDailySpendCents = 2_856L,
                confidenceScore = 0.59,
                recoverableOverspendCents = 3_473L
            ),
            monthlyHistory = histories(2_100L, 90_400L, 42_500L, 0L, 66_200L, -1_500L),
            timelineLockReason = null
        )

        assertTrue(snapshot.rangeExplanation.contains(CurrencyFormatter.format(13_180L)))
        assertTrue(snapshot.rangeExplanation.contains("has not happened in 6 recent completed cycles"))
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

    private fun histories(vararg surpluses: Long): List<MonthlyHistory> {
        return surpluses.mapIndexed { index, surplusCents ->
            val cycleEnd = LocalDate.of(2026, 3, 1).minusDays(index.toLong() * 31L)
            MonthlyHistory(
                cycleStartDate = cycleEnd.minusDays(29).toString(),
                budgetAmountCents = 100_000L,
                totalSpentCents = 100_000L - surplusCents,
                surplusCents = surplusCents,
                cycleEndDate = cycleEnd.toString(),
                endTimestamp = cycleEnd.toEpochDay()
            )
        }
    }
}
