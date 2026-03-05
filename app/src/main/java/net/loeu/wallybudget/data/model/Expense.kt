package net.loeu.wallybudget.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Represents a single expense entry
 */
@Entity(
    tableName = "expenses",
    indices = [
        Index(value = ["timestamp"])
    ]
)
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amountCents: Long,
    val description: String,
    val timestamp: Long = Instant.now().toEpochMilli(),
    val icon: ExpenseCategory? = null
)

/**
 * Groups expenses by date and sums their amounts efficiently.
 *
 * **Note:** This function is optimized for sorted lists (either ascending or descending).
 * While it works with unsorted lists, performance will be suboptimal as the cached
 * day range will be frequently invalidated.
 */
fun List<Expense>.sumByDate(zoneId: ZoneId = ZoneId.systemDefault()): Map<LocalDate, Long> {
    if (isEmpty()) return emptyMap()
    
    val result = mutableMapOf<LocalDate, Long>()
    var currentDayStart: Long = 0
    var nextDayStart: Long = 0
    var currentLocalDate: LocalDate? = null

    for (expense in this) {
        val ts = expense.timestamp

        // Only perform expensive conversion if timestamp falls outside current cached day range
        // or if it's the first iteration (currentLocalDate is null)
        if (currentLocalDate == null || ts < currentDayStart || ts >= nextDayStart) {
            val zdt = Instant.ofEpochMilli(ts).atZone(zoneId)
            currentLocalDate = zdt.toLocalDate()
            currentDayStart = currentLocalDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
            nextDayStart = currentLocalDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        }
        result[currentLocalDate!!] = (result[currentLocalDate] ?: 0L) + expense.amountCents
    }
    return result
}

/**
 * Groups expenses by date efficiently.
 *
 * **Note:** This function is optimized for sorted lists (either ascending or descending).
 * While it works with unsorted lists, performance will be suboptimal as the cached
 * day range will be frequently invalidated.
 */
fun List<Expense>.groupByDate(zoneId: ZoneId = ZoneId.systemDefault()): Map<LocalDate, List<Expense>> {
    if (isEmpty()) return emptyMap()
    
    val result = mutableMapOf<LocalDate, MutableList<Expense>>()
    var currentDayStart: Long = 0
    var nextDayStart: Long = 0
    var currentLocalDate: LocalDate? = null

    for (expense in this) {
        val ts = expense.timestamp

        if (currentLocalDate == null || ts < currentDayStart || ts >= nextDayStart) {
            val zdt = Instant.ofEpochMilli(ts).atZone(zoneId)
            currentLocalDate = zdt.toLocalDate()
            currentDayStart = currentLocalDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
            nextDayStart = currentLocalDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        }
        result.getOrPut(currentLocalDate!!) { mutableListOf() }.add(expense)
    }
    return result
}
