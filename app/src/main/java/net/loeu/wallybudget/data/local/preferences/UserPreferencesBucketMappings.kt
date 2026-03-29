package net.loeu.wallybudget.data.local.preferences

import net.loeu.wallybudget.domain.model.BucketAllocationAdjustment
import net.loeu.wallybudget.domain.model.BucketAllocationPolicy

internal fun BucketAllocationPolicy.toState(): BucketAllocationPolicyState {
    return BucketAllocationPolicyState(
        allocationUuid = allocationUuid,
        bucketUuid = bucketUuid,
        cycleStartDate = cycleStartDate,
        cycleEndDateExclusive = cycleEndDateExclusive,
        allocatedAmountCents = allocatedAmountCents,
        originInstallId = originInstallId,
        lastModifiedByInstallId = lastModifiedByInstallId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        deletedAtEpochMs = deletedAtEpochMs,
        modClock = modClock
    )
}

internal fun BucketAllocationPolicyState.toDomain(): BucketAllocationPolicy {
    return BucketAllocationPolicy(
        allocationUuid = allocationUuid,
        bucketUuid = bucketUuid,
        cycleStartDate = cycleStartDate,
        cycleEndDateExclusive = cycleEndDateExclusive,
        allocatedAmountCents = allocatedAmountCents,
        originInstallId = originInstallId,
        lastModifiedByInstallId = lastModifiedByInstallId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        deletedAtEpochMs = deletedAtEpochMs,
        modClock = modClock
    )
}

internal fun BucketAllocationAdjustment.toState(): BucketAllocationAdjustmentState {
    return BucketAllocationAdjustmentState(
        adjustmentUuid = adjustmentUuid,
        bucketUuid = bucketUuid,
        cycleStartDate = cycleStartDate,
        effectiveDate = effectiveDate,
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

internal fun BucketAllocationAdjustmentState.toDomain(): BucketAllocationAdjustment {
    return BucketAllocationAdjustment(
        adjustmentUuid = adjustmentUuid,
        bucketUuid = bucketUuid,
        cycleStartDate = cycleStartDate,
        effectiveDate = effectiveDate,
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
