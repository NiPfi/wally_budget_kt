package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.preferences.UserPreferencesManager
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.policy.ObservedDatePolicy
import net.loeu.wallybudget.domain.usecase.internal.lastSeenDateOrNull
import java.time.LocalDate

class SyncObservedDateUseCase(
    private val userPreferencesManager: UserPreferencesManager
) {
    suspend operator fun invoke(settings: UserSettings, observedDate: LocalDate): LocalDate {
        val lastSeenDate = settings.lastSeenDateOrNull()
        if (ObservedDatePolicy.shouldPersist(lastSeenDate, observedDate)) {
            userPreferencesManager.updateLastSeenDate(observedDate)
        }
        return ObservedDatePolicy.resolve(lastSeenDate, observedDate)
    }
}
