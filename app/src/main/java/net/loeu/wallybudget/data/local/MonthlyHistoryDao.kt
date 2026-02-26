package net.loeu.wallybudget.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.loeu.wallybudget.data.model.MonthlyHistory

@Dao
interface MonthlyHistoryDao {
    @Insert
    suspend fun insert(history: MonthlyHistory)

    @Query("SELECT * FROM monthly_history ORDER BY endTimestamp DESC")
    fun getAllHistory(): Flow<List<MonthlyHistory>>

    @Query("SELECT * FROM monthly_history WHERE year = :year AND month = :month")
    suspend fun getHistoryForMonth(year: Int, month: Int): MonthlyHistory?

    @Query("SELECT SUM(surplus) FROM monthly_history")
    suspend fun getCumulativeSavings(): Double?
}

