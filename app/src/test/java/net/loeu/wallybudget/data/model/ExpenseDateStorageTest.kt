package net.loeu.wallybudget.data.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ExpenseDateStorageTest {

    @Test
    fun sumByDate_usesStoredExpenseDate_insteadOfRecomputedTimezoneDate() {
        val expense = Expense(
            amountCents = 2_500L,
            description = "Late train",
            timestamp = Instant.parse("2026-03-02T23:30:00Z").toEpochMilli(),
            expenseDate = "2026-03-03"
        )

        val totals = listOf(expense).sumByDate()

        assertEquals(mapOf(LocalDate.of(2026, 3, 3) to 2_500L), totals)
    }

    @Test
    fun groupByDate_usesStoredExpenseDate_forCalendarBucketing() {
        val expenses = listOf(
            Expense(
                amountCents = 1_000L,
                description = "Coffee",
                timestamp = Instant.parse("2026-03-02T22:30:00Z").toEpochMilli(),
                expenseDate = "2026-03-03"
            ),
            Expense(
                amountCents = 1_500L,
                description = "Dinner",
                timestamp = Instant.parse("2026-03-03T18:00:00Z").toEpochMilli(),
                expenseDate = "2026-03-03"
            )
        )

        val grouped = expenses.groupByDate()

        assertEquals(listOf(LocalDate.of(2026, 3, 3)), grouped.keys.toList())
        assertEquals(2, grouped.getValue(LocalDate.of(2026, 3, 3)).size)
    }
}
