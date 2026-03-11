package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.dao.MonthlyHistoryDao
import net.loeu.wallybudget.data.local.db.TransactionRunner
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.usecase.internal.archiveCycleIfNeeded
import net.loeu.wallybudget.domain.usecase.internal.pendingCycleRangeOrNull

class ConcludePendingCycleUseCase(
    private val transactionRunner: TransactionRunner,
    private val expenseDao: ExpenseDao,
    private val budgetPolicyDao: BudgetPolicyDao,
    private val monthlyHistoryDao: MonthlyHistoryDao,
    private val userSettingsStore: UserSettingsStore,
    private val budgetCalculationService: BudgetCalculationService
) {
    suspend operator fun invoke(settings: UserSettings) {
        val pendingCycle = settings.pendingCycleRangeOrNull() ?: return
        transactionRunner.inTransaction {
            archiveCycleIfNeeded(
                expenseDao = expenseDao,
                budgetPolicyDao = budgetPolicyDao,
                monthlyHistoryDao = monthlyHistoryDao,
                budgetCalculationService = budgetCalculationService,
                settings = settings,
                cycleStart = pendingCycle.start,
                cycleEnd = pendingCycle.endExclusive
            )
        }
        userSettingsStore.clearPendingCycle()
    }
}
