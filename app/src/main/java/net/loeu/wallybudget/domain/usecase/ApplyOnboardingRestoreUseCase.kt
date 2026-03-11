package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.db.TransactionRunner
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.domain.model.SnapshotApplyResult
import net.loeu.wallybudget.domain.model.SnapshotError

class ApplyOnboardingRestoreUseCase(
    private val transactionRunner: TransactionRunner,
    private val expenseDao: ExpenseDao,
    private val budgetPolicyDao: BudgetPolicyDao,
    private val userSettingsStore: UserSettingsStore,
    private val rebuildMonthlyHistoryUseCase: RebuildMonthlyHistoryUseCase
) {
    suspend operator fun invoke(preparedSnapshotImport: PreparedSnapshotImport): SnapshotApplyResult {
        val currentSettings = userSettingsStore.ensureIdentity()
        if (currentSettings.isOnboardingCompleted || expenseDao.countAll() > 0 || budgetPolicyDao.countAll() > 0) {
            throw SnapshotOperationException(SnapshotError.NonEmptyProfileRestoreBlocked)
        }

        transactionRunner.inTransaction {
            expenseDao.deleteAll()
            budgetPolicyDao.deleteAll()
            budgetPolicyDao.insert(preparedSnapshotImport.budgetPolicies)
            expenseDao.insert(preparedSnapshotImport.expenses)
            rebuildMonthlyHistoryUseCase(preparedSnapshotImport.settings)
        }
        userSettingsStore.restoreFromSnapshot(
            settings = preparedSnapshotImport.settings,
            onboardingCompleted = true
        )
        return SnapshotApplyResult(
            importedExpenseCount = preparedSnapshotImport.expenses.size,
            importedBudgetPolicyCount = preparedSnapshotImport.budgetPolicies.size
        )
    }
}
