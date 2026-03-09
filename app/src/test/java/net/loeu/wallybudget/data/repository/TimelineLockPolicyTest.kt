package net.loeu.wallybudget.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class TimelineLockPolicyTest {
    private lateinit var previousLocale: Locale

    @Before
    fun setUp() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(previousLocale)
    }

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
        assertTrue(state.reason!!.contains("Mar 1, 2026"))
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
        assertTrue(state.reason!!.contains("Mar 6, 2026"))
    }
}
