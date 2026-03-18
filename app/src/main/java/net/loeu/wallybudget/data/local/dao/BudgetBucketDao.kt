package net.loeu.wallybudget.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import net.loeu.wallybudget.data.local.entity.BudgetBucketEntity

@Dao
interface BudgetBucketDao : BaseInsertDao<BudgetBucketEntity> {
    @Update
    suspend fun update(bucket: BudgetBucketEntity)

    @Query(
        "SELECT * FROM budget_buckets " +
            "WHERE deletedAtEpochMs IS NULL " +
            "ORDER BY sortOrder ASC, createdAtEpochMs ASC"
    )
    fun observeAllActive(): Flow<List<BudgetBucketEntity>>

    @Query("SELECT * FROM budget_buckets ORDER BY sortOrder ASC, createdAtEpochMs ASC")
    fun observeAll(): Flow<List<BudgetBucketEntity>>

    @Query(
        "SELECT * FROM budget_buckets " +
            "WHERE deletedAtEpochMs IS NULL " +
            "ORDER BY sortOrder ASC, createdAtEpochMs ASC"
    )
    suspend fun getAllActive(): List<BudgetBucketEntity>

    @Query("SELECT * FROM budget_buckets ORDER BY sortOrder ASC, createdAtEpochMs ASC")
    suspend fun getAllForSnapshot(): List<BudgetBucketEntity>

    @Query("SELECT * FROM budget_buckets WHERE bucketUuid = :bucketUuid ORDER BY updatedAtEpochMs DESC LIMIT 1")
    suspend fun findByBucketUuid(bucketUuid: String): BudgetBucketEntity?

    @Query("SELECT COUNT(*) FROM budget_buckets")
    suspend fun countAll(): Int

    @Query("DELETE FROM budget_buckets")
    suspend fun deleteAll()
}
