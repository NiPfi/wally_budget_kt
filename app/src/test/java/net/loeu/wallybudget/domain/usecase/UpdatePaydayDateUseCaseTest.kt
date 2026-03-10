package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.domain.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdatePaydayDateUseCaseTest {

    @Test
    fun invoke_updatesPaydayBeforeOnboardingCompletes() = runBlocking {
        val store = FakeUserSettingsStore(
            initialSettings = UserSettings(isOnboardingCompleted = false, paydayDate = 5)
        )

        UpdatePaydayDateUseCase(store)(12)

        assertEquals(12, store.currentSettings.paydayDate)
    }

    @Test
    fun invoke_ignoresPaydayChangesAfterOnboardingCompletes() = runBlocking {
        val store = FakeUserSettingsStore(
            initialSettings = UserSettings(isOnboardingCompleted = true, paydayDate = 5)
        )

        UpdatePaydayDateUseCase(store)(12)

        assertEquals(5, store.currentSettings.paydayDate)
    }
}
