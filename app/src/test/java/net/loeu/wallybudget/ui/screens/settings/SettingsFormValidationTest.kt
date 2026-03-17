package net.loeu.wallybudget.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsFormValidationTest {

    @Test
    fun validateSettingsForm_rejectsInvalidBudget() {
        val result = validateSettingsForm(
            budgetText = "0",
            paydayText = "5"
        )

        assertFalse(result.isValid)
        assertFalse(result.isBudgetValid)
        assertTrue(result.isPaydayValid)
    }

    @Test
    fun validateSettingsForm_rejectsInvalidPayday() {
        val result = validateSettingsForm(
            budgetText = "1200",
            paydayText = ""
        )

        assertFalse(result.isValid)
        assertTrue(result.isBudgetValid)
        assertFalse(result.isPaydayValid)
    }

    @Test
    fun validateSettingsForm_acceptsValidBudgetAndPayday() {
        val result = validateSettingsForm(
            budgetText = "1200",
            paydayText = "5"
        )

        assertTrue(result.isValid)
        assertEquals(120_000L, result.budgetCents)
        assertEquals(5, result.payday)
    }
}
