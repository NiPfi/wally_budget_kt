package net.loeu.wallybudget.ui.screens.analysis

import net.loeu.wallybudget.domain.model.MonthlyHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpendingHistoryChartLayoutTest {

    @Test
    fun computeSpendingHistoryChartLayout_preservesZeroBudgetAsEmptyBar() {
        val layout = computeSpendingHistoryChartLayout(
            history = listOf(
                history(
                    cycleStartDate = "2026-01-01",
                    budgetAmountCents = 0L,
                    totalSpentCents = 20_000L,
                    surplusCents = -20_000L
                )
            ),
            chartWidth = 300f,
            chartHeight = 100f,
            gapPx = 7f
        )

        assertEquals(1, layout.bars.size)
        assertEquals(0f, layout.bars.single().fillHeight, 0.001f)
        assertEquals(16.666666f, layout.budgetLineY, 0.001f)
    }

    @Test
    fun computeSpendingHistoryChartLayout_appliesToneAndSpacing() {
        val layout = computeSpendingHistoryChartLayout(
            history = listOf(
                history("2026-01-01", 100_000L, 90_000L, 10_000L),
                history("2026-02-01", 100_000L, 103_000L, -3_000L),
                history("2026-03-01", 100_000L, 120_000L, -20_000L)
            ),
            chartWidth = 314f,
            chartHeight = 120f,
            gapPx = 7f
        )

        assertEquals(100f, layout.bars[0].width, 0.001f)
        assertEquals(107f, layout.bars[1].left, 0.001f)
        assertEquals(214f, layout.bars[2].left, 0.001f)
        assertEquals(SpendingHistoryBarTone.UnderBudget, layout.bars[0].tone)
        assertEquals(SpendingHistoryBarTone.NearBudget, layout.bars[1].tone)
        assertEquals(SpendingHistoryBarTone.OverBudget, layout.bars[2].tone)
        assertTrue(layout.bars[2].fillHeight > layout.bars[1].fillHeight)
    }

    @Test
    fun spendingHistoryMonthLabel_returnsShortMonthName() {
        assertEquals("Jan", spendingHistoryMonthLabel("2026-01-01"))
        assertEquals("Dec", spendingHistoryMonthLabel("2026-12-15"))
        assertEquals("", spendingHistoryMonthLabel("bad-date"))
    }

    private fun history(
        cycleStartDate: String,
        budgetAmountCents: Long,
        totalSpentCents: Long,
        surplusCents: Long
    ) = MonthlyHistory(
        cycleStartDate = cycleStartDate,
        budgetAmountCents = budgetAmountCents,
        totalSpentCents = totalSpentCents,
        surplusCents = surplusCents,
        cycleEndDate = "2026-02-01",
        endTimestamp = 1L
    )
}
