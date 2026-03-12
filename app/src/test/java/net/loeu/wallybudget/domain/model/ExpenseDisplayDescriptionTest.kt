package net.loeu.wallybudget.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpenseDisplayDescriptionTest {

    @Test
    fun displayDescription_usesStoredDescriptionWhenPresent() {
        val expense = Expense(
            amountCents = 1_200L,
            description = "Lunch",
            icon = ExpenseCategory.RESTAURANT
        )

        assertEquals("Lunch", expense.displayDescription)
    }

    @Test
    fun displayDescription_fallsBackToCategoryWhenDescriptionIsBlank() {
        val expense = Expense(
            amountCents = 4_500L,
            description = "",
            icon = ExpenseCategory.GROCERIES
        )

        assertEquals("Groceries", expense.displayDescription)
    }

    @Test
    fun displayDescription_fallsBackToGenericLabelWithoutCategory() {
        val expense = Expense(
            amountCents = 4_500L,
            description = "",
            icon = null
        )

        assertEquals("Expense", expense.displayDescription)
    }
}
