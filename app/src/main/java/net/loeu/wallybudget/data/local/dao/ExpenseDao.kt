package net.loeu.wallybudget.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import net.loeu.wallybudget.data.local.entity.ExpenseEntity

@Dao
interface ExpenseDao : BaseInsertDao<ExpenseEntity> {
    @Update
    suspend fun update(expense: ExpenseEntity)

    @Delete
    suspend fun delete(expense: ExpenseEntity)

    @Query("SELECT COUNT(*) FROM expenses")
    fun observeCount(): Flow<Int>

    @Query(
        "SELECT * FROM expenses " +
            "WHERE expenseDate >= :startDateInclusive AND expenseDate < :endDateExclusive " +
            "ORDER BY expenseDate DESC, timestamp DESC, id DESC"
    )
    fun observeInRange(
        startDateInclusive: String,
        endDateExclusive: String
    ): Flow<List<ExpenseEntity>>

    @Query("SELECT SUM(amountCents) FROM expenses WHERE expenseDate >= :startDateInclusive AND expenseDate < :endDateExclusive")
    suspend fun totalSpentInRange(startDateInclusive: String, endDateExclusive: String): Long?

    @Query("SELECT COUNT(*) FROM expenses WHERE expenseDate >= :startDateInclusive AND expenseDate < :endDateExclusive")
    suspend fun countInRange(startDateInclusive: String, endDateExclusive: String): Int

    @Query("SELECT * FROM expenses WHERE id = :expenseId")
    suspend fun findById(expenseId: Long): ExpenseEntity?

    @Query("DELETE FROM expenses WHERE expenseDate >= :startDateInclusive AND expenseDate < :endDateExclusive")
    suspend fun deleteInRange(startDateInclusive: String, endDateExclusive: String)

    @Query(
        "SELECT * FROM expenses " +
            "WHERE expenseDate >= :sinceDateInclusive " +
            "ORDER BY expenseDate ASC, timestamp ASC, id ASC"
    )
    fun observeSince(sinceDateInclusive: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses ORDER BY expenseDate DESC, timestamp DESC, id DESC")
    fun observeAllOrderedDesc(): Flow<List<ExpenseEntity>>

    @Query("SELECT MAX(expenseDate) FROM expenses")
    fun observeLatestExpenseDate(): Flow<String?>
}
