package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.entity.BudgetPolicyEntity
import net.loeu.wallybudget.data.local.entity.ExpenseEntity
import net.loeu.wallybudget.domain.model.SnapshotError
import net.loeu.wallybudget.domain.model.SnapshotImportPreview
import net.loeu.wallybudget.domain.model.UserSettings

data class PreparedSnapshotImport(
    val preview: SnapshotImportPreview,
    val settings: UserSettings,
    val budgetPolicies: List<BudgetPolicyEntity>,
    val expenses: List<ExpenseEntity>
)

class SnapshotOperationException(
    val snapshotError: SnapshotError,
    cause: Throwable? = null
) : IllegalStateException(cause)
