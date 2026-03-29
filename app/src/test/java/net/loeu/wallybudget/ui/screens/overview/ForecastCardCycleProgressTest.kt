package net.loeu.wallybudget.ui.screens.overview

import net.loeu.wallybudget.domain.model.BudgetState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ForecastCardCycleProgressTest {

    @Test
    fun calculateForecastCycleProgress_usesEffectiveCurrentDate() {
        val budgetState = BudgetState(
            monthlyBudgetCents = 250_000L,
            totalSpentThisCycleCents = 83_000L,
            dailyBudgetCents = 9_500L,
            spentTodayCents = 1_299L,
            remainingTodayCents = 8_201L,
            daysRemainingInCycle = 18,
            cumulativeSavingsCents = 12_000L,
            paydayDate = 1,
            cycleStartDate = LocalDate.of(2026, 3, 7),
        )

        val (daysElapsed, totalDaysInCycle) = calculateForecastCycleProgress(
            budgetState = budgetState,
            effectiveCurrentDate = LocalDate.of(2026, 3, 19),
        )

        assertEquals(12, daysElapsed)
        assertEquals(30, totalDaysInCycle)
    }

    @Test
    fun calculateForecastCycleProgress_clampsDatesBeforeCycleStart() {
        val budgetState = BudgetState(
            monthlyBudgetCents = 250_000L,
            totalSpentThisCycleCents = 0L,
            dailyBudgetCents = 9_500L,
            spentTodayCents = 0L,
            remainingTodayCents = 9_500L,
            daysRemainingInCycle = 30,
            cumulativeSavingsCents = 12_000L,
            paydayDate = 1,
            cycleStartDate = LocalDate.of(2026, 3, 7),
        )

        val (daysElapsed, totalDaysInCycle) = calculateForecastCycleProgress(
            budgetState = budgetState,
            effectiveCurrentDate = LocalDate.of(2026, 3, 1),
        )

        assertEquals(0, daysElapsed)
        assertEquals(30, totalDaysInCycle)
    }
}
