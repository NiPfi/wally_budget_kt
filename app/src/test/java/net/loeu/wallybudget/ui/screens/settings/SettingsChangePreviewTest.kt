package net.loeu.wallybudget.ui.screens.settings

import net.loeu.wallybudget.domain.model.BudgetState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SettingsChangePreviewTest {

    @Test
    fun calculateSettingsChangePreview_extending_cycle_spreads_remaining_budget_over_more_days() {
        val preview = calculateSettingsChangePreview(
            budgetState = testBudgetState(),
            currentDate = LocalDate.of(2026, 12, 4),
            currentMonthlyBudgetCents = 100_000L,
            proposedMonthlyBudgetCents = 100_000L,
            proposedPaydayDayOfMonth = 1
        )

        assertEquals(LocalDate.of(2026, 12, 25), preview.currentCycleEnd)
        assertEquals(LocalDate.of(2027, 1, 1), preview.projectedCycleEnd)
        assertEquals(3_750L, preview.currentDailyBudgetCents)
        assertEquals(2_703L, preview.projectedDailyBudgetCents)
        assertEquals(2_500L, preview.currentRemainingTodayCents)
        assertEquals(1_453L, preview.projectedRemainingTodayCents)
        assertEquals(60_000L, preview.currentRemainingCycleCents)
        assertEquals(60_000L, preview.projectedRemainingCycleCents)
    }

    @Test
    fun calculateSettingsChangePreview_budget_change_prorates_remaining_cycle_from_today() {
        val preview = calculateSettingsChangePreview(
            budgetState = testBudgetState(),
            currentDate = LocalDate.of(2026, 12, 4),
            currentMonthlyBudgetCents = 100_000L,
            proposedMonthlyBudgetCents = 120_000L,
            proposedPaydayDayOfMonth = 25
        )

        assertEquals(LocalDate.of(2026, 12, 25), preview.projectedCycleEnd)
        assertEquals(4_000L, preview.projectedDailyBudgetCents)
        assertEquals(2_750L, preview.projectedRemainingTodayCents)
        assertEquals(82_750L, preview.projectedRemainingCycleCents)
    }

    @Test
    fun calculateSettingsChangePreview_respects_existing_prorated_segments_before_today() {
        val preview = calculateSettingsChangePreview(
            budgetState = BudgetState(
                monthlyBudgetCents = 116_000L,
                totalSpentThisCycleCents = 32_000L,
                dailyBudgetCents = 4_000L,
                spentTodayCents = 2_000L,
                remainingTodayCents = 2_095L,
                daysRemainingInCycle = 21,
                cumulativeSavingsCents = 8_000L,
                paydayDate = 25,
                cycleStartDate = LocalDate.of(2026, 11, 25)
            ),
            currentDate = LocalDate.of(2026, 12, 4),
            currentMonthlyBudgetCents = 120_000L,
            proposedMonthlyBudgetCents = 110_000L,
            proposedPaydayDayOfMonth = 25
        )

        assertEquals(LocalDate.of(2026, 12, 25), preview.projectedCycleEnd)
        assertEquals(4_000L, preview.currentDailyBudgetCents)
        assertEquals(3_667L, preview.projectedDailyBudgetCents)
        assertEquals(2_095L, preview.currentRemainingTodayCents)
        assertEquals(1_762L, preview.projectedRemainingTodayCents)
        assertEquals(84_000L, preview.currentRemainingCycleCents)
        assertEquals(76_995L, preview.projectedRemainingCycleCents)
    }

    private fun testBudgetState() = BudgetState(
        monthlyBudgetCents = 100_000L,
        totalSpentThisCycleCents = 40_000L,
        dailyBudgetCents = 3_750L,
        spentTodayCents = 1_250L,
        remainingTodayCents = 2_500L,
        daysRemainingInCycle = 21,
        cumulativeSavingsCents = 8_000L,
        paydayDate = 25,
        cycleStartDate = LocalDate.of(2026, 11, 25)
    )
}
