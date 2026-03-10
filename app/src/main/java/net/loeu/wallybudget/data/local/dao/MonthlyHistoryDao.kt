package net.loeu.wallybudget.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.loeu.wallybudget.data.local.entity.MonthlyHistoryEntity

@Dao
interface MonthlyHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: MonthlyHistoryEntity)

    @Query("SELECT * FROM monthly_history ORDER BY endTimestamp DESC")
    fun getAllHistory(): Flow<List<MonthlyHistoryEntity>>

    @Query("SELECT * FROM monthly_history WHERE cycleStartDate = :cycleStartDate")
    suspend fun getHistoryForCycle(cycleStartDate: String): MonthlyHistoryEntity?


    @Query("SELECT SUM(surplusCents) FROM monthly_history")
    suspend fun getCumulativeSavings(): Long?
}
