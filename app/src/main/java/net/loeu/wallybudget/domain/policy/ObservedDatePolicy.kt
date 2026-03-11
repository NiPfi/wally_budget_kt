package net.loeu.wallybudget.domain.policy

import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal object ObservedDatePolicy {
    // Keep small backward shifts monotonic, but recover quickly from clearly bad future jumps.
    private const val MAX_BACKWARD_DATE_SKEW_DAYS = 1L

    fun resolve(lastSeenDate: LocalDate?, observedDate: LocalDate): LocalDate {
        if (lastSeenDate == null || !observedDate.isBefore(lastSeenDate)) {
            return observedDate
        }

        val rollbackDays = ChronoUnit.DAYS.between(observedDate, lastSeenDate)
        return if (rollbackDays <= MAX_BACKWARD_DATE_SKEW_DAYS) {
            lastSeenDate
        } else {
            observedDate
        }
    }

    fun shouldPersist(lastSeenDate: LocalDate?, observedDate: LocalDate): Boolean {
        return when {
            lastSeenDate == null -> true
            observedDate.isAfter(lastSeenDate) -> true
            !observedDate.isBefore(lastSeenDate) -> false
            else -> {
                val rollbackDays = ChronoUnit.DAYS.between(observedDate, lastSeenDate)
                rollbackDays > MAX_BACKWARD_DATE_SKEW_DAYS
            }
        }
    }
}
