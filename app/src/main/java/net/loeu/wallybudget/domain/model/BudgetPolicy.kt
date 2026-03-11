package net.loeu.wallybudget.domain.model

import java.time.LocalDate
import java.time.format.DateTimeParseException

data class BudgetPolicy(
    val policyUuid: String,
    val cycleStartDate: String,
    val cycleEndDateExclusive: String,
    val budgetAmountCents: Long,
    val paydayDayOfMonth: Int,
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

    private val parsedCycleEnd: LocalDate by lazy {
        try {
            LocalDate.parse(cycleEndDateExclusive)
        } catch (exception: DateTimeParseException) {
            throw IllegalStateException("Invalid cycleEndDateExclusive: '$cycleEndDateExclusive'", exception)
        }
    }

    fun cycleStart(): LocalDate = parsedCycleStart

    fun cycleEndExclusive(): LocalDate = parsedCycleEnd
}
