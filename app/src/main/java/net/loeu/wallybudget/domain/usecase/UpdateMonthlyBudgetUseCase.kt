package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.preferences.UserPreferencesManager

class UpdateMonthlyBudgetUseCase(
    private val userPreferencesManager: UserPreferencesManager
) {
    suspend operator fun invoke(amountCents: Long) {
        userPreferencesManager.updateMonthlyBudget(amountCents)
    }
}
