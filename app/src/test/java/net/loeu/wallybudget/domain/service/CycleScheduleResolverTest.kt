package net.loeu.wallybudget.domain.service

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CycleScheduleResolverTest {

    private val resolver = CycleScheduleResolver(BudgetCalculationService())

    @Test
    fun planImmediatePaydayChange_extendsCurrentCycleWhenLaterPaydayFallsAfterCurrentEnd() {
        val plan = resolver.planImmediatePaydayChange(
            currentCycle = ResolvedCyclePolicy(
                cycleStart = LocalDate.of(2026, 3, 25),
                cycleEndExclusive = LocalDate.of(2026, 4, 25),
                budgetAmountCents = 100_000L,
                paydayDayOfMonth = 25
            ),
            today = LocalDate.of(2026, 4, 10),
            targetMonthlyBudgetCents = 120_000L,
            newPaydayDayOfMonth = 1
        )

        assertEquals(LocalDate.of(2026, 4, 25), plan.originalCurrentCycleEndExclusive)
        assertEquals(LocalDate.of(2026, 3, 25), plan.rewrittenCurrentCycle.cycleStart)
        assertEquals(LocalDate.of(2026, 5, 1), plan.rewrittenCurrentCycle.cycleEndExclusive)
        assertEquals(100_000L, plan.rewrittenCurrentCycle.budgetAmountCents)
        assertEquals(1, plan.rewrittenCurrentCycle.paydayDayOfMonth)
        assertEquals(LocalDate.of(2026, 5, 1), plan.firstRegularCycle.cycleStart)
        assertEquals(LocalDate.of(2026, 6, 1), plan.firstRegularCycle.cycleEndExclusive)
        assertEquals(120_000L, plan.firstRegularCycle.budgetAmountCents)
    }

    @Test
    fun planImmediatePaydayChange_shortensCurrentCycleWhenEarlierPaydayStillAhead() {
        val plan = resolver.planImmediatePaydayChange(
            currentCycle = ResolvedCyclePolicy(
                cycleStart = LocalDate.of(2026, 3, 25),
                cycleEndExclusive = LocalDate.of(2026, 4, 25),
                budgetAmountCents = 100_000L,
                paydayDayOfMonth = 25
            ),
            today = LocalDate.of(2026, 4, 10),
            targetMonthlyBudgetCents = 120_000L,
            newPaydayDayOfMonth = 20
        )

        assertEquals(LocalDate.of(2026, 4, 25), plan.originalCurrentCycleEndExclusive)
        assertEquals(LocalDate.of(2026, 3, 25), plan.rewrittenCurrentCycle.cycleStart)
        assertEquals(LocalDate.of(2026, 4, 20), plan.rewrittenCurrentCycle.cycleEndExclusive)
        assertEquals(100_000L, plan.rewrittenCurrentCycle.budgetAmountCents)
        assertEquals(20, plan.rewrittenCurrentCycle.paydayDayOfMonth)
        assertEquals(LocalDate.of(2026, 4, 20), plan.firstRegularCycle.cycleStart)
        assertEquals(LocalDate.of(2026, 5, 20), plan.firstRegularCycle.cycleEndExclusive)
        assertEquals(120_000L, plan.firstRegularCycle.budgetAmountCents)
    }
}
