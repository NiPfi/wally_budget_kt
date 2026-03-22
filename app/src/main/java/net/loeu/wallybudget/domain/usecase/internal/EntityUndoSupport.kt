@file:Suppress("TooManyFunctions")

package net.loeu.wallybudget.domain.usecase.internal

import net.loeu.wallybudget.data.local.dao.BudgetAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationPolicyDao
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.domain.model.BudgetAdjustment
import net.loeu.wallybudget.domain.model.BudgetPolicy
import net.loeu.wallybudget.domain.model.BucketAllocationAdjustment
import net.loeu.wallybudget.domain.model.BucketAllocationPolicy
import net.loeu.wallybudget.domain.service.HybridLogicalClockService

// region HLC-aware deactivation (UpdateBudgetSettingsUseCase, UpdatePaydayUseCase)

internal suspend fun deactivateInsertedPolicy(
    budgetPolicyDao: BudgetPolicyDao,
    policy: BudgetPolicy,
    installDeviceId: String,
    nowEpochMs: Long,
    hybridLogicalClockService: HybridLogicalClockService
) {
    val entity = budgetPolicyDao.findByPolicyUuid(policy.policyUuid) ?: return
    if (entity.deletedAtEpochMs != null) return
    val tombstoneEpochMs = maxOf(nowEpochMs, entity.updatedAtEpochMs + 1L)
    budgetPolicyDao.update(
        entity.copy(
            deletedAtEpochMs = tombstoneEpochMs,
            updatedAtEpochMs = tombstoneEpochMs,
            lastModifiedByInstallId = installDeviceId,
            modClock = hybridLogicalClockService.next(
                previousClock = entity.modClock,
                nowEpochMs = tombstoneEpochMs,
                installId = installDeviceId
            )
        )
    )
}

internal suspend fun deactivateInsertedAdjustment(
    budgetAdjustmentDao: BudgetAdjustmentDao,
    adjustment: BudgetAdjustment,
    installDeviceId: String,
    nowEpochMs: Long,
    hybridLogicalClockService: HybridLogicalClockService
) {
    val entity = budgetAdjustmentDao.findByAdjustmentUuid(adjustment.adjustmentUuid) ?: return
    if (entity.deletedAtEpochMs != null) return
    val tombstoneEpochMs = maxOf(nowEpochMs, entity.updatedAtEpochMs + 1L)
    budgetAdjustmentDao.update(
        entity.copy(
            deletedAtEpochMs = tombstoneEpochMs,
            updatedAtEpochMs = tombstoneEpochMs,
            lastModifiedByInstallId = installDeviceId,
            modClock = hybridLogicalClockService.next(
                previousClock = entity.modClock,
                nowEpochMs = tombstoneEpochMs,
                installId = installDeviceId
            )
        )
    )
}

internal suspend fun deactivateInsertedBucketPolicy(
    bucketAllocationPolicyDao: BucketAllocationPolicyDao,
    policy: BucketAllocationPolicy,
    installDeviceId: String,
    nowEpochMs: Long,
    hybridLogicalClockService: HybridLogicalClockService
) {
    val entity = bucketAllocationPolicyDao.findByAllocationUuid(policy.allocationUuid) ?: return
    if (entity.deletedAtEpochMs != null) return
    val tombstoneEpochMs = maxOf(nowEpochMs, entity.updatedAtEpochMs + 1L)
    bucketAllocationPolicyDao.update(
        entity.copy(
            deletedAtEpochMs = tombstoneEpochMs,
            updatedAtEpochMs = tombstoneEpochMs,
            lastModifiedByInstallId = installDeviceId,
            modClock = hybridLogicalClockService.next(
                previousClock = entity.modClock,
                nowEpochMs = tombstoneEpochMs,
                installId = installDeviceId
            )
        )
    )
}

internal suspend fun deactivateInsertedBucketAdjustment(
    bucketAllocationAdjustmentDao: BucketAllocationAdjustmentDao,
    adjustment: BucketAllocationAdjustment,
    installDeviceId: String,
    nowEpochMs: Long,
    hybridLogicalClockService: HybridLogicalClockService
) {
    val entity = bucketAllocationAdjustmentDao.findByAdjustmentUuid(adjustment.adjustmentUuid) ?: return
    if (entity.deletedAtEpochMs != null) return
    val tombstoneEpochMs = maxOf(nowEpochMs, entity.updatedAtEpochMs + 1L)
    bucketAllocationAdjustmentDao.update(
        entity.copy(
            deletedAtEpochMs = tombstoneEpochMs,
            updatedAtEpochMs = tombstoneEpochMs,
            lastModifiedByInstallId = installDeviceId,
            modClock = hybridLogicalClockService.next(
                previousClock = entity.modClock,
                nowEpochMs = tombstoneEpochMs,
                installId = installDeviceId
            )
        )
    )
}

// endregion

// region Simple deactivation (UndoPaydayChangeUseCase — no HLC, tombstone = updatedAt)

internal suspend fun simpleTombstonePolicy(
    budgetPolicyDao: BudgetPolicyDao,
    policy: BudgetPolicy
) {
    val entity = budgetPolicyDao.findByPolicyUuid(policy.policyUuid) ?: return
    if (entity.deletedAtEpochMs != null) return
    budgetPolicyDao.update(entity.copy(deletedAtEpochMs = entity.updatedAtEpochMs))
}

internal suspend fun simpleTombstoneAdjustment(
    budgetAdjustmentDao: BudgetAdjustmentDao,
    adjustment: BudgetAdjustment
) {
    val entity = budgetAdjustmentDao.findByAdjustmentUuid(adjustment.adjustmentUuid) ?: return
    if (entity.deletedAtEpochMs != null) return
    budgetAdjustmentDao.update(entity.copy(deletedAtEpochMs = entity.updatedAtEpochMs))
}

internal suspend fun simpleTombstoneBucketPolicy(
    bucketAllocationPolicyDao: BucketAllocationPolicyDao,
    policy: BucketAllocationPolicy
) {
    val entity = bucketAllocationPolicyDao.findByAllocationUuid(policy.allocationUuid) ?: return
    if (entity.deletedAtEpochMs != null) return
    bucketAllocationPolicyDao.update(entity.copy(deletedAtEpochMs = entity.updatedAtEpochMs))
}

internal suspend fun simpleTombstoneBucketAdjustment(
    bucketAllocationAdjustmentDao: BucketAllocationAdjustmentDao,
    adjustment: BucketAllocationAdjustment
) {
    val entity = bucketAllocationAdjustmentDao.findByAdjustmentUuid(adjustment.adjustmentUuid) ?: return
    if (entity.deletedAtEpochMs != null) return
    bucketAllocationAdjustmentDao.update(entity.copy(deletedAtEpochMs = entity.updatedAtEpochMs))
}

// endregion

// region Restore (upsert — identical across all use cases)

internal suspend fun restorePolicy(
    budgetPolicyDao: BudgetPolicyDao,
    policy: BudgetPolicy
) {
    val entity = budgetPolicyDao.findByPolicyUuid(policy.policyUuid)
    if (entity == null) {
        budgetPolicyDao.insert(policy.toEntity())
    } else {
        budgetPolicyDao.update(policy.toEntity(id = entity.id))
    }
}

internal suspend fun restoreAdjustment(
    budgetAdjustmentDao: BudgetAdjustmentDao,
    adjustment: BudgetAdjustment
) {
    val entity = budgetAdjustmentDao.findByAdjustmentUuid(adjustment.adjustmentUuid)
    if (entity == null) {
        budgetAdjustmentDao.insert(adjustment.toEntity())
    } else {
        budgetAdjustmentDao.update(adjustment.toEntity(id = entity.id))
    }
}

internal suspend fun restoreBucketPolicy(
    bucketAllocationPolicyDao: BucketAllocationPolicyDao,
    policy: BucketAllocationPolicy
) {
    val entity = bucketAllocationPolicyDao.findByAllocationUuid(policy.allocationUuid)
    if (entity == null) {
        bucketAllocationPolicyDao.insert(policy.toEntity())
    } else {
        bucketAllocationPolicyDao.update(policy.toEntity(id = entity.id))
    }
}

internal suspend fun restoreBucketAdjustment(
    bucketAllocationAdjustmentDao: BucketAllocationAdjustmentDao,
    adjustment: BucketAllocationAdjustment
) {
    val entity = bucketAllocationAdjustmentDao.findByAdjustmentUuid(adjustment.adjustmentUuid)
    if (entity == null) {
        bucketAllocationAdjustmentDao.insert(adjustment.toEntity())
    } else {
        bucketAllocationAdjustmentDao.update(adjustment.toEntity(id = entity.id))
    }
}

// endregion
