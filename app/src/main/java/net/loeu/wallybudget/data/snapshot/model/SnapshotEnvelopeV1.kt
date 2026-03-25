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
    val expenses: List<SnapshotExpenseRecordV1>,
    val budgetBuckets: List<SnapshotBudgetBucketRecordV3>? = null,
    val bucketAllocationPolicies: List<SnapshotBucketAllocationPolicyRecordV3>? = null,
    val bucketAllocationAdjustments: List<SnapshotBucketAllocationAdjustmentRecordV3>? = null,
    val bucketTransfers: List<SnapshotBucketTransferRecordV1>? = null,
    val funds: List<SnapshotFundRecordV5>? = null,
    val fundTransactions: List<SnapshotFundTransactionRecordV5>? = null
)

data class SnapshotSettingsRecordV1(
    val recordUuid: String,
    val defaultMonthlyBudgetCents: Long,
    val portfolioMonthlyBudgetCents: Long? = null,
    val legacyDefaultBucketBudgetCents: Long? = null,
    val paydayDate: Int,
    val primaryBucketUuid: String? = null,
    val selectedBucketUuid: String? = null,
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
    val bucketUuid: String? = null,
    val icon: String?,
    val originInstallId: String,
    val lastModifiedByInstallId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val deletedAtEpochMs: Long?,
    val modClock: String
)

data class SnapshotBudgetBucketRecordV3(
    val bucketUuid: String,
    val name: String,
    val trackingMode: String,
    val balanceBehavior: String,
    val defaultAllocatedAmountCents: Long,
    val sortOrder: Int,
    val isPrimary: Boolean? = null,
    val originInstallId: String,
    val lastModifiedByInstallId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val settledCloseCycleEndDateExclusive: String? = null,
    val closedAtEpochMs: Long?,
    val deletedAtEpochMs: Long?,
    val modClock: String
)

data class SnapshotBucketAllocationPolicyRecordV3(
    val allocationUuid: String,
    val bucketUuid: String,
    val cycleStartDate: String,
    val cycleEndDateExclusive: String,
    val allocatedAmountCents: Long,
    val originInstallId: String,
    val lastModifiedByInstallId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val deletedAtEpochMs: Long?,
    val modClock: String
)

data class SnapshotBucketAllocationAdjustmentRecordV3(
    val adjustmentUuid: String,
    val bucketUuid: String,
    val cycleStartDate: String,
    val effectiveDate: String,
    val previousAllocatedAmountCents: Long,
    val newAllocatedAmountCents: Long,
    val originInstallId: String,
    val lastModifiedByInstallId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val deletedAtEpochMs: Long?,
    val modClock: String
)

data class SnapshotBucketTransferRecordV1(
    val transferUuid: String,
    val fromBucketUuid: String?,
    val toBucketUuid: String?,
    val amountCents: Long,
    val reason: String,
    val cycleStartDate: String,
    val cycleEndDateExclusive: String,
    val effectiveDate: String,
    val originInstallId: String,
    val lastModifiedByInstallId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val deletedAtEpochMs: Long?,
    val modClock: String
)

data class SnapshotFundRecordV5(
    val uuid: String,
    val name: String,
    val balanceCents: Long,
    val allocationPerCycleCents: Long,
    val targetAmountCents: Long?,
    val sortOrder: Int,
    val originInstallId: String,
    val lastModifiedByInstallId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val closedAtEpochMs: Long?,
    val deletedAtEpochMs: Long?,
    val modClock: String
)

data class SnapshotFundTransactionRecordV5(
    val uuid: String,
    val fundUuid: String,
    val amountCents: Long,
    val type: String,
    val description: String,
    val dateEpochMs: Long
)
