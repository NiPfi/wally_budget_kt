@file:Suppress("CyclomaticComplexMethod")

package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.first
import net.loeu.wallybudget.data.local.dao.BudgetAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationPolicyDao
import net.loeu.wallybudget.data.local.db.TransactionRunner
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.data.time.CurrentDateProvider
import net.loeu.wallybudget.domain.usecase.internal.restoreAdjustment
import net.loeu.wallybudget.domain.usecase.internal.restoreBucketAdjustment
import net.loeu.wallybudget.domain.usecase.internal.restoreBucketPolicy
import net.loeu.wallybudget.domain.usecase.internal.restorePolicy
import net.loeu.wallybudget.domain.usecase.internal.simpleTombstoneAdjustment
import net.loeu.wallybudget.domain.usecase.internal.simpleTombstoneBucketAdjustment
import net.loeu.wallybudget.domain.usecase.internal.simpleTombstoneBucketPolicy
import net.loeu.wallybudget.domain.usecase.internal.simpleTombstonePolicy
import java.time.LocalDate

data class UndoPaydayChangeResult(
    val summaryMessage: String
)

class UndoPaydayChangeUseCase(
    private val transactionRunner: TransactionRunner,
    private val userSettingsStore: UserSettingsStore,
    private val budgetPolicyDao: BudgetPolicyDao,
    private val budgetAdjustmentDao: BudgetAdjustmentDao,
    private val bucketAllocationPolicyDao: BucketAllocationPolicyDao,
    private val bucketAllocationAdjustmentDao: BucketAllocationAdjustmentDao,
    private val currentDateProvider: CurrentDateProvider
) {
    private val syncObservedDateUseCase = SyncObservedDateUseCase(userSettingsStore)

    suspend operator fun invoke(): UndoPaydayChangeResult {
        val settings = userSettingsStore.ensureIdentity()
        val today = syncObservedDateUseCase(settings, currentDateProvider.currentDate())
        val pendingUndo = userSettingsStore.pendingPaydayUndo.first()
        val earlyResult = when {
            pendingUndo == null -> UndoPaydayChangeResult("No payday change to undo.")
            !today.isBefore(pendingUndo.expiresAtExclusiveDate()) -> {
                userSettingsStore.clearPendingPaydayUndo()
                UndoPaydayChangeResult("Payday change undo expired.")
            }
            else -> null
        }
        if (earlyResult != null) {
            return earlyResult
        }

        pendingUndo ?: error("pendingUndo checked above")
        transactionRunner.inTransaction {
            pendingUndo.policiesToDeactivate.forEach { simpleTombstonePolicy(budgetPolicyDao, it) }
            pendingUndo.policiesToRestore.forEach { restorePolicy(budgetPolicyDao, it) }
            pendingUndo.adjustmentsToDeactivate.forEach { simpleTombstoneAdjustment(budgetAdjustmentDao, it) }
            pendingUndo.adjustmentsToRestore.forEach { restoreAdjustment(budgetAdjustmentDao, it) }
            pendingUndo.bucketPoliciesToDeactivate.forEach {
                simpleTombstoneBucketPolicy(bucketAllocationPolicyDao, it)
            }
            pendingUndo.bucketPoliciesToRestore.forEach { restoreBucketPolicy(bucketAllocationPolicyDao, it) }
            pendingUndo.bucketAdjustmentsToDeactivate.forEach {
                simpleTombstoneBucketAdjustment(bucketAllocationAdjustmentDao, it)
            }
            pendingUndo.bucketAdjustmentsToRestore.forEach {
                restoreBucketAdjustment(bucketAllocationAdjustmentDao, it)
            }
        }
        userSettingsStore.restoreFromSnapshot(
            settings = pendingUndo.previousSettings,
            onboardingCompleted = pendingUndo.previousSettings.isOnboardingCompleted
        )
        userSettingsStore.clearPendingPaydayUndo()
        return UndoPaydayChangeResult("Restored the previous payday and cycle timing.")
    }
}
