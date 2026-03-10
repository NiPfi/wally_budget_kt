package net.loeu.wallybudget.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsFormValidationTest {

    @Test
    fun validateSettingsForm_rejectsInvalidBudgetWhenPaydayIsLocked() {
        val result = validateSettingsForm(
            budgetText = "0",
            paydayText = "5",
            paydayEditingEnabled = false
        )

        assertFalse(result.isValid)
        assertFalse(result.isBudgetValid)
        assertTrue(result.isPaydayValid)
    }

    @Test
    fun validateSettingsForm_rejectsInvalidPaydayWhenEditingIsEnabled() {
        val result = validateSettingsForm(
            budgetText = "1200",
            paydayText = "",
            paydayEditingEnabled = true
        )

        assertFalse(result.isValid)
        assertTrue(result.isBudgetValid)
        assertFalse(result.isPaydayValid)
    }

    @Test
    fun validateSettingsForm_acceptsValidBudgetWhenPaydayIsLocked() {
        val result = validateSettingsForm(
            budgetText = "1200",
            paydayText = "5",
            paydayEditingEnabled = false
        )

        assertTrue(result.isValid)
        assertEquals(120_000L, result.budgetCents)
        assertEquals(null, result.payday)
    }
}
