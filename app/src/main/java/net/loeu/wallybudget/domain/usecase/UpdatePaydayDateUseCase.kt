package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.preferences.UserPreferencesManager

class UpdatePaydayDateUseCase(
    private val userPreferencesManager: UserPreferencesManager
) {
    suspend operator fun invoke(day: Int) {
        userPreferencesManager.updatePaydayDate(day)
    }
}
