package net.loeu.wallybudget.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.loeu.wallybudget.data.local.entity.BucketTransferEntity

@Dao
interface BucketTransferDao : BaseInsertDao<BucketTransferEntity> {
    @Query(
        "SELECT * FROM bucket_transfers " +
            "WHERE deletedAtEpochMs IS NULL AND cycleStartDate = :cycleStartDate " +
            "ORDER BY effectiveDate ASC, updatedAtEpochMs ASC, transferUuid ASC"
    )
    fun observeForCycle(cycleStartDate: String): Flow<List<BucketTransferEntity>>

    @Query(
        "SELECT * FROM bucket_transfers " +
            "WHERE deletedAtEpochMs IS NULL AND cycleStartDate = :cycleStartDate " +
            "ORDER BY effectiveDate ASC, updatedAtEpochMs ASC, transferUuid ASC"
    )
    suspend fun getForCycle(cycleStartDate: String): List<BucketTransferEntity>

    @Query("SELECT * FROM bucket_transfers ORDER BY cycleStartDate ASC, effectiveDate ASC, updatedAtEpochMs ASC")
    suspend fun getAllForSnapshot(): List<BucketTransferEntity>

    @Query("DELETE FROM bucket_transfers")
    suspend fun deleteAll()
}
