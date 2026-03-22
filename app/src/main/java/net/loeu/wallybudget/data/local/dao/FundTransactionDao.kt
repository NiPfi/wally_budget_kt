package net.loeu.wallybudget.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import net.loeu.wallybudget.data.local.entity.FundTransactionEntity

@Dao
interface FundTransactionDao : BaseInsertDao<FundTransactionEntity> {
    @Query("SELECT * FROM fund_transactions ORDER BY dateEpochMs ASC, uuid ASC")
    suspend fun getAllForSnapshot(): List<FundTransactionEntity>

    @Query("DELETE FROM fund_transactions")
    suspend fun deleteAll()
}
