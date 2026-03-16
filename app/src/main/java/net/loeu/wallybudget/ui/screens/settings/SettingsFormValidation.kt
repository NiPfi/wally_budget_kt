package net.loeu.wallybudget.ui.screens.settings

import net.loeu.wallybudget.domain.model.BudgetChangeMode
import net.loeu.wallybudget.util.CurrencyFormatter

internal data class SettingsFormValidation(
    val budgetCents: Long?,
    val payday: Int?,
    val budgetChangeMode: BudgetChangeMode,
    val isBudgetValid: Boolean,
    val isPaydayValid: Boolean
) {
    val isValid: Boolean
        get() = isBudgetValid && isPaydayValid
}

internal fun validateSettingsForm(
    budgetText: String,
    paydayText: String,
    budgetChangeMode: BudgetChangeMode
): SettingsFormValidation {
    val budgetCents = CurrencyFormatter.parseAmountToCents(budgetText)
    val payday = paydayText.toIntOrNull()
    val isBudgetValid = budgetCents != null && budgetCents > 0L
    val isPaydayValid = payday != null && payday in 1..31

    return SettingsFormValidation(
        budgetCents = budgetCents,
        payday = payday,
        budgetChangeMode = budgetChangeMode,
        isBudgetValid = isBudgetValid,
        isPaydayValid = isPaydayValid
    )
}
