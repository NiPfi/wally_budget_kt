package net.loeu.wallybudget.domain.usecase.internal

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import net.loeu.wallybudget.data.local.dao.BucketAllocationAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationPolicyDao
import net.loeu.wallybudget.data.local.dao.FundDao
import net.loeu.wallybudget.data.local.dao.FundTransactionDao
import net.loeu.wallybudget.data.local.entity.BucketAllocationAdjustmentEntity
import net.loeu.wallybudget.data.local.entity.BucketAllocationPolicyEntity
import net.loeu.wallybudget.data.local.entity.FundEntity
import net.loeu.wallybudget.data.local.entity.FundTransactionEntity

internal val emptyFundDao = object : FundDao {
    override suspend fun update(fund: FundEntity) = Unit
    override fun observeAllActive(): Flow<List<FundEntity>> = flowOf(emptyList())
    override suspend fun getAllForSnapshot(): List<FundEntity> = emptyList()
    override suspend fun getAllActive(): List<FundEntity> = emptyList()
    override suspend fun findByUuid(uuid: String): FundEntity? = null
    override suspend fun deleteAll() = Unit
    override suspend fun insert(entity: FundEntity): Long = 0L
    override suspend fun insert(entities: List<FundEntity>): List<Long> = emptyList()
}

internal val emptyFundTransactionDao = object : FundTransactionDao {
    override suspend fun getAllForSnapshot(): List<FundTransactionEntity> = emptyList()
    override suspend fun deleteAll() = Unit
    override suspend fun insert(entity: FundTransactionEntity): Long = 0L
    override suspend fun insert(entities: List<FundTransactionEntity>): List<Long> = emptyList()
}

internal val emptyBucketAllocationPolicyDao = object : BucketAllocationPolicyDao {
    override suspend fun update(policy: BucketAllocationPolicyEntity) = Unit
    override fun observeActivePolicies(): Flow<List<BucketAllocationPolicyEntity>> = flowOf(emptyList())
    override suspend fun findActivePolicyForCycle(
        bucketUuid: String,
        cycleStartDate: String
    ): BucketAllocationPolicyEntity? = null
    override suspend fun findByAllocationUuid(allocationUuid: String): BucketAllocationPolicyEntity? = null
    override suspend fun getAllForSnapshot(): List<BucketAllocationPolicyEntity> = emptyList()
    override suspend fun countAll(): Int = 0
    override suspend fun deleteAll() = Unit
    override suspend fun insert(entity: BucketAllocationPolicyEntity): Long = 0L
    override suspend fun insert(entities: List<BucketAllocationPolicyEntity>): List<Long> = emptyList()
}

internal val emptyBucketAllocationAdjustmentDao = object : BucketAllocationAdjustmentDao {
    override suspend fun update(adjustment: BucketAllocationAdjustmentEntity) = Unit

    override fun observeActiveForCycle(
        bucketUuid: String,
        cycleStartDate: String
    ): Flow<List<BucketAllocationAdjustmentEntity>> = flowOf(emptyList())

    override suspend fun getActiveForCycle(
        bucketUuid: String,
        cycleStartDate: String
    ): List<BucketAllocationAdjustmentEntity> = emptyList()

    override fun observeAllActive(): Flow<List<BucketAllocationAdjustmentEntity>> = flowOf(emptyList())

    override suspend fun getAllForSnapshot(): List<BucketAllocationAdjustmentEntity> = emptyList()

    override suspend fun findByAdjustmentUuid(adjustmentUuid: String): BucketAllocationAdjustmentEntity? = null

    override suspend fun deleteByAdjustmentUuids(adjustmentUuids: List<String>) = Unit

    override suspend fun countAll(): Int = 0

    override suspend fun deleteAll() = Unit

    override suspend fun insert(entity: BucketAllocationAdjustmentEntity): Long = 0L

    override suspend fun insert(entities: List<BucketAllocationAdjustmentEntity>): List<Long> = emptyList()
}
