package net.loeu.wallybudget.data.planning

import net.loeu.wallybudget.data.local.dao.BudgetBucketDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationPolicyDao
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.BucketAllocationPolicy
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import net.loeu.wallybudget.domain.usecase.BucketDraft
import net.loeu.wallybudget.domain.usecase.internal.newBucketAllocationPolicy
import net.loeu.wallybudget.domain.usecase.internal.resolveSelectedOpenBucketUuid
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class PortfolioPlanningMutationApplier(
    budgetBucketDao: BudgetBucketDao,
    bucketAllocationPolicyDao: BucketAllocationPolicyDao,
    private val bucketAllocationAdjustmentDao: BucketAllocationAdjustmentDao,
    private val hybridLogicalClockService: HybridLogicalClockService
) {
    private val store = BucketMutationStore(
        budgetBucketDao = budgetBucketDao,
        bucketAllocationPolicyDao = bucketAllocationPolicyDao,
        bucketAllocationAdjustmentDao = bucketAllocationAdjustmentDao,
        hybridLogicalClockService = hybridLogicalClockService
    )
    private val bucketAllocationPolicyDao = bucketAllocationPolicyDao

    suspend fun apply(
        context: PortfolioPlanningMutationContext,
        bucketDrafts: List<BucketDraft>,
        portfolioMonthlyBudgetCents: Long,
        leftoverReceiverBucketUuid: String?
    ): String? {
        val nowEpochMs = context.today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val existingByUuid = context.buckets.associateBy { it.bucketUuid }
        val finalSelectedBucketUuid = resolveSelectedOpenBucketUuid(
            context.settings.selectedBucketUuid,
            bucketDrafts.filterNot { it.closeRequested }.map { draft ->
                existingByUuid[draft.bucketUuid]?.copy(
                    name = draft.name.trim(),
                    trackingMode = draft.trackingMode,
                    balanceBehavior = draft.balanceBehavior,
                    defaultAllocatedAmountCents = draft.defaultAllocatedAmountCents,
                    sortOrder = draft.sortOrder,
                    closedAtEpochMs = null,
                    deletedAtEpochMs = null
                ) ?: BudgetBucket(
                    bucketUuid = draft.bucketUuid.ifBlank { UUID.randomUUID().toString() },
                    name = draft.name.trim(),
                    trackingMode = draft.trackingMode,
                    balanceBehavior = draft.balanceBehavior,
                    defaultAllocatedAmountCents = draft.defaultAllocatedAmountCents,
                    sortOrder = draft.sortOrder,
                    originInstallId = context.settings.installDeviceId,
                    lastModifiedByInstallId = context.settings.installDeviceId,
                    createdAtEpochMs = nowEpochMs,
                    updatedAtEpochMs = nowEpochMs,
                    modClock = ""
                )
            }
        )
        bucketDrafts.sortedBy { it.sortOrder }.forEach { draft ->
            processDraft(
                draft = draft,
                existing = existingByUuid[draft.bucketUuid],
                context = context,
                nowEpochMs = nowEpochMs
            )
        }
        upsertFutureBucketPolicies(
            context = context,
            bucketDrafts = bucketDrafts,
            portfolioMonthlyBudgetCents = portfolioMonthlyBudgetCents,
            receiverBucketUuid = leftoverReceiverBucketUuid,
            nowEpochMs = nowEpochMs
        )
        return finalSelectedBucketUuid
    }

    private suspend fun processDraft(
        draft: BucketDraft,
        existing: BudgetBucket?,
        context: PortfolioPlanningMutationContext,
        nowEpochMs: Long
    ) {
        if (existing == null) {
            store.insertNewBucket(context.settings, draft, nowEpochMs)
        } else {
            store.updateExistingBucket(existing, draft, context.settings, nowEpochMs)
        }
        val allocatedAmount = if (draft.closeRequested) 0L else draft.defaultAllocatedAmountCents
        store.upsertCurrentCycleBucketPolicy(
            bucketUuid = draft.bucketUuid.ifBlank { UUID.randomUUID().toString() },
            allocatedAmountCents = allocatedAmount,
            currentCycleStart = context.currentCycleStart,
            currentCycleEndExclusive = context.currentCycleEndExclusive,
            settings = context.settings,
            nowEpochMs = nowEpochMs
        )
        store.clearCurrentCycleAdjustments(draft.bucketUuid, context.currentCycleStart)
        if (draft.closeRequested) {
            softDeleteFutureBucketPoliciesAndAdjustments(
                bucketUuid = draft.bucketUuid,
                context = context,
                nowEpochMs = nowEpochMs
            )
        }
    }

    private suspend fun upsertFutureBucketPolicies(
        context: PortfolioPlanningMutationContext,
        bucketDrafts: List<BucketDraft>,
        portfolioMonthlyBudgetCents: Long,
        receiverBucketUuid: String?,
        nowEpochMs: Long
    ) {
        val existingByUuid = context.buckets.associateBy { it.bucketUuid }
        val openDraftsByUuid = bucketDrafts.filterNot { it.closeRequested }.associateBy { it.bucketUuid }
        context.futureBucketPolicies.groupBy { it.cycleStart() }.toSortedMap().forEach { (cycleStart, cyclePolicies) ->
            val cycleEndExclusive = cyclePolicies.first().cycleEndExclusive()
            updateNamedFuturePolicies(
                context = context,
                cyclePolicies = cyclePolicies,
                openDraftsByUuid = openDraftsByUuid,
                existingByUuid = existingByUuid,
                receiverBucketUuid = receiverBucketUuid,
                nowEpochMs = nowEpochMs
            )
            val namedAllocationTotal = openDraftsByUuid.values
                .filterNot { it.bucketUuid == receiverBucketUuid }
                .sumOf { resolveFutureNamedBucketAllocation(it, cyclePolicies, existingByUuid) }
            upsertReceiverFuturePolicy(
                context = context,
                cycleStart = cycleStart,
                cycleEndExclusive = cycleEndExclusive,
                cyclePolicies = cyclePolicies,
                receiverBucketUuid = receiverBucketUuid,
                defaultAllocation = (portfolioMonthlyBudgetCents - namedAllocationTotal).coerceAtLeast(0L),
                nowEpochMs = nowEpochMs
            )
        }
    }

    private suspend fun updateNamedFuturePolicies(
        context: PortfolioPlanningMutationContext,
        cyclePolicies: List<BucketAllocationPolicy>,
        openDraftsByUuid: Map<String, BucketDraft>,
        existingByUuid: Map<String, BudgetBucket>,
        receiverBucketUuid: String?,
        nowEpochMs: Long
    ) {
        openDraftsByUuid.values.filterNot { it.bucketUuid == receiverBucketUuid }.forEach { draft ->
            if (!hasFutureNamedBucketAllocationChanged(draft, existingByUuid)) return@forEach
            val existingPolicy = cyclePolicies.firstOrNull { it.bucketUuid == draft.bucketUuid } ?: return@forEach
            val entity = bucketAllocationPolicyDao.findByAllocationUuid(existingPolicy.allocationUuid) ?: return@forEach
            if (entity.allocatedAmountCents == draft.defaultAllocatedAmountCents) return@forEach
            bucketAllocationPolicyDao.update(
                entity.copy(
                    allocatedAmountCents = draft.defaultAllocatedAmountCents,
                    updatedAtEpochMs = nowEpochMs,
                    lastModifiedByInstallId = context.settings.installDeviceId,
                    modClock = hybridLogicalClockService.next(
                        previousClock = entity.modClock,
                        nowEpochMs = nowEpochMs,
                        installId = context.settings.installDeviceId
                    )
                )
            )
        }
    }

    private suspend fun upsertReceiverFuturePolicy(
        context: PortfolioPlanningMutationContext,
        cycleStart: LocalDate,
        cycleEndExclusive: LocalDate,
        cyclePolicies: List<BucketAllocationPolicy>,
        receiverBucketUuid: String?,
        defaultAllocation: Long,
        nowEpochMs: Long
    ) {
        val receiverPolicy = cyclePolicies.firstOrNull { it.bucketUuid == receiverBucketUuid }
        val resolvedReceiverBucketUuid = receiverBucketUuid ?: DEFAULT_SPENDING_BUCKET_UUID
        if (receiverPolicy == null) {
            bucketAllocationPolicyDao.insert(
                newBucketAllocationPolicy(
                    bucketUuid = resolvedReceiverBucketUuid,
                    cycleStart = cycleStart,
                    cycleEndExclusive = cycleEndExclusive,
                    allocatedAmountCents = defaultAllocation,
                    installId = context.settings.installDeviceId,
                    nowEpochMs = nowEpochMs,
                    hybridLogicalClockService = hybridLogicalClockService
                ).toEntity()
            )
            return
        }
        val entity = bucketAllocationPolicyDao.findByAllocationUuid(receiverPolicy.allocationUuid) ?: return
        if (entity.allocatedAmountCents != defaultAllocation) {
            bucketAllocationPolicyDao.update(
                entity.copy(
                    allocatedAmountCents = defaultAllocation,
                    updatedAtEpochMs = nowEpochMs,
                    lastModifiedByInstallId = context.settings.installDeviceId,
                    modClock = hybridLogicalClockService.next(
                        previousClock = entity.modClock,
                        nowEpochMs = nowEpochMs,
                        installId = context.settings.installDeviceId
                    )
                )
            )
        }
    }

    private fun resolveFutureNamedBucketAllocation(
        draft: BucketDraft,
        cyclePolicies: List<BucketAllocationPolicy>,
        existingByUuid: Map<String, BudgetBucket>
    ): Long {
        return if (hasFutureNamedBucketAllocationChanged(draft, existingByUuid)) {
            draft.defaultAllocatedAmountCents
        } else {
            cyclePolicies.firstOrNull { it.bucketUuid == draft.bucketUuid }?.allocatedAmountCents
                ?: draft.defaultAllocatedAmountCents
        }
    }

    private fun hasFutureNamedBucketAllocationChanged(
        draft: BucketDraft,
        existingByUuid: Map<String, BudgetBucket>
    ): Boolean {
        val existingBucket = existingByUuid[draft.bucketUuid]
        return existingBucket == null ||
            existingBucket.defaultAllocatedAmountCents != draft.defaultAllocatedAmountCents
    }

    private suspend fun softDeleteFutureBucketPoliciesAndAdjustments(
        bucketUuid: String,
        context: PortfolioPlanningMutationContext,
        nowEpochMs: Long
    ) {
        context.futureBucketPolicies.filter { it.bucketUuid == bucketUuid }.forEach { policy ->
            val entity = bucketAllocationPolicyDao.findByAllocationUuid(policy.allocationUuid) ?: return@forEach
            bucketAllocationPolicyDao.update(
                entity.copy(
                    deletedAtEpochMs = nowEpochMs,
                    updatedAtEpochMs = nowEpochMs,
                    lastModifiedByInstallId = context.settings.installDeviceId,
                    modClock = hybridLogicalClockService.next(
                        previousClock = entity.modClock,
                        nowEpochMs = nowEpochMs,
                        installId = context.settings.installDeviceId
                    )
                )
            )
        }
        bucketAllocationAdjustmentDao.getAllForSnapshot()
            .filter { it.deletedAtEpochMs == null && it.bucketUuid == bucketUuid }
            .filter { LocalDate.parse(it.cycleStartDate) >= context.currentCycleEndExclusive }
            .forEach { adjustment ->
                bucketAllocationAdjustmentDao.update(
                    adjustment.copy(
                        deletedAtEpochMs = nowEpochMs,
                        updatedAtEpochMs = nowEpochMs,
                        lastModifiedByInstallId = context.settings.installDeviceId,
                        modClock = hybridLogicalClockService.next(
                            previousClock = adjustment.modClock,
                            nowEpochMs = nowEpochMs,
                            installId = context.settings.installDeviceId
                        )
                    )
                )
            }
    }
}
