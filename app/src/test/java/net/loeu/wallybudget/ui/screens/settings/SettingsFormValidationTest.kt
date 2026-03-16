package net.loeu.wallybudget.ui.screens.settings

import net.loeu.wallybudget.domain.model.BudgetChangeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsFormValidationTest {

    @Test
    fun validateSettingsForm_rejectsInvalidBudget() {
        val result = validateSettingsForm(
            budgetText = "0",
            paydayText = "5",
            budgetChangeMode = BudgetChangeMode.APPLY_NEXT_CYCLE
        )

        assertFalse(result.isValid)
        assertFalse(result.isBudgetValid)
        assertTrue(result.isPaydayValid)
    }

    @Test
    fun validateSettingsForm_rejectsInvalidPayday() {
        val result = validateSettingsForm(
            budgetText = "1200",
            paydayText = "",
            budgetChangeMode = BudgetChangeMode.APPLY_NEXT_CYCLE
        )

        assertFalse(result.isValid)
        assertTrue(result.isBudgetValid)
        assertFalse(result.isPaydayValid)
    }

    @Test
    fun validateSettingsForm_acceptsValidBudgetAndPayday() {
        val result = validateSettingsForm(
            budgetText = "1200",
            paydayText = "5",
            budgetChangeMode = BudgetChangeMode.PRORATE_CURRENT_CYCLE
        )

        assertTrue(result.isValid)
        assertEquals(120_000L, result.budgetCents)
        assertEquals(5, result.payday)
    }
}
