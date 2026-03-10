package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.domain.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SyncObservedDateUseCaseTest {

    @Test
    fun invoke_persistsForwardProgress() = runBlocking {
        val store = FakeUserSettingsStore(
            UserSettings(lastSeenDate = "2026-03-08")
        )
        val useCase = SyncObservedDateUseCase(store)

        val effectiveDate = useCase(
            settings = store.currentSettings,
            observedDate = LocalDate.of(2026, 3, 9)
        )

        assertEquals(LocalDate.of(2026, 3, 9), effectiveDate)
        assertEquals("2026-03-09", store.currentSettings.lastSeenDate)
    }

    @Test
    fun invoke_keepsMonotonicDate_forOneDayRollbackWithoutPersisting() = runBlocking {
        val store = FakeUserSettingsStore(
            UserSettings(lastSeenDate = "2026-03-09")
        )
        val useCase = SyncObservedDateUseCase(store)

        val effectiveDate = useCase(
            settings = store.currentSettings,
            observedDate = LocalDate.of(2026, 3, 8)
        )

        assertEquals(LocalDate.of(2026, 3, 9), effectiveDate)
        assertEquals("2026-03-09", store.currentSettings.lastSeenDate)
    }
}
