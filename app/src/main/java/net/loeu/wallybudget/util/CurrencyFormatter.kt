package net.loeu.wallybudget.util

import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

object CurrencyFormatter {
    private const val STORAGE_FRACTION_DIGITS = 2

    private val formatterCache: ThreadLocal<MutableMap<Locale, NumberFormat>> =
        ThreadLocal.withInitial { mutableMapOf() }

    /**
     * Format amount in cents as currency using system locale
     */
    fun format(amountCents: Long): String {
        return format(amountCents, Locale.getDefault())
    }

    /**
     * Format amount in cents as currency with custom locale
     */
    fun format(amountCents: Long, locale: Locale): String {
        return currencyFormatter(locale).format(amountCents / 100.0)
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

    fun usesVisualMinorUnitInput(locale: Locale = Locale.getDefault()): Boolean {
        return currencyFractionDigitsForInput(locale) > 0
    }

    fun parseExpenseAmountToCents(amountText: String, locale: Locale = Locale.getDefault()): Long? {
        val normalized = normalizeExpenseAmountText(amountText, locale) ?: return null
        return parseAmountToCents(normalized)
    }

    fun formatExpenseAmountInput(amountText: String, locale: Locale = Locale.getDefault()): String {
        if (!usesVisualMinorUnitInput(locale)) {
            return amountText
        }

        val fractionDigits = storageFractionDigitsForInput(locale)
        val digits = amountText.filter(Char::isDigit)
        if (digits.isEmpty()) {
            return ""
        }

        val paddedDigits = digits.padStart(fractionDigits + 1, '0')
        val integerDigits = paddedDigits.dropLast(fractionDigits).trimStart('0').ifEmpty { "0" }
        val fractionComponent = paddedDigits.takeLast(fractionDigits)
        val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
        return "$integerDigits$separator$fractionComponent"
    }

    fun centsToExpenseAmountInput(amountCents: Long, locale: Locale = Locale.getDefault()): String {
        return if (usesVisualMinorUnitInput(locale)) {
            formatExpenseAmountInput(amountCents.toString(), locale)
        } else {
            if (amountCents % 100L == 0L) {
                (amountCents / 100L).toString()
            } else {
                centsToDecimalString(amountCents)
            }
        }
    }

    fun expenseAmountPlaceholder(locale: Locale = Locale.getDefault()): String {
        return if (usesVisualMinorUnitInput(locale)) {
            formatExpenseAmountInput("0", locale)
        } else {
            "0"
        }
    }

    private fun currencyFormatter(locale: Locale): NumberFormat {
        val formatters = checkNotNull(formatterCache.get())
        return formatters.getOrPut(locale) {
            NumberFormat.getCurrencyInstance(locale)
        }
    }

    fun currencyFractionDigitsForInput(locale: Locale = Locale.getDefault()): Int {
        return currencyFormatter(locale).currency?.defaultFractionDigits?.takeIf { it >= 0 } ?: STORAGE_FRACTION_DIGITS
    }

    fun storageFractionDigitsForInput(locale: Locale = Locale.getDefault()): Int {
        return if (currencyFractionDigitsForInput(locale) > 0) {
            STORAGE_FRACTION_DIGITS
        } else {
            0
        }
    }

    private fun normalizeExpenseAmountText(amountText: String, locale: Locale): String? {
        val trimmed = amountText.trim()
        if (trimmed.isEmpty()) {
            return null
        }

        val localeDecimalSeparator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
        var seenDecimalSeparator = false
        val normalized = StringBuilder(trimmed.length)

        trimmed.forEachIndexed { index, char ->
            when {
                char.isDigit() -> normalized.append(char)
                char == localeDecimalSeparator || char == '.' || char == ',' -> {
                    if (seenDecimalSeparator) {
                        return null
                    }
                    seenDecimalSeparator = true
                    normalized.append('.')
                }
                char == '+' && index == 0 -> Unit
                else -> return null
            }
        }

        return normalized.toString()
    }
}
