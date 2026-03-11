package net.loeu.wallybudget.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import net.loeu.wallybudget.data.local.entity.ExpenseEntity

@Dao
interface ExpenseDao : BaseInsertDao<ExpenseEntity> {
    @Update
    suspend fun update(expense: ExpenseEntity)

    @Query(
        "SELECT * FROM expenses " +
            "WHERE deletedAtEpochMs IS NULL AND expenseDate >= :startDateInclusive AND expenseDate < :endDateExclusive " +
            "ORDER BY expenseDate DESC, timestamp DESC, id DESC"
    )
    fun observeInRange(
        startDateInclusive: String,
        endDateExclusive: String
    ): Flow<List<ExpenseEntity>>

    @Query(
        "SELECT SUM(amountCents) FROM expenses " +
            "WHERE deletedAtEpochMs IS NULL AND expenseDate >= :startDateInclusive AND expenseDate < :endDateExclusive"
    )
    suspend fun totalSpentInRange(startDateInclusive: String, endDateExclusive: String): Long?

    @Query(
        "SELECT COUNT(*) FROM expenses " +
            "WHERE deletedAtEpochMs IS NULL AND expenseDate >= :startDateInclusive AND expenseDate < :endDateExclusive"
    )
    suspend fun countInRange(startDateInclusive: String, endDateExclusive: String): Int

    @Query(
        "SELECT * FROM expenses " +
            "WHERE deletedAtEpochMs IS NULL AND expenseDate >= :sinceDateInclusive " +
            "ORDER BY expenseDate ASC, timestamp ASC, id ASC"
    )
    fun observeSince(sinceDateInclusive: String): Flow<List<ExpenseEntity>>

    @Query(
        "SELECT * FROM expenses " +
            "WHERE deletedAtEpochMs IS NULL " +
            "ORDER BY expenseDate DESC, timestamp DESC, id DESC"
    )
    fun observeAllOrderedDesc(): Flow<List<ExpenseEntity>>

    @Query("SELECT MAX(expenseDate) FROM expenses WHERE deletedAtEpochMs IS NULL")
    suspend fun findLatestExpenseDate(): String?

    @Query("SELECT MAX(expenseDate) FROM expenses WHERE deletedAtEpochMs IS NULL")
    fun observeLatestExpenseDate(): Flow<String?>

    @Query("SELECT * FROM expenses ORDER BY expenseDate DESC, timestamp DESC, id DESC")
    suspend fun getAllForSnapshot(): List<ExpenseEntity>

    @Query("SELECT COUNT(*) FROM expenses")
    suspend fun countAll(): Int

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()

    @Query("SELECT * FROM expenses WHERE recordUuid = :recordUuid LIMIT 1")
    suspend fun findByRecordUuid(recordUuid: String): ExpenseEntity?
}
