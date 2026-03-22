package net.loeu.wallybudget.ui

import net.loeu.wallybudget.util.CurrencyFormatter

internal object CurrencyPlaceholderSamples {
    fun amount(amountCents: Long): String = CurrencyFormatter.format(amountCents)

    fun prefixedAmount(prefix: String, amountCents: Long): String = prefix + amount(amountCents)

    fun forecastRangeSummary(lowerAmountCents: Long, upperAmountCents: Long): String {
        return "Forecast range runs from ${amount(lowerAmountCents)} to " +
            "${amount(upperAmountCents)} total spend by cycle end."
    }

    fun forecastRangeDetail(remainingAmountCents: Long, upperAmountCents: Long): String {
        return "Current projection still leaves ${amount(remainingAmountCents)}, " +
            "but the upper range reaches ${amount(upperAmountCents)}."
    }
}
