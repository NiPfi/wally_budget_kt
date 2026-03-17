package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.preferences.UserSettingsStore

class ClearPendingSettingsUndoUseCase(
    private val userSettingsStore: UserSettingsStore
) {
    suspend operator fun invoke() {
        userSettingsStore.clearPendingSettingsUndo()
    }
}
