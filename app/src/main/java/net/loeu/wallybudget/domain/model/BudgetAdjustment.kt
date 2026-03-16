package net.loeu.wallybudget.domain.model

import java.time.LocalDate
import java.time.format.DateTimeParseException

data class BudgetAdjustment(
    val adjustmentUuid: String,
    val cycleStartDate: String,
    val effectiveDate: String,
    val previousMonthlyBudgetCents: Long,
    val newMonthlyBudgetCents: Long,
    val originInstallId: String,
    val lastModifiedByInstallId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val deletedAtEpochMs: Long? = null,
    val modClock: String
) {
    private val parsedCycleStart: LocalDate by lazy {
        try {
            LocalDate.parse(cycleStartDate)
        } catch (exception: DateTimeParseException) {
            throw IllegalStateException("Invalid cycleStartDate: '$cycleStartDate'", exception)
        }
    }

    private val parsedEffectiveDate: LocalDate by lazy {
        try {
            LocalDate.parse(effectiveDate)
        } catch (exception: DateTimeParseException) {
            throw IllegalStateException("Invalid effectiveDate: '$effectiveDate'", exception)
        }
    }

    fun cycleStart(): LocalDate = parsedCycleStart

    fun effectiveDate(): LocalDate = parsedEffectiveDate
}
