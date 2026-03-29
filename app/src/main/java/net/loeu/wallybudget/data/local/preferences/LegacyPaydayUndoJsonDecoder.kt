package net.loeu.wallybudget.data.local.preferences

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonIOException
import com.google.gson.JsonParseException
import com.google.gson.JsonSyntaxException

internal class LegacyPaydayUndoJsonDecoder(
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
) {
    fun decodeOrNull(input: String): PendingPaydayUndoState? {
        return try {
            gson.fromJson(input, PendingPaydayUndoPayload::class.java)?.toState()
        } catch (_: JsonSyntaxException) {
            null
        } catch (_: JsonParseException) {
            null
        } catch (_: JsonIOException) {
            null
        } catch (_: IllegalStateException) {
            null
        }
    }
}

private data class PendingPaydayUndoPayload(
    val previousSettings: StoredUserSettingsState? = null,
    val policiesToRestore: List<BudgetPolicyPayload> = emptyList(),
    val policiesToDeactivate: List<BudgetPolicyPayload> = emptyList(),
    val adjustmentsToRestore: List<BudgetAdjustmentPayload> = emptyList(),
    val adjustmentsToDeactivate: List<BudgetAdjustmentPayload> = emptyList(),
    val bucketPoliciesToRestore: List<BucketAllocationPolicyPayload> = emptyList(),
    val bucketPoliciesToDeactivate: List<BucketAllocationPolicyPayload> = emptyList(),
    val bucketAdjustmentsToRestore: List<BucketAllocationAdjustmentPayload> = emptyList(),
    val bucketAdjustmentsToDeactivate: List<BucketAllocationAdjustmentPayload> = emptyList(),
    val expiresAtExclusive: String? = null
) {
    fun toState(): PendingPaydayUndoState {
        val previousSettingsValue = requireNotNull(previousSettings) { "Missing previousSettings" }
        val expiresAtExclusiveValue = requireNotNull(expiresAtExclusive) { "Missing expiresAtExclusive" }
        return PendingPaydayUndoState(
            previousSettings = previousSettingsValue,
            policiesToRestore = policiesToRestore.map(BudgetPolicyPayload::toState),
            policiesToDeactivate = policiesToDeactivate.map(BudgetPolicyPayload::toState),
            adjustmentsToRestore = adjustmentsToRestore.map(BudgetAdjustmentPayload::toState),
            adjustmentsToDeactivate = adjustmentsToDeactivate.map(BudgetAdjustmentPayload::toState),
            bucketPoliciesToRestore = bucketPoliciesToRestore.map(BucketAllocationPolicyPayload::toState),
            bucketPoliciesToDeactivate = bucketPoliciesToDeactivate.map(BucketAllocationPolicyPayload::toState),
            bucketAdjustmentsToRestore = bucketAdjustmentsToRestore.map(BucketAllocationAdjustmentPayload::toState),
            bucketAdjustmentsToDeactivate = bucketAdjustmentsToDeactivate.map(
                BucketAllocationAdjustmentPayload::toState
            ),
            expiresAtExclusive = expiresAtExclusiveValue
        )
    }
}

private data class BudgetPolicyPayload(
    val policyUuid: String? = null,
    val cycleStartDate: String? = null,
    val cycleEndDateExclusive: String? = null,
    val budgetAmountCents: Long = 0L,
    val paydayDayOfMonth: Int = 1,
    val originInstallId: String = "",
    val lastModifiedByInstallId: String = "",
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
    val deletedAtEpochMs: Long? = null,
    val modClock: String = ""
) {
    fun toState(): BudgetPolicyState {
        return BudgetPolicyState(
            policyUuid = requireNotNull(policyUuid) { "Missing policyUuid" },
            cycleStartDate = requireNotNull(cycleStartDate) { "Missing cycleStartDate" },
            cycleEndDateExclusive = requireNotNull(cycleEndDateExclusive) { "Missing cycleEndDateExclusive" },
            budgetAmountCents = budgetAmountCents,
            paydayDayOfMonth = paydayDayOfMonth,
            originInstallId = originInstallId,
            lastModifiedByInstallId = lastModifiedByInstallId,
            createdAtEpochMs = createdAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs,
            deletedAtEpochMs = deletedAtEpochMs,
            modClock = modClock
        )
    }
}

private data class BudgetAdjustmentPayload(
    val adjustmentUuid: String? = null,
    val cycleStartDate: String? = null,
    val effectiveDate: String? = null,
    val previousMonthlyBudgetCents: Long = 0L,
    val newMonthlyBudgetCents: Long = 0L,
    val originInstallId: String = "",
    val lastModifiedByInstallId: String = "",
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
    val deletedAtEpochMs: Long? = null,
    val modClock: String = ""
) {
    fun toState(): BudgetAdjustmentState {
        return BudgetAdjustmentState(
            adjustmentUuid = requireNotNull(adjustmentUuid) { "Missing adjustmentUuid" },
            cycleStartDate = requireNotNull(cycleStartDate) { "Missing cycleStartDate" },
            effectiveDate = requireNotNull(effectiveDate) { "Missing effectiveDate" },
            previousMonthlyBudgetCents = previousMonthlyBudgetCents,
            newMonthlyBudgetCents = newMonthlyBudgetCents,
            originInstallId = originInstallId,
            lastModifiedByInstallId = lastModifiedByInstallId,
            createdAtEpochMs = createdAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs,
            deletedAtEpochMs = deletedAtEpochMs,
            modClock = modClock
        )
    }
}

private data class BucketAllocationPolicyPayload(
    val allocationUuid: String? = null,
    val bucketUuid: String? = null,
    val cycleStartDate: String? = null,
    val cycleEndDateExclusive: String? = null,
    val allocatedAmountCents: Long = 0L,
    val originInstallId: String = "",
    val lastModifiedByInstallId: String = "",
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
    val deletedAtEpochMs: Long? = null,
    val modClock: String = ""
) {
    fun toState(): BucketAllocationPolicyState {
        return BucketAllocationPolicyState(
            allocationUuid = requireNotNull(allocationUuid) { "Missing allocationUuid" },
            bucketUuid = requireNotNull(bucketUuid) { "Missing bucketUuid" },
            cycleStartDate = requireNotNull(cycleStartDate) { "Missing cycleStartDate" },
            cycleEndDateExclusive = requireNotNull(cycleEndDateExclusive) { "Missing cycleEndDateExclusive" },
            allocatedAmountCents = allocatedAmountCents,
            originInstallId = originInstallId,
            lastModifiedByInstallId = lastModifiedByInstallId,
            createdAtEpochMs = createdAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs,
            deletedAtEpochMs = deletedAtEpochMs,
            modClock = modClock
        )
    }
}

private data class BucketAllocationAdjustmentPayload(
    val adjustmentUuid: String? = null,
    val bucketUuid: String? = null,
    val cycleStartDate: String? = null,
    val effectiveDate: String? = null,
    val previousAllocatedAmountCents: Long = 0L,
    val newAllocatedAmountCents: Long = 0L,
    val originInstallId: String = "",
    val lastModifiedByInstallId: String = "",
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
    val deletedAtEpochMs: Long? = null,
    val modClock: String = ""
) {
    fun toState(): BucketAllocationAdjustmentState {
        return BucketAllocationAdjustmentState(
            adjustmentUuid = requireNotNull(adjustmentUuid) { "Missing adjustmentUuid" },
            bucketUuid = requireNotNull(bucketUuid) { "Missing bucketUuid" },
            cycleStartDate = requireNotNull(cycleStartDate) { "Missing cycleStartDate" },
            effectiveDate = requireNotNull(effectiveDate) { "Missing effectiveDate" },
            previousAllocatedAmountCents = previousAllocatedAmountCents,
            newAllocatedAmountCents = newAllocatedAmountCents,
            originInstallId = originInstallId,
            lastModifiedByInstallId = lastModifiedByInstallId,
            createdAtEpochMs = createdAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs,
            deletedAtEpochMs = deletedAtEpochMs,
            modClock = modClock
        )
    }
}
