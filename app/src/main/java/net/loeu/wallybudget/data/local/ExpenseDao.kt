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

    @Query(
        "SELECT * FROM expenses " +
            "WHERE expenseDate >= :startDateInclusive AND expenseDate < :endDateExclusive " +
            "ORDER BY expenseDate DESC, timestamp DESC, id DESC"
    )
    fun getExpensesByDateRange(
        startDateInclusive: String,
        endDateExclusive: String
    ): Flow<List<Expense>>

    @Query(
        "SELECT * FROM expenses " +
            "WHERE expenseDate >= :startDateInclusive AND expenseDate < :effectiveEndDateExclusive " +
            "ORDER BY expenseDate DESC, timestamp DESC, id DESC"
    )
    fun getExpensesByDateRangeWithEffectiveEndTime(
        startDateInclusive: String,
        effectiveEndDateExclusive: String
    ): Flow<List<Expense>>

    @Query("SELECT SUM(amountCents) FROM expenses WHERE expenseDate >= :startDateInclusive AND expenseDate < :endDateExclusive")
    suspend fun getTotalSpentInRange(startDateInclusive: String, endDateExclusive: String): Long?

    @Query("SELECT COUNT(*) FROM expenses WHERE expenseDate >= :startDateInclusive AND expenseDate < :endDateExclusive")
    suspend fun getExpenseCountInRange(startDateInclusive: String, endDateExclusive: String): Int

    @Query("SELECT * FROM expenses WHERE id = :expenseId")
    suspend fun getExpenseById(expenseId: Long): Expense?

    @Query("DELETE FROM expenses WHERE expenseDate >= :startDateInclusive AND expenseDate < :endDateExclusive")
    suspend fun deleteExpensesInRange(startDateInclusive: String, endDateExclusive: String)

    @Query(
        "SELECT * FROM expenses " +
            "WHERE expenseDate >= :sinceDateInclusive " +
            "ORDER BY expenseDate ASC, timestamp ASC, id ASC"
    )
    fun getExpensesSince(sinceDateInclusive: String): Flow<List<Expense>>

    @Query("SELECT * FROM expenses ORDER BY expenseDate DESC, timestamp DESC, id DESC")
    fun getAllExpensesOrderedByTimestampDesc(): Flow<List<Expense>>

    @Query("SELECT MAX(expenseDate) FROM expenses")
    fun observeLatestExpenseDate(): Flow<String?>
}
