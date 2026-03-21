@file:Suppress("LongMethod", "ReturnCount", "TooManyFunctions")

package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.first
import net.loeu.wallybudget.data.local.dao.BudgetBucketDao
import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationPolicyDao
import net.loeu.wallybudget.data.local.db.TransactionRunner
import net.loeu.wallybudget.data.local.entity.toDomainModel as bucketToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel as bucketPolicyToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel as policyToDomainModel
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.data.time.CurrentDateProvider
import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.BucketAllocationPolicy
import net.loeu.wallybudget.domain.model.BucketBalanceBehavior
import net.loeu.wallybudget.domain.model.BucketTrackingMode
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_NAME
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import net.loeu.wallybudget.domain.usecase.internal.resolveLeftoverReceiverDraftUuid
import net.loeu.wallybudget.domain.usecase.internal.newBucketAllocationPolicy
import net.loeu.wallybudget.domain.usecase.internal.resolveSelectedOpenBucketUuid
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

data class UpdatePortfolioPlanRequest(
    val portfolioMonthlyBudgetCents: Long,
    val leftoverReceiverBucketUuid: String? = null,
    val buckets: List<BucketDraft> = emptyList()
)

data class UpdatePortfolioPlanResult(
    val summaryMessage: String
)

private data class UpdatePortfolioPlanContext(
    val settings: UserSettings,
    val today: LocalDate,
    val currentCycleStart: LocalDate,
    val currentCycleEndExclusive: LocalDate,
    val buckets: List<BudgetBucket>,
    val futureBucketPolicies: List<BucketAllocationPolicy>
)

private data class PortfolioPlanMutationResult(
    val finalSelectedBucketUuid: String?
)

class UpdatePortfolioPlanUseCase(
    private val transactionRunner: TransactionRunner,
    private val userSettingsStore: UserSettingsStore,
    private val budgetPolicyDao: BudgetPolicyDao,
    private val budgetBucketDao: BudgetBucketDao,
    private val bucketAllocationPolicyDao: BucketAllocationPolicyDao,
    private val bucketAllocationAdjustmentDao: BucketAllocationAdjustmentDao,
    private val currentDateProvider: CurrentDateProvider,
    private val cycleScheduleResolver: CycleScheduleResolver,
    private val hybridLogicalClockService: HybridLogicalClockService
) {
    private val syncObservedDateUseCase = SyncObservedDateUseCase(userSettingsStore)

    suspend operator fun invoke(request: UpdatePortfolioPlanRequest): UpdatePortfolioPlanResult {
        val context = buildContext()
        val bucketDrafts = resolveBucketDrafts(request, context)
        validateRequest(request, bucketDrafts, context)

        val budgetChanged = request.portfolioMonthlyBudgetCents != context.settings.resolvedPortfolioMonthlyBudgetCents
        val resolvedLeftoverReceiverBucketUuid = resolveLeftoverReceiverDraftUuid(
            preferredBucketUuid = request.leftoverReceiverBucketUuid ?: context.settings.leftoverReceiverBucketUuid,
            openDrafts = bucketDrafts.filterNot { it.closeRequested },
            existingBucketsByUuid = context.buckets.associateBy { it.bucketUuid }
        )
        val leftoverReceiverChanged = resolvedLeftoverReceiverBucketUuid !=
            resolveLeftoverReceiverDraftUuid(
                preferredBucketUuid = context.settings.leftoverReceiverBucketUuid,
                openDrafts = context.buckets
                    .filterNot { it.isClosed }
                    .sortedWith(compareBy<BudgetBucket> { it.sortOrder }.thenBy { it.createdAtEpochMs })
                    .map {
                        BucketDraft(
                            bucketUuid = it.bucketUuid,
                            name = it.name,
                            trackingMode = it.trackingMode,
                            balanceBehavior = it.balanceBehavior,
                            defaultAllocatedAmountCents = it.defaultAllocatedAmountCents,
                            sortOrder = it.sortOrder
                        )
                    },
                existingBucketsByUuid = context.buckets.associateBy { it.bucketUuid }
            )
        val bucketChanged = hasBucketChanges(bucketDrafts, context)
        if (!budgetChanged && !bucketChanged && !leftoverReceiverChanged) {
            return UpdatePortfolioPlanResult("No planning changes.")
        }

        val mutation = transactionRunner.inTransaction {
            applyBucketDraftsDirect(
                context = context,
                bucketDrafts = bucketDrafts,
                portfolioMonthlyBudgetCents = request.portfolioMonthlyBudgetCents
            )
        }

        userSettingsStore.updateMonthlyBudget(request.portfolioMonthlyBudgetCents)
        userSettingsStore.updatePortfolioMonthlyBudget(request.portfolioMonthlyBudgetCents)
        userSettingsStore.updateLeftoverReceiverBucket(resolvedLeftoverReceiverBucketUuid)
        userSettingsStore.updateSelectedBucket(mutation.finalSelectedBucketUuid)
        invalidateOrExpirePendingPaydayUndo()

        val parts = buildList {
            if (budgetChanged) add("Portfolio budget updated.")
            if (bucketChanged || leftoverReceiverChanged) add("Bucket plan updated.")
        }
        return UpdatePortfolioPlanResult(parts.joinToString(" "))
    }

    private suspend fun buildContext(): UpdatePortfolioPlanContext {
        val settings = userSettingsStore.ensureIdentity()
        val today = syncObservedDateUseCase(settings, currentDateProvider.currentDate())
        val activePolicies = budgetPolicyDao.getAllForSnapshot()
            .filter { it.deletedAtEpochMs == null }
            .map { it.policyToDomainModel() }
            .sortedBy { it.cycleStartDate }
        val currentPolicy = cycleScheduleResolver.resolvePolicyForDate(today, settings, activePolicies)
        val futureBucketPolicies = bucketAllocationPolicyDao.getAllForSnapshot()
            .map { it.bucketPolicyToDomainModel() }
            .filter { it.deletedAtEpochMs == null && !it.cycleStart().isBefore(currentPolicy.cycleEndExclusive) }
        return UpdatePortfolioPlanContext(
            settings = settings,
            today = today,
            currentCycleStart = currentPolicy.cycleStart,
            currentCycleEndExclusive = currentPolicy.cycleEndExclusive,
            buckets = budgetBucketDao.getAllForSnapshot().map { it.bucketToDomainModel() },
            futureBucketPolicies = futureBucketPolicies
        )
    }

    private fun resolveBucketDrafts(
        request: UpdatePortfolioPlanRequest,
        context: UpdatePortfolioPlanContext
    ): List<BucketDraft> {
        if (request.buckets.isNotEmpty()) {
            return normalizeBucketDrafts(
                drafts = request.buckets.sortedBy { it.sortOrder },
                context = context,
                portfolioMonthlyBudgetCents = request.portfolioMonthlyBudgetCents,
                preferredLeftoverReceiverBucketUuid = request.leftoverReceiverBucketUuid
            )
        }
        val activeBuckets = context.buckets.filter { it.deletedAtEpochMs == null }
        if (activeBuckets.isEmpty()) {
            return normalizeBucketDrafts(
                drafts = listOf(
                    BucketDraft(
                        bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                        name = DEFAULT_SPENDING_BUCKET_NAME,
                        trackingMode = BucketTrackingMode.DAILY_TARGET,
                        balanceBehavior = BucketBalanceBehavior.RETURN_TO_PORTFOLIO,
                        defaultAllocatedAmountCents = request.portfolioMonthlyBudgetCents,
                        sortOrder = 0
                    )
                ),
                context = context,
                portfolioMonthlyBudgetCents = request.portfolioMonthlyBudgetCents,
                preferredLeftoverReceiverBucketUuid = request.leftoverReceiverBucketUuid
            )
        }
        return normalizeBucketDrafts(
            drafts = activeBuckets
                .sortedWith(compareBy<BudgetBucket> { it.sortOrder }.thenBy { it.createdAtEpochMs })
                .map { bucket ->
                    BucketDraft(
                        bucketUuid = bucket.bucketUuid,
                        name = bucket.name,
                        trackingMode = bucket.trackingMode,
                        balanceBehavior = bucket.balanceBehavior,
                        defaultAllocatedAmountCents = bucket.defaultAllocatedAmountCents,
                        sortOrder = bucket.sortOrder,
                        closeRequested = bucket.isClosed
                    )
            },
            context = context,
            portfolioMonthlyBudgetCents = request.portfolioMonthlyBudgetCents,
            preferredLeftoverReceiverBucketUuid = request.leftoverReceiverBucketUuid
        )
    }

    private fun validateRequest(
        request: UpdatePortfolioPlanRequest,
        bucketDrafts: List<BucketDraft>,
        context: UpdatePortfolioPlanContext
    ) {
        require(request.portfolioMonthlyBudgetCents > 0L) {
            "Portfolio budget must be greater than zero."
        }

        val existingByUuid = context.buckets.associateBy { it.bucketUuid }
        bucketDrafts.forEach { draft ->
            require(draft.name.isNotBlank()) { "Bucket name cannot be blank." }
            require(draft.defaultAllocatedAmountCents >= 0L) { "Bucket allocation cannot be negative." }
            val existing = existingByUuid[draft.bucketUuid]
            require(
                existing == null ||
                    existing.deletedAtEpochMs != null ||
                    !existing.isClosed ||
                    draft.closeRequested
            ) {
                "Closed buckets cannot be reopened."
            }
        }

        val openBuckets = bucketDrafts.filterNot { it.closeRequested }
        require(openBuckets.isNotEmpty()) { "At least one bucket must remain open." }
        val duplicateName = openBuckets
            .groupBy { it.name.trim().lowercase(Locale.getDefault()) }
            .values
            .firstOrNull { it.size > 1 }
        require(duplicateName == null) { "Bucket names must be unique." }
        val receiverBucketUuid = resolveLeftoverReceiverDraftUuid(
            preferredBucketUuid = request.leftoverReceiverBucketUuid ?: context.settings.leftoverReceiverBucketUuid,
            openDrafts = openBuckets,
            existingBucketsByUuid = existingByUuid
        )
        require(
            openBuckets
                .filterNot { it.bucketUuid == receiverBucketUuid }
                .sumOf { it.defaultAllocatedAmountCents } <= request.portfolioMonthlyBudgetCents
        ) {
            "Bucket allocations cannot exceed the portfolio budget."
        }
    }

    private fun hasBucketChanges(
        bucketDrafts: List<BucketDraft>,
        context: UpdatePortfolioPlanContext
    ): Boolean {
        val existingByUuid = context.buckets.associateBy { it.bucketUuid }
        val existingActiveCount = context.buckets.count { it.deletedAtEpochMs == null }
        if (bucketDrafts.size != existingActiveCount) {
            return true
        }
        return bucketDrafts.any { draft ->
            val existing = existingByUuid[draft.bucketUuid] ?: return@any true
            existing.name != draft.name ||
                existing.trackingMode != draft.trackingMode ||
                existing.balanceBehavior != draft.balanceBehavior ||
                existing.defaultAllocatedAmountCents != draft.defaultAllocatedAmountCents ||
                existing.sortOrder != draft.sortOrder ||
                draft.closeRequested != existing.isClosed
        }
    }

    private suspend fun applyBucketDraftsDirect(
        context: UpdatePortfolioPlanContext,
        bucketDrafts: List<BucketDraft>,
        portfolioMonthlyBudgetCents: Long
    ): PortfolioPlanMutationResult {
        val nowEpochMs = context.today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val settings = context.settings
        val existingByUuid = context.buckets.associateBy { it.bucketUuid }
        val openBuckets = bucketDrafts.filterNot { it.closeRequested }.map { draft ->
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
                originInstallId = settings.installDeviceId,
                lastModifiedByInstallId = settings.installDeviceId,
                createdAtEpochMs = nowEpochMs,
                updatedAtEpochMs = nowEpochMs,
                modClock = ""
            )
        }
        val finalSelectedBucketUuid = resolveSelectedOpenBucketUuid(settings.selectedBucketUuid, openBuckets)

        bucketDrafts.sortedBy { it.sortOrder }.forEach { draft ->
            val existing = existingByUuid[draft.bucketUuid]
            if (existing == null) {
                insertNewBucket(settings, draft, nowEpochMs)
            } else {
                updateExistingBucket(existing, draft, settings, nowEpochMs)
            }

            if (draft.closeRequested) {
                upsertCurrentCycleBucketPolicy(draft.bucketUuid, 0L, context, nowEpochMs)
            } else {
                upsertCurrentCycleBucketPolicy(
                    bucketUuid = draft.bucketUuid.ifBlank { UUID.randomUUID().toString() },
                    allocatedAmountCents = draft.defaultAllocatedAmountCents,
                    context = context,
                    nowEpochMs = nowEpochMs
                )
            }
            clearCurrentCycleAdjustments(draft.bucketUuid, context)
            if (draft.closeRequested) {
                softDeleteFutureBucketPoliciesAndAdjustments(draft.bucketUuid, context, settings, nowEpochMs)
            }
        }
        upsertFutureBucketPolicies(
            context = context,
            bucketDrafts = bucketDrafts,
            portfolioMonthlyBudgetCents = portfolioMonthlyBudgetCents,
            nowEpochMs = nowEpochMs
        )

        return PortfolioPlanMutationResult(finalSelectedBucketUuid)
    }

    private suspend fun insertNewBucket(
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

    private suspend fun updateExistingBucket(
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
                closedAtEpochMs = if (draft.closeRequested) nowEpochMs else null,
                modClock = hybridLogicalClockService.next(
                    previousClock = existing.modClock,
                    nowEpochMs = nowEpochMs,
                    installId = settings.installDeviceId
                )
            ).toEntity(id = entity.id)
        )
    }

    private suspend fun upsertCurrentCycleBucketPolicy(
        bucketUuid: String,
        allocatedAmountCents: Long,
        context: UpdatePortfolioPlanContext,
        nowEpochMs: Long
    ) {
        val existing = bucketAllocationPolicyDao.findActivePolicyForCycle(
            bucketUuid = bucketUuid,
            cycleStartDate = context.currentCycleStart.toString()
        )
        if (existing == null) {
            bucketAllocationPolicyDao.insert(
                newBucketAllocationPolicy(
                    bucketUuid = bucketUuid,
                    cycleStart = context.currentCycleStart,
                    cycleEndExclusive = context.currentCycleEndExclusive,
                    allocatedAmountCents = allocatedAmountCents,
                    installId = context.settings.installDeviceId,
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
                lastModifiedByInstallId = context.settings.installDeviceId,
                modClock = hybridLogicalClockService.next(
                    previousClock = existing.modClock,
                    nowEpochMs = nowEpochMs,
                    installId = context.settings.installDeviceId
                )
            )
        )
    }

    private suspend fun clearCurrentCycleAdjustments(
        bucketUuid: String,
        context: UpdatePortfolioPlanContext
    ) {
        val activeAdjustments = bucketAllocationAdjustmentDao.getActiveForCycle(
            bucketUuid = bucketUuid,
            cycleStartDate = context.currentCycleStart.toString()
        )
        if (activeAdjustments.isNotEmpty()) {
            bucketAllocationAdjustmentDao.deleteByAdjustmentUuids(activeAdjustments.map { it.adjustmentUuid })
        }
    }

    private suspend fun softDeleteFutureBucketPoliciesAndAdjustments(
        bucketUuid: String,
        context: UpdatePortfolioPlanContext,
        settings: UserSettings,
        nowEpochMs: Long
    ) {
        context.futureBucketPolicies
            .filter { it.bucketUuid == bucketUuid }
            .forEach { policy ->
                val entity = bucketAllocationPolicyDao.findByAllocationUuid(policy.allocationUuid) ?: return@forEach
                bucketAllocationPolicyDao.update(
                    entity.copy(
                        deletedAtEpochMs = nowEpochMs,
                        updatedAtEpochMs = nowEpochMs,
                        lastModifiedByInstallId = settings.installDeviceId,
                        modClock = hybridLogicalClockService.next(
                            previousClock = entity.modClock,
                            nowEpochMs = nowEpochMs,
                            installId = settings.installDeviceId
                        )
                    )
                )
            }
        val futureAdjustments = bucketAllocationAdjustmentDao.getAllForSnapshot()
            .filter { it.deletedAtEpochMs == null && it.bucketUuid == bucketUuid }
            .filter { LocalDate.parse(it.cycleStartDate) >= context.currentCycleEndExclusive }
        if (futureAdjustments.isNotEmpty()) {
            futureAdjustments.forEach { adjustment ->
                bucketAllocationAdjustmentDao.update(
                    adjustment.copy(
                        deletedAtEpochMs = nowEpochMs,
                        updatedAtEpochMs = nowEpochMs,
                        lastModifiedByInstallId = settings.installDeviceId,
                        modClock = hybridLogicalClockService.next(
                            previousClock = adjustment.modClock,
                            nowEpochMs = nowEpochMs,
                            installId = settings.installDeviceId
                        )
                    )
                )
            }
        }
    }

    private suspend fun upsertFutureBucketPolicies(
        context: UpdatePortfolioPlanContext,
        bucketDrafts: List<BucketDraft>,
        portfolioMonthlyBudgetCents: Long,
        nowEpochMs: Long
    ) {
        val existingByUuid = context.buckets.associateBy { it.bucketUuid }
        val receiverBucketUuid = resolveLeftoverReceiverDraftUuid(
            preferredBucketUuid = context.settings.leftoverReceiverBucketUuid,
            openDrafts = bucketDrafts.filterNot { it.closeRequested },
            existingBucketsByUuid = existingByUuid
        )
        val openDraftsByUuid = bucketDrafts
            .filterNot { it.closeRequested }
            .associateBy { it.bucketUuid }
        val futureCycles = context.futureBucketPolicies
            .groupBy { it.cycleStart() }
            .toSortedMap()
        futureCycles.forEach { (cycleStart, cyclePolicies) ->
            val cycleEndExclusive = cyclePolicies.first().cycleEndExclusive()
            updateChangedFutureNamedBucketPolicies(
                context = context,
                cyclePolicies = cyclePolicies,
                openDraftsByUuid = openDraftsByUuid,
                existingByUuid = existingByUuid,
                receiverBucketUuid = receiverBucketUuid,
                nowEpochMs = nowEpochMs
            )
            val namedAllocationTotal = openDraftsByUuid.values
                .filterNot { it.bucketUuid == receiverBucketUuid }
                .sumOf { draft ->
                    resolveFutureNamedBucketAllocation(
                        draft = draft,
                        cyclePolicies = cyclePolicies,
                        existingByUuid = existingByUuid
                    )
                }
            val defaultAllocation = (portfolioMonthlyBudgetCents - namedAllocationTotal).coerceAtLeast(0L)
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
            } else {
                val entity = bucketAllocationPolicyDao.findByAllocationUuid(
                    receiverPolicy.allocationUuid
                ) ?: return@forEach
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
        }
    }

    private suspend fun updateChangedFutureNamedBucketPolicies(
        context: UpdatePortfolioPlanContext,
        cyclePolicies: List<BucketAllocationPolicy>,
        openDraftsByUuid: Map<String, BucketDraft>,
        existingByUuid: Map<String, BudgetBucket>,
        receiverBucketUuid: String?,
        nowEpochMs: Long
    ) {
        openDraftsByUuid.values
            .filterNot { it.bucketUuid == receiverBucketUuid }
            .forEach { draft ->
                if (!hasFutureNamedBucketAllocationChanged(draft, existingByUuid)) return@forEach

                val existingPolicy = cyclePolicies.firstOrNull { it.bucketUuid == draft.bucketUuid }
                    ?: return@forEach
                val entity = bucketAllocationPolicyDao.findByAllocationUuid(existingPolicy.allocationUuid)
                    ?: return@forEach
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

    private suspend fun invalidateOrExpirePendingPaydayUndo() {
        val settings = userSettingsStore.ensureIdentity()
        val today = syncObservedDateUseCase(settings, currentDateProvider.currentDate())
        val pendingUndo = userSettingsStore.pendingPaydayUndo.first() ?: return
        if (!today.isBefore(pendingUndo.expiresAtExclusiveDate())) {
            userSettingsStore.clearPendingPaydayUndo()
            return
        }
        userSettingsStore.clearPendingPaydayUndo()
    }

    private fun normalizeBucketDrafts(
        drafts: List<BucketDraft>,
        context: UpdatePortfolioPlanContext,
        portfolioMonthlyBudgetCents: Long,
        preferredLeftoverReceiverBucketUuid: String?
    ): List<BucketDraft> {
        val existingByUuid = context.buckets.associateBy { it.bucketUuid }
        val trimmedDrafts = drafts.map { it.copy(name = it.name.trim()) }
        val openDrafts = trimmedDrafts.filterNot { it.closeRequested }
        val receiverBucketUuid = resolveLeftoverReceiverDraftUuid(
            preferredBucketUuid = preferredLeftoverReceiverBucketUuid ?: context.settings.leftoverReceiverBucketUuid,
            openDrafts = openDrafts,
            existingBucketsByUuid = existingByUuid
        )
        val namedOpenAllocationTotal = trimmedDrafts
            .filterNot { it.closeRequested || it.bucketUuid == receiverBucketUuid }
            .sumOf { it.defaultAllocatedAmountCents }
        return trimmedDrafts
            .map { draft ->
                if (draft.bucketUuid == receiverBucketUuid && !draft.closeRequested) {
                    draft.copy(
                        defaultAllocatedAmountCents = (portfolioMonthlyBudgetCents - namedOpenAllocationTotal)
                            .coerceAtLeast(0L)
                    )
                } else {
                    draft
                }
            }
            .sortedBy { it.sortOrder }
    }
}
