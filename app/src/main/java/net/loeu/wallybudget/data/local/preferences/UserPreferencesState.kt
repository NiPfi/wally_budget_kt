package net.loeu.wallybudget.data.local.preferences

import kotlinx.serialization.Serializable

@Serializable
internal data class UserPreferencesState(
    val settings: StoredUserSettingsState = StoredUserSettingsState(),
    val pendingPaydayUndo: PendingPaydayUndoState? = null
)

@Serializable
internal data class StoredUserSettingsState(
    val monthlyBudgetCents: Long = 0L,
    val portfolioMonthlyBudgetCents: Long? = null,
    val paydayDate: Int = 1,
    val lastResetTimestamp: Long = 0L,
    val lastSeenDate: String? = null,
    val isOnboardingCompleted: Boolean = false,
    val pendingCycleStartDate: String? = null,
    val pendingCycleEndDateExclusive: String? = null,
    val pendingCycleDetectedAtTimestamp: Long = 0L,
    val selectedBucketUuid: String? = null,
    val installDeviceId: String = "",
    val settingsRecordUuid: String = "",
    val settingsUpdatedAtEpochMs: Long = 0L,
    val settingsModClock: String = "",
    val settingsLastModifiedByInstallId: String = ""
)

@Serializable
internal data class PendingPaydayUndoState(
    val previousSettings: StoredUserSettingsState,
    val policiesToRestore: List<BudgetPolicyState> = emptyList(),
    val policiesToDeactivate: List<BudgetPolicyState> = emptyList(),
    val adjustmentsToRestore: List<BudgetAdjustmentState> = emptyList(),
    val adjustmentsToDeactivate: List<BudgetAdjustmentState> = emptyList(),
    val bucketPoliciesToRestore: List<BucketAllocationPolicyState> = emptyList(),
    val bucketPoliciesToDeactivate: List<BucketAllocationPolicyState> = emptyList(),
    val bucketAdjustmentsToRestore: List<BucketAllocationAdjustmentState> = emptyList(),
    val bucketAdjustmentsToDeactivate: List<BucketAllocationAdjustmentState> = emptyList(),
    val expiresAtExclusive: String
)

@Serializable
internal data class BudgetPolicyState(
    val policyUuid: String,
    val cycleStartDate: String,
    val cycleEndDateExclusive: String,
    val budgetAmountCents: Long,
    val paydayDayOfMonth: Int,
    val originInstallId: String,
    val lastModifiedByInstallId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val deletedAtEpochMs: Long? = null,
    val modClock: String
)

@Serializable
internal data class BudgetAdjustmentState(
    val adjustmentUuid: String,
    val cycleStartDate: String,
    val effectiveDate: String,
    val previousMonthlyBudgetCents: Long,
    val newMonthlyBudgetCents: Long,
    val originInstallId: String,
    val lastModifiedByInstallId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val deletedAtEpochMs: Long? = null,
    val modClock: String
)

@Serializable
internal data class BucketAllocationPolicyState(
    val allocationUuid: String,
    val bucketUuid: String,
    val cycleStartDate: String,
    val cycleEndDateExclusive: String,
    val allocatedAmountCents: Long,
    val originInstallId: String,
    val lastModifiedByInstallId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val deletedAtEpochMs: Long? = null,
    val modClock: String
)

@Serializable
internal data class BucketAllocationAdjustmentState(
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
    val deletedAtEpochMs: Long? = null,
    val modClock: String
)
