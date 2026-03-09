package net.loeu.wallybudget.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ExpenseEntryDatePolicyTest {

    @Test
    fun resolveRequestedDate_defaultsToEffectiveCurrentDate() {
        val effectiveCurrentDate = LocalDate.of(2026, 3, 8)

        val resolvedDate = ExpenseEntryDatePolicy.resolveRequestedDate(
            requestedDate = null,
            effectiveCurrentDate = effectiveCurrentDate
        )

        assertEquals(effectiveCurrentDate, resolvedDate)
    }

    @Test
    fun resolveRequestedDate_preservesExplicitSelection() {
        val requestedDate = LocalDate.of(2026, 3, 7)
        val effectiveCurrentDate = LocalDate.of(2026, 3, 8)

        val resolvedDate = ExpenseEntryDatePolicy.resolveRequestedDate(
            requestedDate = requestedDate,
            effectiveCurrentDate = effectiveCurrentDate
        )

        assertEquals(requestedDate, resolvedDate)
    }

    @Test
    fun resolveRequestedDate_clampsFutureDateToEffectiveCurrentDate() {
        val requestedDate = LocalDate.of(2026, 3, 9)
        val effectiveCurrentDate = LocalDate.of(2026, 3, 8)

        val resolvedDate = ExpenseEntryDatePolicy.resolveRequestedDate(
            requestedDate = requestedDate,
            effectiveCurrentDate = effectiveCurrentDate
        )

        assertEquals(effectiveCurrentDate, resolvedDate)
    }

}