package net.loeu.wallybudget.domain.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ObservedDatePolicyTest {

    @Test
    fun resolve_keepsLastSeenDate_forSingleDayRollback() {
        val observedDate = LocalDate.of(2026, 3, 7)
        val lastSeenDate = LocalDate.of(2026, 3, 8)

        val effectiveDate = ObservedDatePolicy.resolve(lastSeenDate, observedDate)

        assertEquals(lastSeenDate, effectiveDate)
        assertFalse(ObservedDatePolicy.shouldPersist(lastSeenDate, observedDate))
    }

    @Test
    fun resolve_recoversWhenRollbackExceedsTolerance() {
        val observedDate = LocalDate.of(2026, 3, 8)
        val lastSeenDate = LocalDate.of(2026, 3, 10)

        val effectiveDate = ObservedDatePolicy.resolve(lastSeenDate, observedDate)

        assertEquals(observedDate, effectiveDate)
        assertTrue(ObservedDatePolicy.shouldPersist(lastSeenDate, observedDate))
    }

    @Test
    fun shouldPersist_acceptsForwardProgress() {
        val observedDate = LocalDate.of(2026, 3, 9)
        val lastSeenDate = LocalDate.of(2026, 3, 8)

        assertEquals(observedDate, ObservedDatePolicy.resolve(lastSeenDate, observedDate))
        assertTrue(ObservedDatePolicy.shouldPersist(lastSeenDate, observedDate))
    }
}
