package net.loeu.wallybudget.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import net.loeu.wallybudget.data.local.entity.BucketAllocationAdjustmentEntity

@Dao
interface BucketAllocationAdjustmentDao : BaseInsertDao<BucketAllocationAdjustmentEntity> {
    @Update
    suspend fun update(adjustment: BucketAllocationAdjustmentEntity)

    @Query(
        "SELECT * FROM bucket_allocation_adjustments " +
            "WHERE deletedAtEpochMs IS NULL AND bucketUuid = :bucketUuid AND cycleStartDate = :cycleStartDate " +
            "ORDER BY effectiveDate ASC, updatedAtEpochMs ASC"
    )
    fun observeActiveForCycle(bucketUuid: String, cycleStartDate: String): Flow<List<BucketAllocationAdjustmentEntity>>

    @Query(
        "SELECT * FROM bucket_allocation_adjustments " +
            "WHERE deletedAtEpochMs IS NULL AND bucketUuid = :bucketUuid AND cycleStartDate = :cycleStartDate " +
            "ORDER BY effectiveDate ASC, updatedAtEpochMs ASC"
    )
    suspend fun getActiveForCycle(bucketUuid: String, cycleStartDate: String): List<BucketAllocationAdjustmentEntity>

    @Query(
        "SELECT * FROM bucket_allocation_adjustments " +
            "WHERE deletedAtEpochMs IS NULL " +
            "ORDER BY bucketUuid ASC, cycleStartDate ASC, effectiveDate ASC, updatedAtEpochMs ASC"
    )
    fun observeAllActive(): Flow<List<BucketAllocationAdjustmentEntity>>

    @Query("SELECT * FROM bucket_allocation_adjustments ORDER BY bucketUuid ASC, cycleStartDate ASC, effectiveDate ASC")
    suspend fun getAllForSnapshot(): List<BucketAllocationAdjustmentEntity>

    @Query(
        "SELECT * FROM bucket_allocation_adjustments " +
            "WHERE adjustmentUuid = :adjustmentUuid " +
            "ORDER BY updatedAtEpochMs DESC LIMIT 1"
    )
    suspend fun findByAdjustmentUuid(adjustmentUuid: String): BucketAllocationAdjustmentEntity?

    @Query("SELECT COUNT(*) FROM bucket_allocation_adjustments")
    suspend fun countAll(): Int

    @Query("DELETE FROM bucket_allocation_adjustments")
    suspend fun deleteAll()
}
