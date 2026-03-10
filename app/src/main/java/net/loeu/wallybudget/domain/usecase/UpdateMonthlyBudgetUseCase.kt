package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.preferences.UserSettingsStore

class UpdateMonthlyBudgetUseCase(
    private val userSettingsStore: UserSettingsStore
) {
    suspend operator fun invoke(amountCents: Long) {
        userSettingsStore.updateMonthlyBudget(amountCents)
    }
}
