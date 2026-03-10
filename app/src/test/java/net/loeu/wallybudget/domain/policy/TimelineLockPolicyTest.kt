package net.loeu.wallybudget.domain.policy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class TimelineLockPolicyTest {
    @Test
    fun resolve_unlocksWhenTimelineIsConsistent() {
        val state = TimelineLockPolicy.resolve(
            effectiveCurrentDate = LocalDate.of(2026, 3, 9),
            currentCycleStart = LocalDate.of(2026, 3, 1),
            lastResetDate = LocalDate.of(2026, 3, 1),
            latestExpenseDate = LocalDate.of(2026, 3, 9)
        )

        assertFalse(state.isLocked)
    }

    @Test
    fun resolve_locksWhenDateFallsBackIntoClosedCycle() {
        val state = TimelineLockPolicy.resolve(
            effectiveCurrentDate = LocalDate.of(2026, 2, 28),
            currentCycleStart = LocalDate.of(2026, 2, 1),
            lastResetDate = LocalDate.of(2026, 3, 1),
            latestExpenseDate = LocalDate.of(2026, 2, 28)
        )

        assertTrue(state.isLocked)
        assertTrue(state.reason!!.contains(displayDate(LocalDate.of(2026, 3, 1))))
    }

    @Test
    fun resolve_locksWhenFutureExpensesExist() {
        val state = TimelineLockPolicy.resolve(
            effectiveCurrentDate = LocalDate.of(2026, 3, 2),
            currentCycleStart = LocalDate.of(2026, 3, 1),
            lastResetDate = LocalDate.of(2026, 3, 1),
            latestExpenseDate = LocalDate.of(2026, 3, 6)
        )

        assertTrue(state.isLocked)
        assertTrue(state.reason!!.contains(displayDate(LocalDate.of(2026, 3, 6))))
    }

    private fun displayDate(date: LocalDate): String {
        return date.format(
            DateTimeFormatter
                .ofPattern("MMM d, yyyy")
                .withLocale(Locale.getDefault())
        )
    }
}
