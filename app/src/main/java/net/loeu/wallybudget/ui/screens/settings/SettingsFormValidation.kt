package net.loeu.wallybudget.ui.screens.settings

import net.loeu.wallybudget.util.CurrencyFormatter

internal data class SettingsFormValidationResult(
    val budgetCents: Long?,
    val payday: Int?,
    val isBudgetValid: Boolean,
    val isPaydayValid: Boolean
) {
    val isValid: Boolean
        get() = isBudgetValid && isPaydayValid
}

internal fun validateSettingsForm(
    budgetText: String,
    paydayText: String,
    paydayEditingEnabled: Boolean
): SettingsFormValidationResult {
    val budgetCents = CurrencyFormatter.parseAmountToCents(budgetText)
    val payday = paydayText.toIntOrNull()?.takeIf { paydayEditingEnabled }
    val isBudgetValid = budgetCents != null && budgetCents > 0L
    val isPaydayValid = !paydayEditingEnabled || (payday != null && payday in 1..31)

    return SettingsFormValidationResult(
        budgetCents = budgetCents,
        payday = payday,
        isBudgetValid = isBudgetValid,
        isPaydayValid = isPaydayValid
    )
}
