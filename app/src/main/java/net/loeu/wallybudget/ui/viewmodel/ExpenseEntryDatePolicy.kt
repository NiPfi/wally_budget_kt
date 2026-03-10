package net.loeu.wallybudget.ui.viewmodel

import java.time.LocalDate

internal object ExpenseEntryDatePolicy {
    fun resolveRequestedDate(requestedDate: LocalDate?, effectiveCurrentDate: LocalDate): LocalDate {
        return when {
            requestedDate == null -> effectiveCurrentDate
            requestedDate.isAfter(effectiveCurrentDate) -> effectiveCurrentDate
            else -> requestedDate
        }
    }
}
