package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.first
import net.loeu.wallybudget.data.local.dao.BudgetAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.db.TransactionRunner
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.data.time.CurrentDateProvider
import net.loeu.wallybudget.domain.model.BudgetAdjustment
import net.loeu.wallybudget.domain.model.BudgetPolicy
data class UndoBudgetSettingsChangeResult(
    val summaryMessage: String
)

class UndoBudgetSettingsChangeUseCase(
    private val transactionRunner: TransactionRunner,
    private val userSettingsStore: UserSettingsStore,
    private val budgetPolicyDao: BudgetPolicyDao,
    private val budgetAdjustmentDao: BudgetAdjustmentDao,
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
}
