package net.loeu.wallybudget.domain.usecase.internal

import net.loeu.wallybudget.domain.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class BudgetUseCaseSupportTest {

    @Test
    fun lastSeenDateOrNull_returnsNull_forMalformedDate() {
        val settings = UserSettings(lastSeenDate = "not-a-date")

        assertNull(settings.lastSeenDateOrNull())
    }

    @Test
    fun pendingCycleRangeOrNull_returnsNull_forMalformedStartDate() {
        val settings = UserSettings(
            pendingCycleStartDate = "bad-start",
            pendingCycleEndDateExclusive = "2026-04-25"
        )

        assertNull(settings.pendingCycleRangeOrNull())
    }

    @Test
    fun pendingCycleRangeOrNull_returnsRange_forValidDates() {
        val settings = UserSettings(
            pendingCycleStartDate = "2026-03-25",
            pendingCycleEndDateExclusive = "2026-04-25"
        )

        val range = settings.pendingCycleRangeOrNull()

        assertEquals(LocalDate.of(2026, 3, 25), range?.start)
        assertEquals(LocalDate.of(2026, 4, 25), range?.endExclusive)
    }

    @Test
    fun pendingCycleRangeOrNull_returnsNull_forSameDayRange() {
        val sameDaySettings = UserSettings(
            pendingCycleStartDate = "2026-03-25",
            pendingCycleEndDateExclusive = "2026-03-25"
        )

        assertNull(sameDaySettings.pendingCycleRangeOrNull())
    }

    @Test
    fun pendingCycleRangeOrNull_returnsNull_forReversedRange() {
        val reversedSettings = UserSettings(
            pendingCycleStartDate = "2026-04-25",
            pendingCycleEndDateExclusive = "2026-03-25"
        )

        assertNull(reversedSettings.pendingCycleRangeOrNull())
    }
}
