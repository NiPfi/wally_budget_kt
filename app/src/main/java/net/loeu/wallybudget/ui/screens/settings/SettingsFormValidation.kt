package net.loeu.wallybudget.ui.screens.settings

import net.loeu.wallybudget.util.CurrencyFormatter

internal data class SettingsFormValidation(
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
    paydayText: String
): SettingsFormValidation {
    val budgetCents = CurrencyFormatter.parseAmountToCents(budgetText)
    val payday = paydayText.toIntOrNull()
    val isBudgetValid = budgetCents != null && budgetCents > 0L
    val isPaydayValid = payday != null && payday in 1..31

    return SettingsFormValidation(
        budgetCents = budgetCents,
        payday = payday,
        isBudgetValid = isBudgetValid,
        isPaydayValid = isPaydayValid
    )
}

internal fun settingsDraftsMatch(
    currentBudgetText: String,
    currentPaydayText: String,
    currentBucketDrafts: List<EditableBucketUi>,
    externalBudgetText: String,
    externalPaydayText: String,
    externalBucketDrafts: List<EditableBucketUi>
): Boolean {
    return currentBudgetText == externalBudgetText &&
        currentPaydayText == externalPaydayText &&
        currentBucketDrafts == externalBucketDrafts
}

internal fun shouldSyncSettingsDrafts(
    currentBudgetText: String,
    currentPaydayText: String,
    currentBucketDrafts: List<EditableBucketUi>,
    externalBudgetText: String,
    externalPaydayText: String,
    externalBucketDrafts: List<EditableBucketUi>,
    isEditorOpen: Boolean
): Boolean {
    return currentBucketDrafts.isEmpty() || (
        !isEditorOpen && settingsDraftsMatch(
            currentBudgetText = currentBudgetText,
            currentPaydayText = currentPaydayText,
            currentBucketDrafts = currentBucketDrafts,
            externalBudgetText = externalBudgetText,
            externalPaydayText = externalPaydayText,
            externalBucketDrafts = externalBucketDrafts
        )
    )
}
