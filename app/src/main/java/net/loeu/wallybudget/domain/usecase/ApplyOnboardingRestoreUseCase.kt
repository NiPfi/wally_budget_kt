package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.dao.BudgetAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationPolicyDao
import net.loeu.wallybudget.data.local.dao.BucketCycleBaselineDao
import net.loeu.wallybudget.data.local.dao.BucketTransferDao
import net.loeu.wallybudget.data.local.dao.BucketMonthlyHistoryDao
import net.loeu.wallybudget.data.local.dao.BudgetBucketDao
import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.dao.FundDao
import net.loeu.wallybudget.data.local.dao.FundTransactionDao
import net.loeu.wallybudget.data.local.db.TransactionRunner
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.domain.model.SnapshotApplyResult
import net.loeu.wallybudget.domain.model.SnapshotError

class ApplyOnboardingRestoreUseCase(
    private val transactionRunner: TransactionRunner,
    private val expenseDao: ExpenseDao,
    private val budgetPolicyDao: BudgetPolicyDao,
    private val budgetAdjustmentDao: BudgetAdjustmentDao,
    private val budgetBucketDao: BudgetBucketDao,
    private val bucketAllocationPolicyDao: BucketAllocationPolicyDao,
    private val bucketAllocationAdjustmentDao: BucketAllocationAdjustmentDao,
    private val bucketCycleBaselineDao: BucketCycleBaselineDao,
    private val bucketTransferDao: BucketTransferDao,
    private val bucketMonthlyHistoryDao: BucketMonthlyHistoryDao,
    private val fundDao: FundDao,
    private val fundTransactionDao: FundTransactionDao,
    private val userSettingsStore: UserSettingsStore,
    private val rebuildMonthlyHistoryUseCase: RebuildMonthlyHistoryUseCase,
    private val rebuildBucketMonthlyHistoryUseCase: RebuildBucketMonthlyHistoryUseCase
) {
    suspend operator fun invoke(preparedSnapshotImport: PreparedSnapshotImport): SnapshotApplyResult {
        val currentSettings = userSettingsStore.ensureIdentity()
        if (currentSettings.isOnboardingCompleted) {
            throw SnapshotOperationException(SnapshotError.OnboardingCompletedRestoreBlocked)
        }

        transactionRunner.inTransaction {
            expenseDao.deleteAll()
            budgetPolicyDao.deleteAll()
            budgetAdjustmentDao.deleteAll()
            budgetBucketDao.deleteAll()
            bucketAllocationPolicyDao.deleteAll()
            bucketAllocationAdjustmentDao.deleteAll()
            bucketCycleBaselineDao.deleteAll()
            bucketTransferDao.deleteAll()
            bucketMonthlyHistoryDao.deleteAll()
            fundTransactionDao.deleteAll()
            fundDao.deleteAll()
            budgetPolicyDao.insert(preparedSnapshotImport.budgetPolicies)
            budgetAdjustmentDao.insert(preparedSnapshotImport.budgetAdjustments)
            budgetBucketDao.insert(preparedSnapshotImport.budgetBuckets)
            bucketAllocationPolicyDao.insert(preparedSnapshotImport.bucketAllocationPolicies)
            bucketAllocationAdjustmentDao.insert(preparedSnapshotImport.bucketAllocationAdjustments)
            bucketCycleBaselineDao.insert(preparedSnapshotImport.bucketCycleBaselines)
            bucketTransferDao.insert(preparedSnapshotImport.bucketTransfers)
            fundDao.insert(preparedSnapshotImport.funds)
            fundTransactionDao.insert(preparedSnapshotImport.fundTransactions)
            expenseDao.insert(preparedSnapshotImport.expenses)
            rebuildMonthlyHistoryUseCase(preparedSnapshotImport.settings, replaceExisting = true)
            rebuildBucketMonthlyHistoryUseCase(preparedSnapshotImport.settings, replaceExisting = true)
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
