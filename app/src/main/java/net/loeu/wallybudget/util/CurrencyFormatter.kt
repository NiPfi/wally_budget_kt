package net.loeu.wallybudget.util

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object CurrencyFormatter {

    /**
     * Format amount as currency using system locale
     */
    fun format(amount: Double): String {
        val formatter = NumberFormat.getCurrencyInstance()
        return formatter.format(amount)
    }

    /**
     * Format amount as currency with custom locale
     */
    fun format(amount: Double, locale: Locale): String {
        val formatter = NumberFormat.getCurrencyInstance(locale)
        return formatter.format(amount)
    }

    /**
     * Get currency symbol for system locale
     */
    fun getCurrencySymbol(): String {
        val formatter = NumberFormat.getCurrencyInstance()
        return formatter.currency?.symbol ?: "$"
    }

    /**
     * Parse currency string to double (best effort)
     */
    fun parse(currencyString: String): Double? {
        return try {
            val formatter = NumberFormat.getCurrencyInstance()
            formatter.parse(currencyString)?.toDouble()
        } catch (e: Exception) {
            null
        }
    }
}

