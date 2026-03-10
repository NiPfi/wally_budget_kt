package net.loeu.wallybudget.domain.service

import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.MonthlyHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class BudgetCalculationServiceTest {

    private val service = BudgetCalculationService()

    @Test
    fun currentCycleProgressRange_stopsAtTomorrow_notNextPayday() {
        val range = service.getCurrentCycleProgressRange(
            now = LocalDate.of(2026, 3, 25),
            paydayDate = 25
        )

        assertEquals(LocalDate.of(2026, 3, 25), range.start)
        assertEquals(LocalDate.of(2026, 3, 26), range.endExclusive)
    }

    @Test
    fun currentCycleProgressRange_capsAtCycleEndOnFinalDay() {
        val range = service.getCurrentCycleProgressRange(
            now = LocalDate.of(2026, 4, 24),
            paydayDate = 25
        )

        assertEquals(LocalDate.of(2026, 3, 25), range.start)
        assertEquals(LocalDate.of(2026, 4, 25), range.endExclusive)
    }

    @Test
    fun getCycleStartDate_invalidLowPayday_isClampedToFirstDay() {
        val cycleStart = service.getCycleStartDate(
            now = LocalDate.of(2026, 4, 10),
            paydayDate = 0
        )

        assertEquals(LocalDate.of(2026, 4, 1), cycleStart)
    }

    @Test
    fun getNextCycleStartDate_invalidLowPayday_isClampedToFirstDay() {
        val nextCycleStart = service.getNextCycleStartDate(
            now = LocalDate.of(2026, 4, 10),
            paydayDate = 0
        )

        assertEquals(LocalDate.of(2026, 5, 1), nextCycleStart)
    }

    @Test
    fun calculateSpendingForecast_onCycleRollover_doesNotExplodeFromSparsePreviousCycle() {
        val now = LocalDate.of(2026, 4, 25)

        val forecast = service.calculateSpendingForecast(
            budgetState = BudgetState(
                monthlyBudgetCents = 100_000L,
                totalSpentThisCycleCents = 0L,
                dailyBudgetCents = 3_333L,
                spentTodayCents = 0L,
                remainingTodayCents = 3_333L,
                daysRemainingInCycle = 30,
                cumulativeSavingsCents = 0L,
                paydayDate = 25,
                cycleStartDate = now
            ),
            now = now,
            monthlyHistory = emptyList<MonthlyHistory>(),
            recentExpenses = listOf(
                expenseOn(LocalDate.of(2026, 3, 25), 4_000L),
                expenseOn(LocalDate.of(2026, 4, 4), 5_000L),
                expenseOn(LocalDate.of(2026, 4, 14), 40_000L),
                expenseOn(LocalDate.of(2026, 4, 24), 50_000L)
            )
        )

        assertFalse(forecast.isProjectedOverBudget)
        assertTrue(forecast.projectedDailySpendCents < 5_000L)
        assertTrue(forecast.projectedTotalSpentCents <= 110_000L)
    }

    @Test
    fun calculateSpendingForecast_doesNotDiluteKnownPreviousCycleWithUnknownPrehistory() {
        val now = LocalDate.of(2026, 4, 25)

        val forecast = service.calculateSpendingForecast(
            budgetState = BudgetState(
                monthlyBudgetCents = 100_000L,
                totalSpentThisCycleCents = 0L,
                dailyBudgetCents = 3_333L,
                spentTodayCents = 0L,
                remainingTodayCents = 3_333L,
                daysRemainingInCycle = 30,
                cumulativeSavingsCents = 0L,
                paydayDate = 25,
                cycleStartDate = now
            ),
            now = now,
            monthlyHistory = listOf(
                MonthlyHistory(
                    cycleStartDate = "2026-03-25",
                    budgetAmountCents = 100_000L,
                    totalSpentCents = 99_000L,
                    surplusCents = 1_000L,
                    cycleEndDate = "2026-04-25",
                    endTimestamp = 0L
                )
            ),
            recentExpenses = listOf(
                expenseOn(LocalDate.of(2026, 3, 25), 4_000L),
                expenseOn(LocalDate.of(2026, 4, 4), 5_000L),
                expenseOn(LocalDate.of(2026, 4, 14), 40_000L),
                expenseOn(LocalDate.of(2026, 4, 24), 50_000L)
            )
        )

        assertTrue(forecast.projectedDailySpendCents >= 3_000L)
        assertTrue(forecast.projectedTotalSpentCents >= 900_00L)
    }

    private fun expenseOn(date: LocalDate, amountCents: Long): Expense {
        return Expense(
            amountCents = amountCents,
            description = "Expense",
            timestamp = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            expenseDate = date.toString()
        )
    }
}
