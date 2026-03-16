package net.loeu.wallybudget.domain.service

import net.loeu.wallybudget.domain.model.BudgetAdjustment
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BudgetAdjustmentResolverTest {

    private val resolver = BudgetAdjustmentResolver()

    @Test
    fun resolveCycleBudget_proratesOnlyRemainingDaysAfterAdjustment() {
        val result = resolver.resolveCycleBudget(
            cycleStart = LocalDate.of(2026, 4, 1),
            cycleEndExclusive = LocalDate.of(2026, 5, 1),
            baseMonthlyBudgetCents = 90_000L,
            adjustments = listOf(
                adjustment(
                    cycleStart = LocalDate.of(2026, 4, 1),
                    effectiveDate = LocalDate.of(2026, 4, 16),
                    previousMonthlyBudgetCents = 90_000L,
                    newMonthlyBudgetCents = 120_000L
                )
            ),
            today = LocalDate.of(2026, 4, 20)
        )

        assertEquals(105_000L, result.effectiveCycleBudgetCents)
        assertEquals(61_000L, result.allocatedBeforeDateCents)
        assertEquals(4_000L, result.plannedTodayBudgetCents)
        assertEquals(120_000L, result.effectiveMonthlyBudgetCents)
    }

    private fun adjustment(
        cycleStart: LocalDate,
        effectiveDate: LocalDate,
        previousMonthlyBudgetCents: Long,
        newMonthlyBudgetCents: Long
    ): BudgetAdjustment {
        return BudgetAdjustment(
            adjustmentUuid = "adjustment",
            cycleStartDate = cycleStart.toString(),
            effectiveDate = effectiveDate.toString(),
            previousMonthlyBudgetCents = previousMonthlyBudgetCents,
            newMonthlyBudgetCents = newMonthlyBudgetCents,
            originInstallId = "test-install-id",
            lastModifiedByInstallId = "test-install-id",
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
            deletedAtEpochMs = null,
            modClock = "0000000000001-0000-test-install-id"
        )
    }
}
