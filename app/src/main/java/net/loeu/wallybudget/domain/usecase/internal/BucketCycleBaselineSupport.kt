package net.loeu.wallybudget.domain.usecase.internal

import net.loeu.wallybudget.data.local.dao.BucketCycleBaselineDao
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.domain.model.BucketCycleBaseline
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import java.time.LocalDate
import java.util.UUID

internal fun newBucketCycleBaseline(
    bucketUuid: String,
    cycleStart: LocalDate,
    cycleEndExclusive: LocalDate,
    baselineAmountCents: Long,
    installId: String,
    nowEpochMs: Long,
    hybridLogicalClockService: HybridLogicalClockService
): BucketCycleBaseline {
    return BucketCycleBaseline(
        baselineUuid = UUID.randomUUID().toString(),
        bucketUuid = bucketUuid,
        cycleStartDate = cycleStart.toString(),
        cycleEndDateExclusive = cycleEndExclusive.toString(),
        baselineAmountCents = baselineAmountCents,
        originInstallId = installId,
        lastModifiedByInstallId = installId,
        createdAtEpochMs = nowEpochMs,
        updatedAtEpochMs = nowEpochMs,
        deletedAtEpochMs = null,
        modClock = hybridLogicalClockService.format(nowEpochMs, 0, installId)
    )
}

internal suspend fun upsertCurrentCycleBucketBaselineAmount(
    bucketCycleBaselineDao: BucketCycleBaselineDao,
    bucketUuid: String,
    cycleStart: LocalDate,
    cycleEndExclusive: LocalDate,
    baselineAmountCents: Long,
    installId: String,
    nowEpochMs: Long,
    hybridLogicalClockService: HybridLogicalClockService
) {
    val existing = bucketCycleBaselineDao.findActiveBaselineForCycle(
        bucketUuid = bucketUuid,
        cycleStartDate = cycleStart.toString()
    )
    if (existing == null) {
        bucketCycleBaselineDao.insert(
            newBucketCycleBaseline(
                bucketUuid = bucketUuid,
                cycleStart = cycleStart,
                cycleEndExclusive = cycleEndExclusive,
                baselineAmountCents = baselineAmountCents,
                installId = installId,
                nowEpochMs = nowEpochMs,
                hybridLogicalClockService = hybridLogicalClockService
            ).toEntity()
        )
        return
    }
    if (existing.baselineAmountCents == baselineAmountCents &&
        existing.cycleEndDateExclusive == cycleEndExclusive.toString()
    ) {
        return
    }
    bucketCycleBaselineDao.update(
        existing.copy(
            cycleEndDateExclusive = cycleEndExclusive.toString(),
            baselineAmountCents = baselineAmountCents,
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
