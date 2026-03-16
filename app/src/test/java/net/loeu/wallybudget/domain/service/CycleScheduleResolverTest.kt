package net.loeu.wallybudget.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate

class CycleScheduleResolverTest {

    private val resolver = CycleScheduleResolver(BudgetCalculationService())

    @Test
    fun planPaydayTransition_createsBridgeCycleBeforeFirstRegularCycle() {
        val transition = resolver.planPaydayTransition(
            currentCycleEndExclusive = LocalDate.of(2026, 4, 25),
            targetMonthlyBudgetCents = 120_000L,
            newPaydayDayOfMonth = 1
        )

        assertNotNull(transition.bridgeCycle)
        assertEquals(LocalDate.of(2026, 4, 25), transition.bridgeCycle?.cycleStart)
        assertEquals(LocalDate.of(2026, 5, 1), transition.bridgeCycle?.cycleEndExclusive)
        assertEquals(23_226L, transition.bridgeCycle?.budgetAmountCents)
        assertEquals(LocalDate.of(2026, 5, 1), transition.firstRegularCycle.cycleStart)
        assertEquals(LocalDate.of(2026, 6, 1), transition.firstRegularCycle.cycleEndExclusive)
    }
}
