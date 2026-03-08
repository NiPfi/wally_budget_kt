package net.loeu.wallybudget.util

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

object CurrencyFormatter {

    /**
     * Format amount in cents as currency using system locale
     */
    fun format(amountCents: Long): String {
        val formatter = NumberFormat.getCurrencyInstance()
        return formatter.format(amountCents / 100.0)
    }

    /**
     * Format amount in cents as currency with custom locale
     */
    fun format(amountCents: Long, locale: Locale): String {
        val formatter = NumberFormat.getCurrencyInstance(locale)
        return formatter.format(amountCents / 100.0)
    }

    fun formatSigned(amountCents: Long): String {
        return if (amountCents < 0L) {
            "-${format(-amountCents)}"
        } else {
            format(amountCents)
        }
    }

    /**
     * Parse decimal amount text (e.g. "12.34") to cents.
     */
    fun parseAmountToCents(amountText: String): Long? {
        val normalized = amountText.replace(',', '.').trim()
        val value = normalized.toDoubleOrNull() ?: return null
        return if (value >= 0.0) {
            (value * 100.0).roundToLong()
        } else {
            null
        }
    }

    fun centsToDecimalString(amountCents: Long): String {
        return (amountCents / 100.0).toString()
    }
}
