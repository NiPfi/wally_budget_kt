package net.loeu.wallybudget.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.loeu.wallybudget.data.model.MonthlyHistory

@Dao
interface MonthlyHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: MonthlyHistory)

    @Query("SELECT * FROM monthly_history ORDER BY endTimestamp DESC")
    fun getAllHistory(): Flow<List<MonthlyHistory>>

    @Query("SELECT * FROM monthly_history WHERE cycleStartDate = :cycleStartDate")
    suspend fun getHistoryForCycle(cycleStartDate: String): MonthlyHistory?


    @Query("SELECT SUM(surplusCents) FROM monthly_history")
    suspend fun getCumulativeSavings(): Long?
}

