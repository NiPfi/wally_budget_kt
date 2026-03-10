package net.loeu.wallybudget.data.local.entity

import androidx.room.Entity
import androidx.room.Ignore
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
@Entity(
    tableName = "monthly_history",
    primaryKeys = ["cycleStartDate"]
)
data class MonthlyHistory(
    val cycleStartDate: String, // ISO date format (YYYY-MM-DD) - unique identifier for the cycle
    val budgetAmountCents: Long,
    val totalSpentCents: Long,
    val surplusCents: Long, // positive if under budget, negative if over
    val cycleEndDate: String, // ISO date format (YYYY-MM-DD) - Exclusive (start of the next cycle)
    val endTimestamp: Long // When this cycle ended
) {
    // Prevent Room from treating the delegate as a field, since lazy properties create a 
    // hidden delegate field that Room would otherwise attempt to persist.
    @delegate:Ignore
    private val _cycleStart: LocalDate by lazy {
        try {
            LocalDate.parse(cycleStartDate)
        } catch (exception: DateTimeParseException) {
            throw IllegalStateException("Invalid cycleStartDate: '$cycleStartDate'", exception)
        }
    }

    // Prevent Room from treating the delegate as a field, since lazy properties create a 
    // hidden delegate field that Room would otherwise attempt to persist.
    @delegate:Ignore
    private val _cycleEnd: LocalDate by lazy {
        try {
            LocalDate.parse(cycleEndDate)
        } catch (exception: DateTimeParseException) {
            throw IllegalStateException("Invalid cycleEndDate: '$cycleEndDate'", exception)
        }
    }

    /**
     * Parse cycleStartDate to LocalDate (cached and thread-safe)
     *
     * @throws IllegalStateException if cycleStartDate is not a valid ISO-8601 date
     */
    fun getCycleStart(): LocalDate = _cycleStart

    /**
     * Parse cycleEndDate to LocalDate (cached and thread-safe). 
     * The date returned is the start of the next cycle (exclusive end of this cycle).
     *
     * @throws IllegalStateException if cycleEndDate is not a valid ISO-8601 date
     */
    fun getCycleEnd(): LocalDate = _cycleEnd

    /**
     * The number of days in this budget cycle.
     * Uses the exclusive cycleEndDate invariant to calculate the correct duration.
     */
    fun getDayCount(): Int {
        return ChronoUnit.DAYS.between(getCycleStart(), getCycleEnd()).toInt().coerceAtLeast(1)
    }

    /**
     * Get display name for the cycle (e.g., "Jan 15 - Feb 14, 2026")
     */
    fun getDisplayName(): String {
        val start = getCycleStart()
        val end = getCycleEnd().minusDays(1) // End is exclusive, so show the last day of the cycle

        val startMonth = start.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
        val endMonth = end.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)

        return if (start.year == end.year) {
            "$startMonth ${start.dayOfMonth} - $endMonth ${end.dayOfMonth}, ${start.year}"
        } else {
            "$startMonth ${start.dayOfMonth}, ${start.year} - $endMonth ${end.dayOfMonth}, ${end.year}"
        }
    }
}
