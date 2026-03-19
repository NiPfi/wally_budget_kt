@file:Suppress("CyclomaticComplexMethod")

package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.first
import net.loeu.wallybudget.data.local.dao.BudgetAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BudgetBucketDao
import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationPolicyDao
import net.loeu.wallybudget.data.local.db.TransactionRunner
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.data.time.CurrentDateProvider
import net.loeu.wallybudget.domain.model.BudgetAdjustment
import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.BudgetPolicy
import net.loeu.wallybudget.domain.model.BucketAllocationAdjustment
import net.loeu.wallybudget.domain.model.BucketAllocationPolicy
import java.time.LocalDate

data class UndoBudgetSettingsChangeResult(
    val summaryMessage: String
)

class UndoBudgetSettingsChangeUseCase(
    private val transactionRunner: TransactionRunner,
    private val userSettingsStore: UserSettingsStore,
    private val budgetPolicyDao: BudgetPolicyDao,
    private val budgetAdjustmentDao: BudgetAdjustmentDao,
    private val budgetBucketDao: BudgetBucketDao,
    private val bucketAllocationPolicyDao: BucketAllocationPolicyDao,
    private val bucketAllocationAdjustmentDao: BucketAllocationAdjustmentDao,
    private val currentDateProvider: CurrentDateProvider
) {
    private val syncObservedDateUseCase = SyncObservedDateUseCase(userSettingsStore)

    suspend operator fun invoke(): UndoBudgetSettingsChangeResult {
        val settings = userSettingsStore.ensureIdentity()
        val today = syncObservedDateUseCase(settings, currentDateProvider.currentDate())
        val pendingUndo = userSettingsStore.pendingSettingsUndo.first()
        val earlyResult = when {
            pendingUndo == null -> UndoBudgetSettingsChangeResult("No cycle default to restore.")
            !today.isBefore(pendingUndo.expiresAtExclusiveDate()) -> {
                userSettingsStore.clearPendingSettingsUndo()
                UndoBudgetSettingsChangeResult("Cycle default restore expired.")
            }
            else -> null
        }
        if (earlyResult != null) {
            return earlyResult
        }

        pendingUndo ?: error("pendingUndo checked above")
        transactionRunner.inTransaction {
            pendingUndo.policiesToDeactivate.forEach { deactivateInsertedPolicy(it) }
            pendingUndo.policiesToRestore.forEach { restorePolicy(it) }
            pendingUndo.adjustmentsToDeactivate.forEach { deactivateInsertedAdjustment(it) }
            pendingUndo.adjustmentsToRestore.forEach { restoreAdjustment(it) }
            pendingUndo.bucketsToDeactivate.forEach { deactivateInsertedBucket(it) }
            pendingUndo.bucketsToRestore.forEach { restoreBucket(it) }
            pendingUndo.bucketPoliciesToDeactivate.forEach { deactivateInsertedBucketPolicy(it) }
            pendingUndo.bucketPoliciesToRestore.forEach { restoreBucketPolicy(it) }
            pendingUndo.bucketAdjustmentsToDeactivate.forEach { deactivateInsertedBucketAdjustment(it) }
            pendingUndo.bucketAdjustmentsToRestore.forEach { restoreBucketAdjustment(it) }
        }
        userSettingsStore.restoreFromSnapshot(
            settings = pendingUndo.previousSettings,
            onboardingCompleted = pendingUndo.previousSettings.isOnboardingCompleted
        )
        userSettingsStore.clearPendingSettingsUndo()
        return UndoBudgetSettingsChangeResult("Restored this cycle's default settings.")
    }

    private suspend fun deactivateInsertedPolicy(policy: BudgetPolicy) {
        val entity = budgetPolicyDao.findByPolicyUuid(policy.policyUuid) ?: return
        if (entity.deletedAtEpochMs != null) return
        budgetPolicyDao.update(
            entity.copy(
                deletedAtEpochMs = entity.updatedAtEpochMs
            )
        )
    }

    private suspend fun restorePolicy(policy: BudgetPolicy) {
        val entity = budgetPolicyDao.findByPolicyUuid(policy.policyUuid)
        if (entity == null) {
            budgetPolicyDao.insert(policy.toEntity())
        } else {
            budgetPolicyDao.update(policy.toEntity(id = entity.id))
        }
    }

    private suspend fun deactivateInsertedAdjustment(adjustment: BudgetAdjustment) {
        val entity = budgetAdjustmentDao.findByAdjustmentUuid(adjustment.adjustmentUuid) ?: return
        if (entity.deletedAtEpochMs != null) return
        budgetAdjustmentDao.update(
            entity.copy(
                deletedAtEpochMs = entity.updatedAtEpochMs
            )
        )
    }

    private suspend fun restoreAdjustment(adjustment: BudgetAdjustment) {
        val entity = budgetAdjustmentDao.findByAdjustmentUuid(adjustment.adjustmentUuid)
        if (entity == null) {
            budgetAdjustmentDao.insert(adjustment.toEntity())
        } else {
            budgetAdjustmentDao.update(adjustment.toEntity(id = entity.id))
        }
    }

    private suspend fun deactivateInsertedBucket(bucket: BudgetBucket) {
        val entity = budgetBucketDao.findByBucketUuid(bucket.bucketUuid) ?: return
        if (entity.deletedAtEpochMs != null) return
        budgetBucketDao.update(entity.copy(deletedAtEpochMs = entity.updatedAtEpochMs))
    }

    private suspend fun restoreBucket(bucket: BudgetBucket) {
        val entity = budgetBucketDao.findByBucketUuid(bucket.bucketUuid)
        if (entity == null) {
            budgetBucketDao.insert(bucket.toEntity())
        } else {
            budgetBucketDao.update(bucket.toEntity(id = entity.id))
        }
    }

    private suspend fun deactivateInsertedBucketPolicy(policy: BucketAllocationPolicy) {
        val entity = bucketAllocationPolicyDao.findByAllocationUuid(policy.allocationUuid) ?: return
        if (entity.deletedAtEpochMs != null) return
        bucketAllocationPolicyDao.update(entity.copy(deletedAtEpochMs = entity.updatedAtEpochMs))
    }

    private suspend fun restoreBucketPolicy(policy: BucketAllocationPolicy) {
        val entity = bucketAllocationPolicyDao.findByAllocationUuid(policy.allocationUuid)
        if (entity == null) {
            bucketAllocationPolicyDao.insert(policy.toEntity())
        } else {
            bucketAllocationPolicyDao.update(policy.toEntity(id = entity.id))
        }
    }

    private suspend fun deactivateInsertedBucketAdjustment(adjustment: BucketAllocationAdjustment) {
        val entity = bucketAllocationAdjustmentDao.findByAdjustmentUuid(adjustment.adjustmentUuid) ?: return
        if (entity.deletedAtEpochMs != null) return
        bucketAllocationAdjustmentDao.update(entity.copy(deletedAtEpochMs = entity.updatedAtEpochMs))
    }

    private suspend fun restoreBucketAdjustment(adjustment: BucketAllocationAdjustment) {
        val entity = bucketAllocationAdjustmentDao.findByAdjustmentUuid(adjustment.adjustmentUuid)
        if (entity == null) {
            bucketAllocationAdjustmentDao.insert(adjustment.toEntity())
        } else {
            bucketAllocationAdjustmentDao.update(adjustment.toEntity(id = entity.id))
        }
    }
}
