package net.loeu.wallybudget.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

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
    val icon: ExpenseIcon? = null
)

/**
 * Optional icons for expenses using Material Icons
 */
enum class ExpenseIcon {
    SHOPPING,      // Shopping cart
    RESTAURANT,    // Restaurant/food
    TRANSPORT,     // Car/transit
    ENTERTAINMENT, // Entertainment
    HOME,          // Home/utilities
    HEALTH,        // Health/medical
    OTHER          // Miscellaneous
}

