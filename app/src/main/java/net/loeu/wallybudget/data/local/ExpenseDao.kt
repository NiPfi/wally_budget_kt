package net.loeu.wallybudget.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import net.loeu.wallybudget.data.model.Expense

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insert(expense: Expense): Long

    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)

    @Query("SELECT COUNT(*) FROM expenses")
    fun observeExpenseCount(): Flow<Int>

    @Query("SELECT * FROM expenses WHERE timestamp >= :startTime AND timestamp < :endTime ORDER BY timestamp DESC")
    fun getExpensesByDateRange(startTime: Long, endTime: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE timestamp >= :startTime AND timestamp < :effectiveEndTime ORDER BY timestamp DESC")
    fun getExpensesByDateRangeWithEffectiveEndTime(startTime: Long, effectiveEndTime: Long): Flow<List<Expense>>

    @Query("SELECT SUM(amountCents) FROM expenses WHERE timestamp >= :startTime AND timestamp < :endTime")
    suspend fun getTotalSpentInRange(startTime: Long, endTime: Long): Long?

    @Query("SELECT COUNT(*) FROM expenses WHERE timestamp >= :startTime AND timestamp < :endTime")
    suspend fun getExpenseCountInRange(startTime: Long, endTime: Long): Int

    @Query("SELECT * FROM expenses WHERE id = :expenseId")
    suspend fun getExpenseById(expenseId: Long): Expense?

    @Query("DELETE FROM expenses WHERE timestamp >= :startTime AND timestamp < :endTime")
    suspend fun deleteExpensesInRange(startTime: Long, endTime: Long)

    @Query("SELECT * FROM expenses WHERE timestamp >= :sinceTimestamp ORDER BY timestamp ASC")
    fun getExpensesSince(sinceTimestamp: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun getAllExpensesOrderedByTimestampDesc(): Flow<List<Expense>>
}
