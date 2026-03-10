package net.loeu.wallybudget.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.ExpenseCategory
import java.time.Instant
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
data class ExpenseEntity(
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

fun ExpenseEntity.toDomainModel(): Expense {
    return Expense(
        id = id,
        amountCents = amountCents,
        description = description,
        timestamp = timestamp,
        expenseDate = expenseDate,
        icon = icon
    )
}

fun Expense.toEntity(): ExpenseEntity {
    return ExpenseEntity(
        id = id,
        amountCents = amountCents,
        description = description,
        timestamp = timestamp,
        expenseDate = expenseDate,
        icon = icon
    )
}
