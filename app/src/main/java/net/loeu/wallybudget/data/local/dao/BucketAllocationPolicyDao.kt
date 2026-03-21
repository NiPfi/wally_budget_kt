package net.loeu.wallybudget.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import net.loeu.wallybudget.data.local.entity.BucketAllocationPolicyEntity

@Dao
interface BucketAllocationPolicyDao : BaseInsertDao<BucketAllocationPolicyEntity> {
    @Update
    suspend fun update(policy: BucketAllocationPolicyEntity)

    @Query(
        "SELECT * FROM bucket_allocation_policies " +
            "WHERE deletedAtEpochMs IS NULL " +
            "ORDER BY cycleStartDate ASC, updatedAtEpochMs ASC"
    )
    fun observeActivePolicies(): Flow<List<BucketAllocationPolicyEntity>>

    @Query(
        "SELECT * FROM bucket_allocation_policies " +
            "WHERE deletedAtEpochMs IS NULL AND bucketUuid = :bucketUuid AND cycleStartDate = :cycleStartDate " +
            "ORDER BY updatedAtEpochMs DESC LIMIT 1"
    )
    suspend fun findActivePolicyForCycle(bucketUuid: String, cycleStartDate: String): BucketAllocationPolicyEntity?

    @Query(
        "SELECT * FROM bucket_allocation_policies " +
            "WHERE allocationUuid = :allocationUuid " +
            "ORDER BY updatedAtEpochMs DESC LIMIT 1"
    )
    suspend fun findByAllocationUuid(allocationUuid: String): BucketAllocationPolicyEntity?

    @Query("SELECT * FROM bucket_allocation_policies ORDER BY cycleStartDate ASC, updatedAtEpochMs ASC")
    suspend fun getAllForSnapshot(): List<BucketAllocationPolicyEntity>

    @Query("SELECT COUNT(*) FROM bucket_allocation_policies")
    suspend fun countAll(): Int

    @Query("DELETE FROM bucket_allocation_policies")
    suspend fun deleteAll()
}
