package net.loeu.wallybudget.data.local.dao

import androidx.room.Dao
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.loeu.wallybudget.data.local.entity.MonthlyHistoryEntity

@Dao
interface MonthlyHistoryDao : BaseInsertDao<MonthlyHistoryEntity> {
    @androidx.room.Insert(onConflict = OnConflictStrategy.REPLACE)
    override suspend fun insert(entity: MonthlyHistoryEntity): Long

    @Query("SELECT * FROM monthly_history ORDER BY endTimestamp DESC")
    fun observeAll(): Flow<List<MonthlyHistoryEntity>>

    @Query("SELECT * FROM monthly_history WHERE cycleStartDate = :cycleStartDate")
    suspend fun findByCycleStart(cycleStartDate: String): MonthlyHistoryEntity?

    @Query("SELECT * FROM monthly_history ORDER BY endTimestamp DESC")
    suspend fun getAll(): List<MonthlyHistoryEntity>

    @Query("DELETE FROM monthly_history")
    suspend fun deleteAll()
}
