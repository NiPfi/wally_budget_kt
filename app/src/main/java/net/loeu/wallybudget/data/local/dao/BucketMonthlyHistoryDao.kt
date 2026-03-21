package net.loeu.wallybudget.data.local.dao

import androidx.room.Dao
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.loeu.wallybudget.data.local.entity.BucketMonthlyHistoryEntity

@Dao
interface BucketMonthlyHistoryDao : BaseInsertDao<BucketMonthlyHistoryEntity> {
    @androidx.room.Insert(onConflict = OnConflictStrategy.REPLACE)
    override suspend fun insert(entity: BucketMonthlyHistoryEntity): Long

    @Query("SELECT * FROM bucket_monthly_history ORDER BY endTimestamp DESC")
    fun observeAll(): Flow<List<BucketMonthlyHistoryEntity>>

    @Query(
        "SELECT * FROM bucket_monthly_history " +
            "WHERE bucketUuid = :bucketUuid " +
            "ORDER BY endTimestamp DESC"
    )
    fun observeForBucket(bucketUuid: String): Flow<List<BucketMonthlyHistoryEntity>>

    @Query(
        "SELECT * FROM bucket_monthly_history " +
            "WHERE bucketUuid = :bucketUuid AND cycleStartDate = :cycleStartDate"
    )
    suspend fun findByBucketAndCycleStart(bucketUuid: String, cycleStartDate: String): BucketMonthlyHistoryEntity?

    @Query("SELECT * FROM bucket_monthly_history ORDER BY endTimestamp DESC")
    suspend fun getAll(): List<BucketMonthlyHistoryEntity>

    @Query(
        "SELECT * FROM bucket_monthly_history " +
            "WHERE bucketUuid = :bucketUuid " +
            "ORDER BY endTimestamp DESC"
    )
    suspend fun getAllForBucket(bucketUuid: String): List<BucketMonthlyHistoryEntity>

    @Query("DELETE FROM bucket_monthly_history")
    suspend fun deleteAll()
}
