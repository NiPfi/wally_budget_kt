package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.preferences.UserSettingsStore

class UpdatePaydayDateUseCase(
    private val userSettingsStore: UserSettingsStore
) {
    suspend operator fun invoke(day: Int) {
        userSettingsStore.updatePaydayDate(day)
    }
}
