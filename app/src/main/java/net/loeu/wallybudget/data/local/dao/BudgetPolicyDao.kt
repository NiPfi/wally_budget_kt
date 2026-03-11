package net.loeu.wallybudget.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import net.loeu.wallybudget.data.local.entity.BudgetPolicyEntity

@Dao
interface BudgetPolicyDao : BaseInsertDao<BudgetPolicyEntity> {
    @Update
    suspend fun update(policy: BudgetPolicyEntity)

    @Query(
        "SELECT * FROM budget_policies " +
            "WHERE deletedAtEpochMs IS NULL " +
            "ORDER BY cycleStartDate ASC, updatedAtEpochMs ASC"
    )
    fun observeActivePolicies(): Flow<List<BudgetPolicyEntity>>

    @Query(
        "SELECT * FROM budget_policies " +
            "WHERE deletedAtEpochMs IS NULL AND cycleStartDate = :cycleStartDate " +
            "ORDER BY updatedAtEpochMs DESC LIMIT 1"
    )
    suspend fun findActivePolicyForCycle(cycleStartDate: String): BudgetPolicyEntity?

    @Query(
        "SELECT * FROM budget_policies " +
            "WHERE policyUuid = :policyUuid " +
            "ORDER BY updatedAtEpochMs DESC LIMIT 1"
    )
    suspend fun findByPolicyUuid(policyUuid: String): BudgetPolicyEntity?

    @Query(
        "SELECT * FROM budget_policies " +
            "ORDER BY cycleStartDate ASC, updatedAtEpochMs ASC"
    )
    suspend fun getAllForSnapshot(): List<BudgetPolicyEntity>

    @Query("SELECT COUNT(*) FROM budget_policies")
    suspend fun countAll(): Int

    @Query("DELETE FROM budget_policies")
    suspend fun deleteAll()
}
