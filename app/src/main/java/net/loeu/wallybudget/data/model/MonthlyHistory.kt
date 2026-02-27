package net.loeu.wallybudget.data.model

import androidx.room.Entity
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Historical record of monthly budget cycles
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
    val cycleEndDate: String, // ISO date format (YYYY-MM-DD)
    val endTimestamp: Long // When this cycle ended
) {
    /**
     * Parse cycleStartDate to LocalDate
     *
     * @throws IllegalStateException if cycleStartDate is not a valid ISO-8601 date
     */
    fun getCycleStart(): LocalDate {
        return try {
            LocalDate.parse(cycleStartDate)
        } catch (exception: DateTimeParseException) {
            throw IllegalStateException("Invalid cycleStartDate: '$cycleStartDate'", exception)
        }
    }

    /**
     * Parse cycleEndDate to LocalDate
     *
     * @throws IllegalStateException if cycleEndDate is not a valid ISO-8601 date
     */
    fun getCycleEnd(): LocalDate {
        return try {
            LocalDate.parse(cycleEndDate)
        } catch (exception: DateTimeParseException) {
            throw IllegalStateException("Invalid cycleEndDate: '$cycleEndDate'", exception)
        }
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

