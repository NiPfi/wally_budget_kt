package net.loeu.wallybudget.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Historical record of monthly budget cycles
 */
@Entity(tableName = "monthly_history")
data class MonthlyHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val year: Int,
    val month: Int, // 1-12
    val budgetAmount: Double,
    val totalSpent: Double,
    val surplus: Double, // positive if under budget, negative if over
    val endTimestamp: Long // When this cycle ended
)

