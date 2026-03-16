package net.loeu.wallybudget.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import net.loeu.wallybudget.data.local.entity.BudgetAdjustmentEntity

@Dao
interface BudgetAdjustmentDao : BaseInsertDao<BudgetAdjustmentEntity> {
    @Update
    suspend fun update(adjustment: BudgetAdjustmentEntity)

    @Query(
        "SELECT * FROM budget_adjustments " +
            "WHERE deletedAtEpochMs IS NULL AND cycleStartDate = :cycleStartDate " +
            "ORDER BY effectiveDate ASC, updatedAtEpochMs ASC"
    )
    fun observeActiveForCycle(cycleStartDate: String): Flow<List<BudgetAdjustmentEntity>>

    @Query(
        "SELECT * FROM budget_adjustments " +
            "WHERE deletedAtEpochMs IS NULL AND cycleStartDate = :cycleStartDate " +
            "ORDER BY effectiveDate ASC, updatedAtEpochMs ASC"
    )
    suspend fun getActiveForCycle(cycleStartDate: String): List<BudgetAdjustmentEntity>

    @Query(
        "SELECT * FROM budget_adjustments " +
            "WHERE deletedAtEpochMs IS NULL " +
            "ORDER BY cycleStartDate ASC, effectiveDate ASC, updatedAtEpochMs ASC"
    )
    fun observeAllActive(): Flow<List<BudgetAdjustmentEntity>>

    @Query(
        "SELECT * FROM budget_adjustments " +
            "ORDER BY cycleStartDate ASC, effectiveDate ASC, updatedAtEpochMs ASC"
    )
    suspend fun getAllForSnapshot(): List<BudgetAdjustmentEntity>

    @Query("SELECT COUNT(*) FROM budget_adjustments")
    suspend fun countAll(): Int

    @Query("DELETE FROM budget_adjustments")
    suspend fun deleteAll()
}
