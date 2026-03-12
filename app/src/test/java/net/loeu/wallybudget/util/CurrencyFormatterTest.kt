package net.loeu.wallybudget.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.DecimalFormatSymbols
import java.util.Locale

class CurrencyFormatterTest {

    @Test
    fun usesVisualMinorUnitInput_isEnabledForUsd() {
        assertTrue(CurrencyFormatter.usesVisualMinorUnitInput(Locale.US))
    }

    @Test
    fun usesVisualMinorUnitInput_isDisabledForJpy() {
        assertFalse(CurrencyFormatter.usesVisualMinorUnitInput(Locale.JAPAN))
    }

    @Test
    fun formatExpenseAmountInput_insertsVisualDecimalForUsd() {
        val formatted = CurrencyFormatter.formatExpenseAmountInput("1234", Locale.US)

        assertEquals("12.34", formatted)
    }

    @Test
    fun parseExpenseAmountToCents_readsUsdInputAsMinorUnits() {
        val cents = CurrencyFormatter.parseExpenseAmountToCents("12.34", Locale.US)

        assertEquals(1_234L, cents)
    }

    @Test
    fun parseExpenseAmountToCents_rejectsGarbageInDecimalLocales() {
        val cents = CurrencyFormatter.parseExpenseAmountToCents("12abc34", Locale.US)

        assertEquals(null, cents)
    }

    @Test
    fun parseExpenseAmountToCents_acceptsLocaleDecimalSeparator() {
        val locale = Locale.Builder()
            .setLanguage("ar")
            .setRegion("KW")
            .build()
        val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator

        val cents = CurrencyFormatter.parseExpenseAmountToCents("12${separator}34", locale)

        assertEquals(1_234L, cents)
    }

    @Test
    fun parseExpenseAmountToCents_keepsWholeUnitsForJpy() {
        val cents = CurrencyFormatter.parseExpenseAmountToCents("500", Locale.JAPAN)

        assertEquals(50_000L, cents)
    }

    @Test
    fun centsToExpenseAmountInput_preservesLegacyFractionalCentsForZeroDecimalLocales() {
        val text = CurrencyFormatter.centsToExpenseAmountInput(1_234L, Locale.JAPAN)

        assertEquals("12.34", text)
    }

    @Test
    fun parseExpenseAmountToCents_roundTripsLegacyFractionalCentsForZeroDecimalLocales() {
        val cents = CurrencyFormatter.parseExpenseAmountToCents("12.34", Locale.JAPAN)

        assertEquals(1_234L, cents)
    }

    @Test
    fun formatExpenseAmountInput_keepsTwoFractionDigitsForCentsStorage() {
        val locale = Locale.Builder()
            .setLanguage("ar")
            .setRegion("KW")
            .build()

        val formatted = CurrencyFormatter.formatExpenseAmountInput("1234", locale)
        val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator

        assertEquals("12${separator}34", formatted)
    }

    @Test
    fun storageFractionDigitsForInput_staysAtTwoForThreeDecimalCurrencies() {
        val locale = Locale.Builder()
            .setLanguage("ar")
            .setRegion("KW")
            .build()

        assertEquals(2, CurrencyFormatter.storageFractionDigitsForInput(locale))
    }
}
