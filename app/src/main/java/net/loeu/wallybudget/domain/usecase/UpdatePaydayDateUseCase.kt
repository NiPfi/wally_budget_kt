package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import kotlinx.coroutines.flow.first

class UpdatePaydayDateUseCase(
    private val userSettingsStore: UserSettingsStore
) {
    suspend operator fun invoke(day: Int) {
        if (userSettingsStore.userSettings.first().isOnboardingCompleted) {
            return
        }
        userSettingsStore.updatePaydayDate(day)
    }
}
