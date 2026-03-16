package net.loeu.wallybudget.data.snapshot.model

data class SnapshotEnvelopeV1(
    val format: String,
    val schemaVersion: Int,
    val snapshotId: String,
    val baseSnapshotId: String?,
    val exportedAtEpochMs: Long,
    val writerInstallId: String,
    val snapshotModClock: String,
    val appVersionName: String,
    val settings: SnapshotSettingsRecordV1,
    val budgetPolicies: List<SnapshotBudgetPolicyRecordV1>,
    val budgetAdjustments: List<SnapshotBudgetAdjustmentRecordV2>? = null,
    val expenses: List<SnapshotExpenseRecordV1>
)

data class SnapshotSettingsRecordV1(
    val recordUuid: String,
    val defaultMonthlyBudgetCents: Long,
    val paydayDate: Int,
    val lastResetTimestamp: Long,
    val pendingCycleStartDate: String?,
    val pendingCycleEndDateExclusive: String?,
    val pendingCycleDetectedAtTimestamp: Long,
    val updatedAtEpochMs: Long,
    val modClock: String,
    val lastModifiedByInstallId: String
)

data class SnapshotBudgetPolicyRecordV1(
    val policyUuid: String,
    val cycleStartDate: String,
    val cycleEndDateExclusive: String,
    val budgetAmountCents: Long,
    val paydayDayOfMonth: Int,
    val originInstallId: String,
    val lastModifiedByInstallId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val deletedAtEpochMs: Long?,
    val modClock: String
)

data class SnapshotBudgetAdjustmentRecordV2(
    val adjustmentUuid: String,
    val cycleStartDate: String,
    val effectiveDate: String,
    val previousMonthlyBudgetCents: Long,
    val newMonthlyBudgetCents: Long,
    val originInstallId: String,
    val lastModifiedByInstallId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val deletedAtEpochMs: Long?,
    val modClock: String
)

data class SnapshotExpenseRecordV1(
    val recordUuid: String,
    val amountCents: Long,
    val description: String,
    val timestampEpochMs: Long,
    val expenseDate: String,
    val icon: String?,
    val originInstallId: String,
    val lastModifiedByInstallId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val deletedAtEpochMs: Long?,
    val modClock: String
)
