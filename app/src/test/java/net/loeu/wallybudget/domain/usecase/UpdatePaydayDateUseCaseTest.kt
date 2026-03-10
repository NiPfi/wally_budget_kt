package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.domain.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class UpdatePaydayDateUseCaseTest {

    @Test
    fun invoke_updatesPaydayBeforeOnboardingCompletes() = runBlocking {
        val store = FakeUserSettingsStore(
            initialSettings = UserSettings(isOnboardingCompleted = false, paydayDate = 5)
        )

        UpdatePaydayDateUseCase(store)(12)

        assertEquals(12, store.lastUpdatedPaydayDate)
    }

    @Test
    fun invoke_ignoresPaydayChangesAfterOnboardingCompletes() = runBlocking {
        val store = FakeUserSettingsStore(
            initialSettings = UserSettings(isOnboardingCompleted = true, paydayDate = 5)
        )

        UpdatePaydayDateUseCase(store)(12)

        assertEquals(null, store.lastUpdatedPaydayDate)
    }

    private class FakeUserSettingsStore(
        initialSettings: UserSettings
    ) : UserSettingsStore {
        private val settingsFlow = MutableStateFlow(initialSettings)
        var lastUpdatedPaydayDate: Int? = null

        override val userSettings: Flow<UserSettings> = settingsFlow

        override suspend fun updateMonthlyBudget(amountCents: Long) = Unit

        override suspend fun updatePaydayDate(day: Int) {
            lastUpdatedPaydayDate = day
            settingsFlow.value = settingsFlow.value.copy(paydayDate = day)
        }

        override suspend fun updateLastResetTimestamp(timestamp: Long) = Unit

        override suspend fun updateLastSeenDate(date: LocalDate) = Unit

        override suspend fun completeOnboarding() = Unit

        override suspend fun setPendingCycle(
            cycleStartDate: LocalDate,
            cycleEndDateExclusive: LocalDate,
            detectedAtTimestamp: Long
        ) = Unit

        override suspend fun clearPendingCycle() = Unit
    }
}
