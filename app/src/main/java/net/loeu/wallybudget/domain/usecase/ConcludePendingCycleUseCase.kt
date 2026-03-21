package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.dao.BudgetAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.dao.MonthlyHistoryDao
import net.loeu.wallybudget.data.local.db.TransactionRunner
import net.loeu.wallybudget.data.local.entity.toDomainModel as adjustmentToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel as policyToDomainModel
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BudgetAdjustmentResolver
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import net.loeu.wallybudget.domain.usecase.internal.archiveCycleIfNeeded
import net.loeu.wallybudget.domain.usecase.internal.pendingCycleRangeOrNull

class ConcludePendingCycleUseCase(
    private val transactionRunner: TransactionRunner,
    private val expenseDao: ExpenseDao,
    private val budgetPolicyDao: BudgetPolicyDao,
    private val budgetAdjustmentDao: BudgetAdjustmentDao,
    private val monthlyHistoryDao: MonthlyHistoryDao,
    private val userSettingsStore: UserSettingsStore,
    private val budgetCalculationService: BudgetCalculationService,
    private val cycleScheduleResolver: CycleScheduleResolver,
    private val budgetAdjustmentResolver: BudgetAdjustmentResolver,
    private val rebuildBucketMonthlyHistoryUseCase: RebuildBucketMonthlyHistoryUseCase
) {
    suspend operator fun invoke(settings: UserSettings) {
        val pendingCycle = settings.pendingCycleRangeOrNull() ?: return
        transactionRunner.inTransaction {
            val policies = budgetPolicyDao.getAllForSnapshot()
                .filter { it.deletedAtEpochMs == null }
                .map { it.policyToDomainModel() }
            val cyclePolicy = cycleScheduleResolver.policyForCycleStart(
                cycleStart = pendingCycle.start,
                settings = settings,
                policies = policies
            )
            archiveCycleIfNeeded(
                expenseDao = expenseDao,
                budgetPolicyDao = budgetPolicyDao,
                monthlyHistoryDao = monthlyHistoryDao,
                budgetCalculationService = budgetCalculationService,
                budgetAdjustmentResolver = budgetAdjustmentResolver,
                cyclePolicy = cyclePolicy,
                adjustments = budgetAdjustmentDao.getActiveForCycle(pendingCycle.start.toString())
                    .map { it.adjustmentToDomainModel() },
                settings = settings,
                cycleStart = pendingCycle.start,
                cycleEnd = pendingCycle.endExclusive
            )
        }
        userSettingsStore.clearPendingCycle()
        rebuildBucketMonthlyHistoryUseCase(settings, replaceExisting = true)
    }
}
