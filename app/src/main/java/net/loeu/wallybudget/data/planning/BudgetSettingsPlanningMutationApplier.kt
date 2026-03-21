package net.loeu.wallybudget.data.planning

import net.loeu.wallybudget.data.local.dao.BudgetBucketDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationPolicyDao
import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BucketAllocationResolver
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import net.loeu.wallybudget.domain.usecase.BucketDraft
import net.loeu.wallybudget.domain.usecase.internal.resolveSelectedOpenBucketUuid
import java.time.ZoneId

class BudgetSettingsPlanningMutationApplier(
    budgetBucketDao: BudgetBucketDao,
    private val bucketAllocationPolicyDao: BucketAllocationPolicyDao,
    private val bucketAllocationAdjustmentDao: BucketAllocationAdjustmentDao,
    hybridLogicalClockService: HybridLogicalClockService,
    bucketAllocationResolver: BucketAllocationResolver
) {
    private val store = BucketMutationStore(
        budgetBucketDao = budgetBucketDao,
        bucketAllocationPolicyDao = bucketAllocationPolicyDao,
        bucketAllocationAdjustmentDao = bucketAllocationAdjustmentDao,
        hybridLogicalClockService = hybridLogicalClockService,
        bucketAllocationResolver = bucketAllocationResolver
    )
    private val hybridLogicalClockService = hybridLogicalClockService

    suspend fun apply(
        context: BudgetSettingsPlanningMutationContext,
        bucketDrafts: List<BucketDraft>,
        leftoverReceiverBucketUuid: String?
    ): String? {
        val nowEpochMs = context.today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val existingByUuid = context.buckets.associateBy { it.bucketUuid }
        val finalSelectedBucketUuid = resolveSelectedBucketUuid(
            settings = context.settings,
            existingByUuid = existingByUuid,
            bucketDrafts = bucketDrafts,
            nowEpochMs = nowEpochMs
        )
        bucketDrafts.sortedBy { it.sortOrder }.forEach { draft ->
            if (existingByUuid[draft.bucketUuid] == null) {
                handleNewDraft(draft, context, nowEpochMs, leftoverReceiverBucketUuid)
            } else {
                handleExistingDraft(
                    draft = draft,
                    existing = requireNotNull(existingByUuid[draft.bucketUuid]),
                    context = context,
                    nowEpochMs = nowEpochMs,
                    leftoverReceiverBucketUuid = leftoverReceiverBucketUuid
                )
            }
        }
        return finalSelectedBucketUuid
    }

    private fun resolveSelectedBucketUuid(
        settings: UserSettings,
        existingByUuid: Map<String, BudgetBucket>,
        bucketDrafts: List<BucketDraft>,
        nowEpochMs: Long
    ): String? {
        return resolveSelectedOpenBucketUuid(
            selectedBucketUuid = settings.selectedBucketUuid,
            openBuckets = bucketDrafts.filterNot { it.closeRequested }.map { draft ->
                existingByUuid[draft.bucketUuid]?.copy(
                    name = draft.name.trim(),
                    trackingMode = draft.trackingMode,
                    balanceBehavior = draft.balanceBehavior,
                    defaultAllocatedAmountCents = draft.defaultAllocatedAmountCents,
                    sortOrder = draft.sortOrder,
                    closedAtEpochMs = null,
                    deletedAtEpochMs = null
                ) ?: BudgetBucket(
                    bucketUuid = draft.bucketUuid,
                    name = draft.name.trim(),
                    trackingMode = draft.trackingMode,
                    balanceBehavior = draft.balanceBehavior,
                    defaultAllocatedAmountCents = draft.defaultAllocatedAmountCents,
                    sortOrder = draft.sortOrder,
                    originInstallId = settings.installDeviceId,
                    lastModifiedByInstallId = settings.installDeviceId,
                    createdAtEpochMs = nowEpochMs,
                    updatedAtEpochMs = nowEpochMs,
                    modClock = ""
                )
            }
        )
    }

    private suspend fun handleNewDraft(
        draft: BucketDraft,
        context: BudgetSettingsPlanningMutationContext,
        nowEpochMs: Long,
        leftoverReceiverBucketUuid: String?
    ) {
        store.insertNewBucket(context.settings, draft, nowEpochMs)
        if (draft.closeRequested) return
        if (draft.bucketUuid == leftoverReceiverBucketUuid) {
            upsertCurrentCycleDefaultBucketPolicy(draft, context, nowEpochMs)
        } else {
            store.ensureCurrentCycleBucketPolicy(
                bucketUuid = draft.bucketUuid,
                allocatedAmountCents = draft.defaultAllocatedAmountCents,
                currentCycleStart = context.currentCycleStart,
                currentCycleEndExclusive = context.currentCycleEndExclusive,
                settings = context.settings,
                nowEpochMs = nowEpochMs
            )
        }
    }

    private suspend fun handleExistingDraft(
        draft: BucketDraft,
        existing: BudgetBucket,
        context: BudgetSettingsPlanningMutationContext,
        nowEpochMs: Long,
        leftoverReceiverBucketUuid: String?
    ) {
        store.updateExistingBucket(existing, draft, context.settings, nowEpochMs)
        if (draft.closeRequested) {
            zeroClosedBucket(existing, context, nowEpochMs)
            softDeleteFuturePoliciesAndAdjustments(draft.bucketUuid, context, nowEpochMs)
            return
        }
        if (draft.bucketUuid == leftoverReceiverBucketUuid) {
            upsertCurrentCycleDefaultBucketPolicy(draft, context, nowEpochMs)
        } else {
            store.ensureCurrentCycleBucketPolicy(
                bucketUuid = draft.bucketUuid,
                allocatedAmountCents = existing.defaultAllocatedAmountCents,
                currentCycleStart = context.currentCycleStart,
                currentCycleEndExclusive = context.currentCycleEndExclusive,
                settings = context.settings,
                nowEpochMs = nowEpochMs
            )
            updateCurrentCycleBucketAllocationIfNeeded(existing, draft, context, nowEpochMs)
        }
        softDeleteFuturePoliciesAndAdjustments(draft.bucketUuid, context, nowEpochMs)
    }

    private suspend fun upsertCurrentCycleDefaultBucketPolicy(
        draft: BucketDraft,
        context: BudgetSettingsPlanningMutationContext,
        nowEpochMs: Long
    ) {
        val existing = bucketAllocationPolicyDao.findActivePolicyForCycle(
            bucketUuid = draft.bucketUuid,
            cycleStartDate = context.currentCycleStart.toString()
        )
        if (existing == null) {
            store.ensureCurrentCycleBucketPolicy(
                bucketUuid = draft.bucketUuid,
                allocatedAmountCents = draft.defaultAllocatedAmountCents,
                currentCycleStart = context.currentCycleStart,
                currentCycleEndExclusive = context.currentCycleEndExclusive,
                settings = context.settings,
                nowEpochMs = nowEpochMs
            )
        } else if (existing.allocatedAmountCents != draft.defaultAllocatedAmountCents) {
            bucketAllocationPolicyDao.update(
                existing.copy(
                    allocatedAmountCents = draft.defaultAllocatedAmountCents,
                    updatedAtEpochMs = nowEpochMs,
                    lastModifiedByInstallId = context.settings.installDeviceId,
                    modClock = hybridLogicalClockService.next(
                        previousClock = existing.modClock,
                        nowEpochMs = nowEpochMs,
                        installId = context.settings.installDeviceId
                    )
                )
            )
        }
        store.clearCurrentCycleAdjustments(draft.bucketUuid, context.currentCycleStart)
    }

    private suspend fun updateCurrentCycleBucketAllocationIfNeeded(
        bucket: BudgetBucket,
        draft: BucketDraft,
        context: BudgetSettingsPlanningMutationContext,
        nowEpochMs: Long
    ) {
        val baseAllocationCents = bucketAllocationPolicyDao.findActivePolicyForCycle(
            bucketUuid = bucket.bucketUuid,
            cycleStartDate = context.currentCycleStart.toString()
        )?.allocatedAmountCents ?: bucket.defaultAllocatedAmountCents
        val currentAllocation = store.currentAllocatedAmount(
            bucket = bucket,
            currentCycleStart = context.currentCycleStart,
            currentCycleEndExclusive = context.currentCycleEndExclusive,
            today = context.today
        )
        if (currentAllocation == draft.defaultAllocatedAmountCents) return
        store.insertBucketAllocationAdjustment(
            bucketUuid = bucket.bucketUuid,
            currentCycleStart = context.currentCycleStart,
            today = context.today,
            previousAllocatedAmountCents = currentAllocation,
            newAllocatedAmountCents = draft.defaultAllocatedAmountCents,
            settings = context.settings,
            nowEpochMs = nowEpochMs
        )
        store.normalizeBucketAdjustments(
            bucketUuid = bucket.bucketUuid,
            cycleStart = context.currentCycleStart,
            baseAllocatedAmountCents = baseAllocationCents,
            settings = context.settings
        )
    }

    private suspend fun zeroClosedBucket(
        bucket: BudgetBucket,
        context: BudgetSettingsPlanningMutationContext,
        nowEpochMs: Long
    ) {
        val baseAllocationCents = bucketAllocationPolicyDao.findActivePolicyForCycle(
            bucketUuid = bucket.bucketUuid,
            cycleStartDate = context.currentCycleStart.toString()
        )?.allocatedAmountCents ?: bucket.defaultAllocatedAmountCents
        val currentAllocation = store.currentAllocatedAmount(
            bucket = bucket,
            currentCycleStart = context.currentCycleStart,
            currentCycleEndExclusive = context.currentCycleEndExclusive,
            today = context.today
        )
        if (currentAllocation == 0L) return
        store.insertBucketAllocationAdjustment(
            bucketUuid = bucket.bucketUuid,
            currentCycleStart = context.currentCycleStart,
            today = context.today,
            previousAllocatedAmountCents = currentAllocation,
            newAllocatedAmountCents = 0L,
            settings = context.settings,
            nowEpochMs = nowEpochMs
        )
        store.normalizeBucketAdjustments(
            bucketUuid = bucket.bucketUuid,
            cycleStart = context.currentCycleStart,
            baseAllocatedAmountCents = baseAllocationCents,
            settings = context.settings
        )
    }

    private suspend fun softDeleteFuturePoliciesAndAdjustments(
        bucketUuid: String,
        context: BudgetSettingsPlanningMutationContext,
        nowEpochMs: Long
    ) {
        context.bucketPolicies
            .filter { it.bucketUuid == bucketUuid }
            .filter { it.deletedAtEpochMs == null && !it.cycleStart().isBefore(context.currentCycleEndExclusive) }
            .forEach { policy ->
                val entity = bucketAllocationPolicyDao.findByAllocationUuid(policy.allocationUuid) ?: return@forEach
                if (entity.deletedAtEpochMs != null) return@forEach
                val tombstoneEpochMs = maxOf(nowEpochMs, entity.updatedAtEpochMs + 1L)
                bucketAllocationPolicyDao.update(
                    entity.copy(
                        deletedAtEpochMs = tombstoneEpochMs,
                        updatedAtEpochMs = tombstoneEpochMs,
                        lastModifiedByInstallId = context.settings.installDeviceId,
                        modClock = hybridLogicalClockService.next(
                            previousClock = entity.modClock,
                            nowEpochMs = tombstoneEpochMs,
                            installId = context.settings.installDeviceId
                        )
                    )
                )
            }
        context.bucketAdjustments
            .filter { it.bucketUuid == bucketUuid }
            .filter { it.deletedAtEpochMs == null && !it.cycleStart().isBefore(context.currentCycleEndExclusive) }
            .forEach { adjustment ->
                val entity = bucketAllocationAdjustmentDao.findByAdjustmentUuid(adjustment.adjustmentUuid)
                    ?: return@forEach
                if (entity.deletedAtEpochMs != null) return@forEach
                val tombstoneEpochMs = maxOf(nowEpochMs, entity.updatedAtEpochMs + 1L)
                bucketAllocationAdjustmentDao.update(
                    entity.copy(
                        deletedAtEpochMs = tombstoneEpochMs,
                        updatedAtEpochMs = tombstoneEpochMs,
                        lastModifiedByInstallId = context.settings.installDeviceId,
                        modClock = hybridLogicalClockService.next(
                            previousClock = entity.modClock,
                            nowEpochMs = tombstoneEpochMs,
                            installId = context.settings.installDeviceId
                        )
                    )
                )
            }
    }
}
