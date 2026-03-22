package net.loeu.wallybudget.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import net.loeu.wallybudget.data.local.entity.FundEntity

@Dao
interface FundDao : BaseInsertDao<FundEntity> {
    @Update
    suspend fun update(fund: FundEntity)

    @Query(
        "SELECT * FROM funds " +
            "WHERE deletedAtEpochMs IS NULL AND closedAtEpochMs IS NULL " +
            "ORDER BY sortOrder ASC, createdAtEpochMs ASC"
    )
    fun observeAllActive(): Flow<List<FundEntity>>

    @Query("SELECT * FROM funds ORDER BY sortOrder ASC, createdAtEpochMs ASC")
    suspend fun getAllForSnapshot(): List<FundEntity>

    @Query(
        "SELECT * FROM funds " +
            "WHERE deletedAtEpochMs IS NULL AND closedAtEpochMs IS NULL " +
            "ORDER BY sortOrder ASC, createdAtEpochMs ASC"
    )
    suspend fun getAllActive(): List<FundEntity>

    @Query("SELECT * FROM funds WHERE uuid = :uuid LIMIT 1")
    suspend fun findByUuid(uuid: String): FundEntity?

    @Query("DELETE FROM funds")
    suspend fun deleteAll()
}
