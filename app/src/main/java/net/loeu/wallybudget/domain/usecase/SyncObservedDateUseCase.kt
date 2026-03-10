package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.policy.ObservedDatePolicy
import net.loeu.wallybudget.domain.usecase.internal.lastSeenDateOrNull
import java.time.LocalDate

class SyncObservedDateUseCase(
    private val userSettingsStore: UserSettingsStore
) {
    suspend operator fun invoke(settings: UserSettings, observedDate: LocalDate): LocalDate {
        val lastSeenDate = settings.lastSeenDateOrNull()
        if (ObservedDatePolicy.shouldPersist(lastSeenDate, observedDate)) {
            userSettingsStore.updateLastSeenDate(observedDate)
        }
        return ObservedDatePolicy.resolve(lastSeenDate, observedDate)
    }
}
