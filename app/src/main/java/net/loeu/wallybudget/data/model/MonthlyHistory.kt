package net.loeu.wallybudget.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Historical record of monthly budget cycles
 */
@Entity(
    tableName = "monthly_history",
    indices = [Index(value = ["year", "month"], unique = true)]
)
data class MonthlyHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val year: Int,
    val month: Int, // 1-12
    val budgetAmountCents: Long,
    val totalSpentCents: Long,
    val surplusCents: Long, // positive if under budget, negative if over
    val endTimestamp: Long // When this cycle ended
)

