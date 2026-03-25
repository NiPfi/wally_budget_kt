package net.loeu.wallybudget.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.loeu.wallybudget.data.local.entity.BucketCycleBaselineEntity

@Dao
interface BucketCycleBaselineDao : BaseInsertDao<BucketCycleBaselineEntity> {
    @Query(
        "SELECT * FROM bucket_cycle_baselines " +
            "WHERE deletedAtEpochMs IS NULL AND cycleStartDate = :cycleStartDate " +
            "ORDER BY bucketUuid ASC, updatedAtEpochMs ASC"
    )
    fun observeActiveForCycle(cycleStartDate: String): Flow<List<BucketCycleBaselineEntity>>

    @Query(
        "SELECT * FROM bucket_cycle_baselines " +
            "WHERE deletedAtEpochMs IS NULL AND cycleStartDate = :cycleStartDate " +
            "ORDER BY bucketUuid ASC, updatedAtEpochMs ASC"
    )
    suspend fun getActiveForCycle(cycleStartDate: String): List<BucketCycleBaselineEntity>

    @Query(
        "SELECT * FROM bucket_cycle_baselines " +
            "WHERE deletedAtEpochMs IS NULL AND bucketUuid = :bucketUuid AND cycleStartDate = :cycleStartDate " +
            "ORDER BY updatedAtEpochMs DESC LIMIT 1"
    )
    suspend fun findActiveBaselineForCycle(bucketUuid: String, cycleStartDate: String): BucketCycleBaselineEntity?

    @Query(
        "SELECT * FROM bucket_cycle_baselines " +
            "WHERE baselineUuid = :baselineUuid " +
            "ORDER BY updatedAtEpochMs DESC LIMIT 1"
    )
    suspend fun findByBaselineUuid(baselineUuid: String): BucketCycleBaselineEntity?

    @Query("SELECT * FROM bucket_cycle_baselines ORDER BY cycleStartDate ASC, updatedAtEpochMs ASC")
    suspend fun getAllForSnapshot(): List<BucketCycleBaselineEntity>

    @Query("SELECT COUNT(*) FROM bucket_cycle_baselines")
    suspend fun countAll(): Int

    @Query("DELETE FROM bucket_cycle_baselines")
    suspend fun deleteAll()

    @androidx.room.Update
    suspend fun update(baseline: BucketCycleBaselineEntity)
}
