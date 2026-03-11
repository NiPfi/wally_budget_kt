@file:Suppress("MaxLineLength")

package net.loeu.wallybudget.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.loeu.wallybudget.data.local.querymodel.ExpenseDayTotalRow

@Dao
interface CycleOverviewDao {
    @Query(
        "SELECT expenseDate, SUM(amountCents) AS totalSpentCents " +
            "FROM expenses " +
            "WHERE deletedAtEpochMs IS NULL AND expenseDate >= :startDateInclusive AND expenseDate < :endDateExclusive " +
            "GROUP BY expenseDate " +
            "ORDER BY expenseDate DESC"
    )
    fun observeDayTotalsInRange(
        startDateInclusive: String,
        endDateExclusive: String
    ): Flow<List<ExpenseDayTotalRow>>
}
