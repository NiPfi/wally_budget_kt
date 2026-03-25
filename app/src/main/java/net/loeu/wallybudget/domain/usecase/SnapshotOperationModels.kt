package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.entity.BudgetPolicyEntity
import net.loeu.wallybudget.data.local.entity.BudgetAdjustmentEntity
import net.loeu.wallybudget.data.local.entity.BucketAllocationAdjustmentEntity
import net.loeu.wallybudget.data.local.entity.BucketAllocationPolicyEntity
import net.loeu.wallybudget.data.local.entity.BudgetBucketEntity
import net.loeu.wallybudget.data.local.entity.BucketCycleBaselineEntity
import net.loeu.wallybudget.data.local.entity.BucketTransferEntity
import net.loeu.wallybudget.data.local.entity.ExpenseEntity
import net.loeu.wallybudget.data.local.entity.FundEntity
import net.loeu.wallybudget.data.local.entity.FundTransactionEntity
import net.loeu.wallybudget.domain.model.SnapshotError
import net.loeu.wallybudget.domain.model.SnapshotImportPreview
import net.loeu.wallybudget.domain.model.UserSettings

data class PreparedSnapshotImport(
    val preview: SnapshotImportPreview,
    val settings: UserSettings,
    val budgetPolicies: List<BudgetPolicyEntity>,
    val budgetAdjustments: List<BudgetAdjustmentEntity>,
    val budgetBuckets: List<BudgetBucketEntity> = emptyList(),
    val bucketAllocationPolicies: List<BucketAllocationPolicyEntity> = emptyList(),
    val bucketAllocationAdjustments: List<BucketAllocationAdjustmentEntity> = emptyList(),
    val bucketCycleBaselines: List<BucketCycleBaselineEntity> = emptyList(),
    val bucketTransfers: List<BucketTransferEntity> = emptyList(),
    val funds: List<FundEntity> = emptyList(),
    val fundTransactions: List<FundTransactionEntity> = emptyList(),
    val expenses: List<ExpenseEntity>
)

class SnapshotOperationException(
    val snapshotError: SnapshotError,
    cause: Throwable? = null
) : IllegalStateException(cause)
