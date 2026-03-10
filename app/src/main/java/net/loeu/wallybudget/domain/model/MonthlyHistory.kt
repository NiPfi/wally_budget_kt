package net.loeu.wallybudget.domain.model

import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * Historical record of monthly budget cycles.
 *
 * INVARIANT: cycleEndDate is EXCLUSIVE (it is the start date of the next cycle).
 * This ensures that ChronoUnit.DAYS.between(cycleStart, cycleEnd) correctly calculates
 * the total number of days in the cycle.
 */
data class MonthlyHistory(
    val cycleStartDate: String,
    val budgetAmountCents: Long,
    val totalSpentCents: Long,
    val surplusCents: Long,
    val cycleEndDate: String,
    val endTimestamp: Long
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
            LocalDate.parse(cycleEndDate)
        } catch (exception: DateTimeParseException) {
            throw IllegalStateException("Invalid cycleEndDate: '$cycleEndDate'", exception)
        }
    }

    fun getCycleStart(): LocalDate = parsedCycleStart

    fun getCycleEnd(): LocalDate = parsedCycleEnd

    fun getDayCount(): Int {
        return ChronoUnit.DAYS.between(getCycleStart(), getCycleEnd()).toInt().coerceAtLeast(1)
    }

    fun getDisplayName(): String {
        val start = getCycleStart()
        val end = getCycleEnd().minusDays(1)

        val startMonth = start.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
        val endMonth = end.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)

        return if (start.year == end.year) {
            "$startMonth ${start.dayOfMonth} - $endMonth ${end.dayOfMonth}, ${start.year}"
        } else {
            "$startMonth ${start.dayOfMonth}, ${start.year} - $endMonth ${end.dayOfMonth}, ${end.year}"
        }
    }
}
