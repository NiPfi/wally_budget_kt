package net.loeu.wallybudget.domain.model

data class SnapshotImportPreview(
    val exportedAtEpochMs: Long,
    val writerInstallId: String,
    val expenseCount: Int,
    val tombstoneCount: Int,
    val budgetPolicyCount: Int,
    val defaultMonthlyBudgetCents: Long,
    val paydayDate: Int,
    val compressed: Boolean,
    val warnings: List<String> = emptyList()
)

data class SnapshotApplyResult(
    val importedExpenseCount: Int,
    val importedBudgetPolicyCount: Int
)

sealed interface SnapshotError {
    data object UnsupportedSchemaVersion : SnapshotError
    data object MalformedSnapshot : SnapshotError
    data object InvalidCompression : SnapshotError
    data object NonEmptyProfileRestoreBlocked : SnapshotError
    data class IoFailure(val message: String) : SnapshotError
}
