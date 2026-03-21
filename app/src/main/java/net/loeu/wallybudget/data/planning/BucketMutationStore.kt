package net.loeu.wallybudget.data.planning

import net.loeu.wallybudget.data.local.dao.BudgetBucketDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationPolicyDao
import net.loeu.wallybudget.data.local.entity.toDomainModel as bucketAdjustmentToDomainModel
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.BucketAllocationAdjustment
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BucketAllocationResolver
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import net.loeu.wallybudget.domain.usecase.BucketDraft
import net.loeu.wallybudget.domain.usecase.internal.newBucketAllocationAdjustment
import net.loeu.wallybudget.domain.usecase.internal.newBucketAllocationPolicy
import java.time.LocalDate
import java.util.UUID

class BucketMutationStore(
    private val budgetBucketDao: BudgetBucketDao,
    private val bucketAllocationPolicyDao: BucketAllocationPolicyDao,
    private val bucketAllocationAdjustmentDao: BucketAllocationAdjustmentDao,
    private val hybridLogicalClockService: HybridLogicalClockService,
    private val bucketAllocationResolver: BucketAllocationResolver? = null
) {
    suspend fun insertNewBucket(
        settings: UserSettings,
        draft: BucketDraft,
        nowEpochMs: Long
    ) {
        val installId = settings.installDeviceId
        budgetBucketDao.insert(
            BudgetBucket(
                bucketUuid = draft.bucketUuid.ifBlank { UUID.randomUUID().toString() },
                name = draft.name.trim(),
                trackingMode = draft.trackingMode,
                balanceBehavior = draft.balanceBehavior,
                defaultAllocatedAmountCents = draft.defaultAllocatedAmountCents,
                sortOrder = draft.sortOrder,
                originInstallId = installId,
                lastModifiedByInstallId = installId,
                createdAtEpochMs = nowEpochMs,
                updatedAtEpochMs = nowEpochMs,
                closedAtEpochMs = if (draft.closeRequested) nowEpochMs else null,
                deletedAtEpochMs = null,
                modClock = hybridLogicalClockService.format(nowEpochMs, 0, installId)
            ).toEntity()
        )
    }

    suspend fun updateExistingBucket(
        existing: BudgetBucket,
        draft: BucketDraft,
        settings: UserSettings,
        nowEpochMs: Long
    ) {
        val entity = budgetBucketDao.findByBucketUuid(existing.bucketUuid) ?: return
        budgetBucketDao.update(
            existing.copy(
                name = draft.name.trim(),
                trackingMode = draft.trackingMode,
                balanceBehavior = draft.balanceBehavior,
                defaultAllocatedAmountCents = draft.defaultAllocatedAmountCents,
                sortOrder = draft.sortOrder,
                updatedAtEpochMs = nowEpochMs,
                lastModifiedByInstallId = settings.installDeviceId,
                closedAtEpochMs = if (draft.closeRequested) nowEpochMs else existing.closedAtEpochMs,
                modClock = hybridLogicalClockService.next(
                    previousClock = existing.modClock,
                    nowEpochMs = nowEpochMs,
                    installId = settings.installDeviceId
                )
            ).toEntity(id = entity.id)
        )
    }

    suspend fun upsertCurrentCycleBucketPolicy(
        bucketUuid: String,
        allocatedAmountCents: Long,
        currentCycleStart: LocalDate,
        currentCycleEndExclusive: LocalDate,
        settings: UserSettings,
        nowEpochMs: Long
    ) {
        val existing = bucketAllocationPolicyDao.findActivePolicyForCycle(
            bucketUuid = bucketUuid,
            cycleStartDate = currentCycleStart.toString()
        )
        if (existing == null) {
            bucketAllocationPolicyDao.insert(
                newBucketAllocationPolicy(
                    bucketUuid = bucketUuid,
                    cycleStart = currentCycleStart,
                    cycleEndExclusive = currentCycleEndExclusive,
                    allocatedAmountCents = allocatedAmountCents,
                    installId = settings.installDeviceId,
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
                lastModifiedByInstallId = settings.installDeviceId,
                modClock = hybridLogicalClockService.next(
                    previousClock = existing.modClock,
                    nowEpochMs = nowEpochMs,
                    installId = settings.installDeviceId
                )
            )
        )
    }

    suspend fun ensureCurrentCycleBucketPolicy(
        bucketUuid: String,
        allocatedAmountCents: Long,
        currentCycleStart: LocalDate,
        currentCycleEndExclusive: LocalDate,
        settings: UserSettings,
        nowEpochMs: Long
    ) {
        val existing = bucketAllocationPolicyDao.findActivePolicyForCycle(
            bucketUuid = bucketUuid,
            cycleStartDate = currentCycleStart.toString()
        )
        if (existing != null) return
        bucketAllocationPolicyDao.insert(
            newBucketAllocationPolicy(
                bucketUuid = bucketUuid,
                cycleStart = currentCycleStart,
                cycleEndExclusive = currentCycleEndExclusive,
                allocatedAmountCents = allocatedAmountCents,
                installId = settings.installDeviceId,
                nowEpochMs = nowEpochMs,
                hybridLogicalClockService = hybridLogicalClockService
            ).toEntity()
        )
    }

    suspend fun clearCurrentCycleAdjustments(
        bucketUuid: String,
        currentCycleStart: LocalDate
    ) {
        val activeAdjustments = bucketAllocationAdjustmentDao.getActiveForCycle(
            bucketUuid = bucketUuid,
            cycleStartDate = currentCycleStart.toString()
        )
        if (activeAdjustments.isNotEmpty()) {
            bucketAllocationAdjustmentDao.deleteByAdjustmentUuids(activeAdjustments.map { it.adjustmentUuid })
        }
    }

    suspend fun currentAllocatedAmount(
        bucket: BudgetBucket,
        currentCycleStart: LocalDate,
        currentCycleEndExclusive: LocalDate,
        today: LocalDate
    ): Long {
        val resolver = requireNotNull(bucketAllocationResolver)
        val currentPolicyEntity = bucketAllocationPolicyDao.findActivePolicyForCycle(
            bucketUuid = bucket.bucketUuid,
            cycleStartDate = currentCycleStart.toString()
        )
        val baseAllocationCents = currentPolicyEntity?.allocatedAmountCents ?: bucket.defaultAllocatedAmountCents
        val currentAdjustments = bucketAllocationAdjustmentDao.getActiveForCycle(
            bucketUuid = bucket.bucketUuid,
            cycleStartDate = currentCycleStart.toString()
        ).map { it.bucketAdjustmentToDomainModel() }
        return resolver.currentAllocatedAmount(
            cycleStart = currentCycleStart,
            cycleEndExclusive = currentCycleEndExclusive,
            baseAllocatedAmountCents = baseAllocationCents,
            adjustments = currentAdjustments,
            onDate = today
        )
    }

    suspend fun insertBucketAllocationAdjustment(
        bucketUuid: String,
        currentCycleStart: LocalDate,
        today: LocalDate,
        previousAllocatedAmountCents: Long,
        newAllocatedAmountCents: Long,
        settings: UserSettings,
        nowEpochMs: Long
    ) {
        bucketAllocationAdjustmentDao.insert(
            newBucketAllocationAdjustment(
                bucketUuid = bucketUuid,
                cycleStart = currentCycleStart,
                effectiveDate = today,
                previousAllocatedAmountCents = previousAllocatedAmountCents,
                newAllocatedAmountCents = newAllocatedAmountCents,
                installId = settings.installDeviceId,
                nowEpochMs = nowEpochMs,
                hybridLogicalClockService = hybridLogicalClockService
            ).toEntity()
        )
    }

    suspend fun normalizeBucketAdjustments(
        bucketUuid: String,
        cycleStart: LocalDate,
        baseAllocatedAmountCents: Long,
        settings: UserSettings
    ) {
        val activeAdjustments = bucketAllocationAdjustmentDao.getActiveForCycle(
            bucketUuid = bucketUuid,
            cycleStartDate = cycleStart.toString()
        )
        if (activeAdjustments.isEmpty()) return

        val nowEpochMs = System.currentTimeMillis()
        val redundantAdjustmentUuids = mutableSetOf<String>()
        var currentAllocation = baseAllocatedAmountCents

        activeAdjustments
            .groupBy { it.effectiveDate }
            .toSortedMap()
            .values
            .forEach { sameDayAdjustments ->
                sameDayAdjustments.dropLast(1).forEach { redundantAdjustmentUuids += it.adjustmentUuid }
                val latestAdjustment = sameDayAdjustments.last()
                if (latestAdjustment.newAllocatedAmountCents == currentAllocation) {
                    redundantAdjustmentUuids += latestAdjustment.adjustmentUuid
                } else {
                    if (latestAdjustment.previousAllocatedAmountCents != currentAllocation) {
                        bucketAllocationAdjustmentDao.update(
                            latestAdjustment.copy(
                                previousAllocatedAmountCents = currentAllocation,
                                updatedAtEpochMs = nowEpochMs,
                                lastModifiedByInstallId = settings.installDeviceId,
                                modClock = hybridLogicalClockService.next(
                                    previousClock = latestAdjustment.modClock,
                                    nowEpochMs = nowEpochMs,
                                    installId = settings.installDeviceId
                                )
                            )
                        )
                    }
                    currentAllocation = latestAdjustment.newAllocatedAmountCents
                }
            }

        if (redundantAdjustmentUuids.isNotEmpty()) {
            bucketAllocationAdjustmentDao.deleteByAdjustmentUuids(redundantAdjustmentUuids.toList())
        }
    }
}
