package net.loeu.wallybudget.domain.usecase.internal

import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.BucketTransferDao
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.BucketCycleBaseline
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import net.loeu.wallybudget.domain.model.BucketTransfer
import net.loeu.wallybudget.domain.model.BucketTransferReason
import net.loeu.wallybudget.domain.service.CurrentCycleBucketAllocationResolver
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import java.time.LocalDate
import java.util.UUID

internal data class CurrentCycleReallocation(
    val bucketAllocationCents: Long,
    val defaultBucketAllocationCents: Long,
    val transferAmountCents: Long,
    val fromBucketUuid: String,
    val toBucketUuid: String
)

internal data class CurrentCycleCloseSettlement(
    val closingBucketAllocationCents: Long,
    val defaultBucketAllocationCents: Long,
    val settlementCents: Long
)

internal suspend fun upsertCurrentCyclePortfolioPolicyAmount(
    budgetPolicyDao: BudgetPolicyDao,
    cycleStart: LocalDate,
    cycleEndExclusive: LocalDate,
    budgetAmountCents: Long,
    paydayDayOfMonth: Int,
    installId: String,
    nowEpochMs: Long,
    hybridLogicalClockService: HybridLogicalClockService
) {
    val existing = budgetPolicyDao.findActivePolicyForCycle(cycleStart.toString())
    if (existing == null) {
        budgetPolicyDao.insert(
            newBudgetPolicy(
                cycleStart = cycleStart,
                cycleEndExclusive = cycleEndExclusive,
                budgetAmountCents = budgetAmountCents,
                paydayDayOfMonth = paydayDayOfMonth,
                installId = installId,
                nowEpochMs = nowEpochMs,
                hybridLogicalClockService = hybridLogicalClockService
            ).toEntity()
        )
        return
    }
    if (existing.budgetAmountCents == budgetAmountCents) return
    budgetPolicyDao.update(
        existing.copy(
            budgetAmountCents = budgetAmountCents,
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

internal fun currentCycleTransferDelta(
    bucketUuid: String,
    transfers: List<BucketTransfer>
): Long {
    return transfers.sumOf { transfer ->
        when (bucketUuid) {
            transfer.toBucketUuid -> transfer.amountCents
            transfer.fromBucketUuid -> -transfer.amountCents
            else -> 0L
        }
    }
}

internal fun resolveCurrentCycleAllocationSnapshot(
    buckets: List<BudgetBucket>,
    cycleStart: LocalDate,
    baselines: List<BucketCycleBaseline>,
    transfers: List<BucketTransfer>,
    currentCycleBucketAllocationResolver: CurrentCycleBucketAllocationResolver = CurrentCycleBucketAllocationResolver()
): Map<String, Long> {
    return buckets.associate { bucket ->
        bucket.bucketUuid to currentCycleBucketAllocationResolver.resolve(
            bucketUuid = bucket.bucketUuid,
            cycleStart = cycleStart,
            fallbackAllocationCents = bucket.defaultAllocatedAmountCents,
            baselines = baselines,
            transfers = transfers
        ).effectiveAllocationCents
    }
}

internal fun resolveCurrentCycleAllocationTotal(
    buckets: List<BudgetBucket>,
    cycleStart: LocalDate,
    baselines: List<BucketCycleBaseline>,
    transfers: List<BucketTransfer>,
    currentCycleBucketAllocationResolver: CurrentCycleBucketAllocationResolver = CurrentCycleBucketAllocationResolver()
): Long {
    return resolveCurrentCycleAllocationSnapshot(
        buckets = buckets,
        cycleStart = cycleStart,
        baselines = baselines,
        transfers = transfers,
        currentCycleBucketAllocationResolver = currentCycleBucketAllocationResolver
    ).values.sum()
}

internal fun resolveCurrentCycleDefaultAllocation(
    portfolioMonthlyBudgetCents: Long,
    namedBuckets: List<BudgetBucket>,
    cycleStart: LocalDate,
    baselines: List<BucketCycleBaseline>,
    transfers: List<BucketTransfer>,
    currentCycleBucketAllocationResolver: CurrentCycleBucketAllocationResolver = CurrentCycleBucketAllocationResolver()
): Long {
    val namedCurrentAllocationTotal = resolveCurrentCycleAllocationTotal(
        buckets = namedBuckets,
        cycleStart = cycleStart,
        baselines = baselines,
        transfers = transfers,
        currentCycleBucketAllocationResolver = currentCycleBucketAllocationResolver
    )
    return (
        portfolioMonthlyBudgetCents -
            namedCurrentAllocationTotal -
            currentCycleTransferDelta(DEFAULT_SPENDING_BUCKET_UUID, transfers)
        ).coerceAtLeast(0L)
}

internal fun resolveCurrentCycleReallocation(
    bucketUuid: String,
    currentAllocation: Long,
    targetAllocation: Long,
    defaultCurrentAllocation: Long
): CurrentCycleReallocation? {
    if (currentAllocation == targetAllocation) return null
    val delta = targetAllocation - currentAllocation
    return CurrentCycleReallocation(
        bucketAllocationCents = targetAllocation,
        defaultBucketAllocationCents = defaultCurrentAllocation - delta,
        transferAmountCents = kotlin.math.abs(delta),
        fromBucketUuid = if (delta >= 0L) DEFAULT_SPENDING_BUCKET_UUID else bucketUuid,
        toBucketUuid = if (delta >= 0L) bucketUuid else DEFAULT_SPENDING_BUCKET_UUID
    )
}

internal fun resolveCurrentCycleCloseSettlement(
    currentAllocation: Long,
    spentCents: Long,
    defaultCurrentAllocation: Long
): CurrentCycleCloseSettlement {
    val settlementCents = currentAllocation - spentCents
    return CurrentCycleCloseSettlement(
        closingBucketAllocationCents = spentCents,
        defaultBucketAllocationCents = defaultCurrentAllocation + settlementCents,
        settlementCents = settlementCents
    )
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
