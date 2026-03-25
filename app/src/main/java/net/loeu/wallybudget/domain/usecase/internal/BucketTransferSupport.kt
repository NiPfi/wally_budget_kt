package net.loeu.wallybudget.domain.usecase.internal

import net.loeu.wallybudget.data.local.dao.BucketAllocationPolicyDao
import net.loeu.wallybudget.data.local.dao.BucketTransferDao
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.domain.model.BucketAllocationPolicy
import net.loeu.wallybudget.domain.model.BucketTransfer
import net.loeu.wallybudget.domain.model.BucketTransferReason
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import java.time.LocalDate
import java.util.UUID

internal suspend fun upsertCurrentCycleBucketPolicyAmount(
    bucketAllocationPolicyDao: BucketAllocationPolicyDao,
    bucketUuid: String,
    cycleStart: LocalDate,
    cycleEndExclusive: LocalDate,
    allocatedAmountCents: Long,
    installId: String,
    nowEpochMs: Long,
    hybridLogicalClockService: HybridLogicalClockService
) {
    val existing = bucketAllocationPolicyDao.findActivePolicyForCycle(
        bucketUuid = bucketUuid,
        cycleStartDate = cycleStart.toString()
    )
    if (existing == null) {
        bucketAllocationPolicyDao.insert(
            newBucketAllocationPolicy(
                bucketUuid = bucketUuid,
                cycleStart = cycleStart,
                cycleEndExclusive = cycleEndExclusive,
                allocatedAmountCents = allocatedAmountCents,
                installId = installId,
                nowEpochMs = nowEpochMs,
                hybridLogicalClockService = hybridLogicalClockService
            ).toEntity()
        )
        return
    }
    if (existing.allocatedAmountCents == allocatedAmountCents) return
    bucketAllocationPolicyDao.update(
        existing.copy(
            allocatedAmountCents = allocatedAmountCents,
            updatedAtEpochMs = nowEpochMs,
            lastModifiedByInstallId = installId,
            modClock = hybridLogicalClockService.next(
                previousClock = existing.modClock,
                nowEpochMs = nowEpochMs,
                installId = installId
            )
        )
    )
}

internal fun currentCyclePolicyAllocation(
    bucketUuid: String,
    cycleStart: LocalDate,
    bucketPolicies: List<BucketAllocationPolicy>,
    defaultAllocatedAmountCents: Long
): Long {
    return bucketPolicies
        .firstOrNull { it.deletedAtEpochMs == null && it.bucketUuid == bucketUuid && it.cycleStart() == cycleStart }
        ?.allocatedAmountCents
        ?: defaultAllocatedAmountCents
}

internal suspend fun insertBucketTransfer(
    bucketTransferDao: BucketTransferDao,
    fromBucketUuid: String?,
    toBucketUuid: String?,
    amountCents: Long,
    reason: BucketTransferReason,
    cycleStart: LocalDate,
    cycleEndExclusive: LocalDate,
    effectiveDate: LocalDate,
    installId: String,
    nowEpochMs: Long,
    hybridLogicalClockService: HybridLogicalClockService
) {
    bucketTransferDao.insert(
        BucketTransfer(
            transferUuid = UUID.randomUUID().toString(),
            fromBucketUuid = fromBucketUuid,
            toBucketUuid = toBucketUuid,
            amountCents = amountCents,
            reason = reason,
            cycleStartDate = cycleStart.toString(),
            cycleEndDateExclusive = cycleEndExclusive.toString(),
            effectiveDate = effectiveDate.toString(),
            originInstallId = installId,
            lastModifiedByInstallId = installId,
            createdAtEpochMs = nowEpochMs,
            updatedAtEpochMs = nowEpochMs,
            deletedAtEpochMs = null,
            modClock = hybridLogicalClockService.format(nowEpochMs, 0, installId)
        ).toEntity()
    )
}
