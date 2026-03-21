package net.loeu.wallybudget.domain.usecase.internal

import net.loeu.wallybudget.domain.model.BucketAllocationAdjustment
import net.loeu.wallybudget.domain.model.BucketAllocationPolicy
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import java.time.LocalDate
import java.util.UUID

internal fun newBucketAllocationPolicy(
    bucketUuid: String,
    cycleStart: LocalDate,
    cycleEndExclusive: LocalDate,
    allocatedAmountCents: Long,
    installId: String,
    nowEpochMs: Long,
    hybridLogicalClockService: HybridLogicalClockService
): BucketAllocationPolicy {
    return BucketAllocationPolicy(
        allocationUuid = UUID.randomUUID().toString(),
        bucketUuid = bucketUuid,
        cycleStartDate = cycleStart.toString(),
        cycleEndDateExclusive = cycleEndExclusive.toString(),
        allocatedAmountCents = allocatedAmountCents,
        originInstallId = installId,
        lastModifiedByInstallId = installId,
        createdAtEpochMs = nowEpochMs,
        updatedAtEpochMs = nowEpochMs,
        deletedAtEpochMs = null,
        modClock = hybridLogicalClockService.format(nowEpochMs, 0, installId)
    )
}

internal fun newBucketAllocationAdjustment(
    bucketUuid: String,
    cycleStart: LocalDate,
    effectiveDate: LocalDate,
    previousAllocatedAmountCents: Long,
    newAllocatedAmountCents: Long,
    installId: String,
    nowEpochMs: Long,
    hybridLogicalClockService: HybridLogicalClockService
): BucketAllocationAdjustment {
    return BucketAllocationAdjustment(
        adjustmentUuid = UUID.randomUUID().toString(),
        bucketUuid = bucketUuid,
        cycleStartDate = cycleStart.toString(),
        effectiveDate = effectiveDate.toString(),
        previousAllocatedAmountCents = previousAllocatedAmountCents,
        newAllocatedAmountCents = newAllocatedAmountCents,
        originInstallId = installId,
        lastModifiedByInstallId = installId,
        createdAtEpochMs = nowEpochMs,
        updatedAtEpochMs = nowEpochMs,
        deletedAtEpochMs = null,
        modClock = hybridLogicalClockService.format(nowEpochMs, 0, installId)
    )
}
