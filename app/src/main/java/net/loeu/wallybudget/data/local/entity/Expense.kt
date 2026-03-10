package net.loeu.wallybudget.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import net.loeu.wallybudget.domain.model.ExpenseCategory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Represents a single expense entry
 */
@Entity(
    tableName = "expenses",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["expenseDate"])
    ]
)
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amountCents: Long,
    val description: String,
    val timestamp: Long = Instant.now().toEpochMilli(),
    val expenseDate: String = Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .toString(),
    val icon: ExpenseCategory? = null
)

fun Expense.recordedDate(): LocalDate {
    return try {
        LocalDate.parse(expenseDate)
    } catch (exception: Exception) {
        throw IllegalStateException("Invalid expenseDate: '$expenseDate'", exception)
    }
}

/**
 * Groups expenses by date and sums their amounts efficiently.
 *
 * **Note:** This function is optimized for sorted lists (either ascending or descending).
 * While it works with unsorted lists, performance will be suboptimal as the cached
 * day range will be frequently invalidated.
 */
fun List<Expense>.sumByDate(): Map<LocalDate, Long> {
    if (isEmpty()) return emptyMap()

    val result = mutableMapOf<LocalDate, Long>()
    var currentExpenseDate: String? = null
    var currentLocalDate: LocalDate? = null

    for (expense in this) {
        if (currentLocalDate == null || expense.expenseDate != currentExpenseDate) {
            currentExpenseDate = expense.expenseDate
            currentLocalDate = expense.recordedDate()
        }
        result[currentLocalDate] = (result[currentLocalDate] ?: 0L) + expense.amountCents
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
fun List<Expense>.groupByDate(): Map<LocalDate, List<Expense>> {
    if (isEmpty()) return emptyMap()

    val result = mutableMapOf<LocalDate, MutableList<Expense>>()
    var currentExpenseDate: String? = null
    var currentLocalDate: LocalDate? = null

    for (expense in this) {
        if (currentLocalDate == null || expense.expenseDate != currentExpenseDate) {
            currentExpenseDate = expense.expenseDate
            currentLocalDate = expense.recordedDate()
        }
        result.getOrPut(currentLocalDate) { mutableListOf() }.add(expense)
    }
    return result
}
