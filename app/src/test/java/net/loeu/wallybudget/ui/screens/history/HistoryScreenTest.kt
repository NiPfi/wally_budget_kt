package net.loeu.wallybudget.ui.screens.history

import net.loeu.wallybudget.domain.model.CycleBucketSummary
import net.loeu.wallybudget.domain.model.ExpenseCycleSection
import net.loeu.wallybudget.util.CurrencyFormatter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HistoryScreenTest {

    @Test
    fun cycleHeaderSummaryText_usesComputedRemainingForActiveCycle() {
        val section = section(
            budgetAmountCents = 4_500_00L,
            totalSpentCents = 2_791_25L,
            surplusCents = -1_791_25L,
            isCompletedCycle = false
        )

        assertEquals(
            "Net ${CurrencyFormatter.format(1_708_75L)} available",
            cycleHeaderSummaryText(section)
        )
    }

    @Test
    fun cycleHeaderSummaryText_usesComputedRemainingForCompletedCycle() {
        val section = section(
            budgetAmountCents = 4_500_00L,
            totalSpentCents = 2_791_25L,
            surplusCents = -1_791_25L,
            isCompletedCycle = true
        )

        assertEquals(
            "Finished ${CurrencyFormatter.format(1_708_75L)} under budget",
            cycleHeaderSummaryText(section)
        )
    }

    @Test
    fun cycleHeaderSummaryText_prefersBucketRemainingWhenSectionBudgetIsStale() {
        val section = section(
            budgetAmountCents = 1_000_00L,
            totalSpentCents = 2_791_25L,
            surplusCents = -1_791_25L,
            isCompletedCycle = false,
            bucketSummaries = listOf(
                CycleBucketSummary(
                    bucketUuid = "bills",
                    bucketName = "Bills",
                    spentCents = 2_050_00L,
                    remainingCents = 1_450_00L,
                    overspentCents = 0L
                ),
                CycleBucketSummary(
                    bucketUuid = "spending",
                    bucketName = "Spending money",
                    spentCents = 741_25L,
                    remainingCents = 258_75L,
                    overspentCents = 0L
                )
            )
        )

        assertEquals(
            "Net ${CurrencyFormatter.format(1_708_75L)} available",
            cycleHeaderSummaryText(section)
        )
    }

    private fun section(
        budgetAmountCents: Long,
        totalSpentCents: Long,
        surplusCents: Long,
        isCompletedCycle: Boolean,
        bucketSummaries: List<CycleBucketSummary> = emptyList()
    ) = ExpenseCycleSection(
        cycleStartDate = LocalDate.of(2026, 3, 25),
        cycleEndDateExclusive = LocalDate.of(2026, 4, 25),
        title = "Current cycle",
        budgetAmountCents = budgetAmountCents,
        totalSpentCents = totalSpentCents,
        surplusCents = surplusCents,
        bucketSummaries = bucketSummaries,
        daySections = emptyList(),
        isActiveCycle = !isCompletedCycle,
        isReadOnly = false,
        isCompletedCycle = isCompletedCycle
    )
}
