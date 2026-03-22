@file:Suppress("LargeClass", "LongMethod", "MaxLineLength", "ReturnCount")

package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.first
import net.loeu.wallybudget.data.local.dao.BudgetAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BudgetBucketDao
import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationPolicyDao
import net.loeu.wallybudget.data.local.db.TransactionRunner
import net.loeu.wallybudget.data.local.entity.BudgetAdjustmentEntity
import net.loeu.wallybudget.data.local.entity.toDomainModel
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.data.time.CurrentDateProvider
import net.loeu.wallybudget.domain.model.BudgetAdjustment
import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.BucketAllocationAdjustment
import net.loeu.wallybudget.domain.model.BucketAllocationPolicy
import net.loeu.wallybudget.domain.model.BucketBalanceBehavior
import net.loeu.wallybudget.domain.model.BucketTrackingMode
import net.loeu.wallybudget.domain.model.BudgetChangeMode
import net.loeu.wallybudget.domain.model.BudgetPolicy
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_NAME
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import net.loeu.wallybudget.domain.model.PendingPaydayUndo
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BudgetAdjustmentResolver
import net.loeu.wallybudget.domain.service.BucketAllocationResolver
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import net.loeu.wallybudget.domain.service.ImmediatePaydayChangePlan
import net.loeu.wallybudget.domain.service.ResolvedCyclePolicy
import net.loeu.wallybudget.domain.usecase.internal.deactivateInsertedAdjustment
import net.loeu.wallybudget.domain.usecase.internal.deactivateInsertedBucketAdjustment
import net.loeu.wallybudget.domain.usecase.internal.deactivateInsertedBucketPolicy
import net.loeu.wallybudget.domain.usecase.internal.deactivateInsertedPolicy
import net.loeu.wallybudget.domain.usecase.internal.newBudgetAdjustment
import net.loeu.wallybudget.domain.usecase.internal.newBudgetPolicy
import net.loeu.wallybudget.domain.usecase.internal.newBucketAllocationAdjustment
import net.loeu.wallybudget.domain.usecase.internal.newBucketAllocationPolicy
import net.loeu.wallybudget.domain.usecase.internal.restoreAdjustment
import net.loeu.wallybudget.domain.usecase.internal.restoreBucketAdjustment
import net.loeu.wallybudget.domain.usecase.internal.restoreBucketPolicy
import net.loeu.wallybudget.domain.usecase.internal.restorePolicy
import net.loeu.wallybudget.domain.usecase.internal.resolveSelectedOpenBucketUuid
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

data class BucketDraft(
    val bucketUuid: String,
    val name: String,
    val trackingMode: BucketTrackingMode = BucketTrackingMode.DAILY_TARGET,
    val balanceBehavior: BucketBalanceBehavior = BucketBalanceBehavior.RETURN_TO_PORTFOLIO,
    val defaultAllocatedAmountCents: Long,
    val sortOrder: Int,
    val closeRequested: Boolean = false
) {
    @Suppress("UNUSED_PARAMETER")
    constructor(
        bucketUuid: String,
        name: String,
        trackingMode: BucketTrackingMode,
        balanceBehavior: BucketBalanceBehavior,
        defaultAllocatedAmountCents: Long,
        sortOrder: Int,
        isPrimary: Boolean,
        closeRequested: Boolean = false
    ) : this(
        bucketUuid = bucketUuid,
        name = name,
        trackingMode = trackingMode,
        balanceBehavior = balanceBehavior,
        defaultAllocatedAmountCents = defaultAllocatedAmountCents,
        sortOrder = sortOrder,
        closeRequested = closeRequested
    )
}

data class UpdateBudgetSettingsRequest(
    val portfolioMonthlyBudgetCents: Long,
    val paydayDate: Int,
    val buckets: List<BucketDraft> = emptyList(),
    val budgetChangeMode: BudgetChangeMode
) {
    constructor(
        monthlyBudgetCents: Long,
        paydayDate: Int,
        budgetChangeMode: BudgetChangeMode,
        buckets: List<BucketDraft> = emptyList()
    ) : this(
        portfolioMonthlyBudgetCents = monthlyBudgetCents,
        paydayDate = paydayDate,
        buckets = buckets,
        budgetChangeMode = budgetChangeMode
    )

    @Deprecated("Use portfolioMonthlyBudgetCents instead.")
    val monthlyBudgetCents: Long
        get() = portfolioMonthlyBudgetCents
}

data class UpdateBudgetSettingsResult(
    val summaryMessage: String
)

private data class UpdateBudgetSettingsContext(
    val settings: UserSettings,
    val today: LocalDate,
    val policies: List<BudgetPolicy>,
    val currentPolicy: ResolvedCyclePolicy,
    val currentPolicyRecord: BudgetPolicy?,
    val currentAdjustments: List<BudgetAdjustment>,
    val futurePolicies: List<BudgetPolicy>,
    val paydayPlan: ImmediatePaydayChangePlan?,
    val buckets: List<BudgetBucket>,
    val bucketPolicies: List<BucketAllocationPolicy>,
    val bucketAdjustments: List<BucketAllocationAdjustment>
)

private data class UpdateBudgetSettingsMutation(
    val insertedPolicies: List<BudgetPolicy>,
    val activeAdjustments: List<BudgetAdjustment>,
    val activeBuckets: List<BudgetBucket>,
    val activeBucketPolicies: List<BucketAllocationPolicy>,
    val activeBucketAdjustments: List<BucketAllocationAdjustment>,
    val finalSelectedBucketUuid: String?
)

@Suppress("TooManyFunctions")
class UpdateBudgetSettingsUseCase(
    private val transactionRunner: TransactionRunner,
    private val userSettingsStore: UserSettingsStore,
    private val budgetPolicyDao: BudgetPolicyDao,
    private val budgetAdjustmentDao: BudgetAdjustmentDao,
    private val budgetBucketDao: BudgetBucketDao,
    private val bucketAllocationPolicyDao: BucketAllocationPolicyDao,
    private val bucketAllocationAdjustmentDao: BucketAllocationAdjustmentDao,
    private val currentDateProvider: CurrentDateProvider,
    private val cycleScheduleResolver: CycleScheduleResolver,
    private val budgetAdjustmentResolver: BudgetAdjustmentResolver,
    private val bucketAllocationResolver: BucketAllocationResolver,
    private val hybridLogicalClockService: HybridLogicalClockService
) {
    private val syncObservedDateUseCase = SyncObservedDateUseCase(userSettingsStore)

    suspend operator fun invoke(request: UpdateBudgetSettingsRequest): UpdateBudgetSettingsResult {
        val (context, bucketDrafts) = prepareUpdateContext(request)
        validateRequest(request, bucketDrafts, context)

        val settings = context.settings
        val shouldApplyBucketDrafts = request.buckets.isNotEmpty() || context.buckets.any { it.deletedAtEpochMs == null }
        val budgetChanged = request.portfolioMonthlyBudgetCents != settings.resolvedPortfolioMonthlyBudgetCents
        val paydayChanged = request.paydayDate != settings.paydayDate
        val bucketChanged = when {
            request.buckets.isEmpty() && context.buckets.none { it.deletedAtEpochMs == null } -> false
            else -> hasBucketChanges(bucketDrafts, context)
        }
        if (!budgetChanged && !paydayChanged && !bucketChanged) {
            return UpdateBudgetSettingsResult(summaryMessage = "No settings changed.")
        }

        val mutation = transactionRunner.inTransaction {
            if (budgetChanged && request.budgetChangeMode == BudgetChangeMode.PRORATE_CURRENT_CYCLE) {
                insertCurrentCycleAdjustment(context, request)
                normalizeCycleAdjustments(
                    settings = settings,
                    cycleStart = context.currentPolicy.cycleStart,
                    baseMonthlyBudgetCents = context.currentPolicy.budgetAmountCents
                )
            }

            val insertedPolicies = regeneratePolicies(
                settings = settings,
                request = request,
                today = context.today,
                currentPolicy = context.currentPolicy,
                currentPolicyRecord = context.currentPolicyRecord,
                futurePolicies = context.futurePolicies,
                paydayPlan = context.paydayPlan
            )

            val bucketMutation = if (shouldApplyBucketDrafts) {
                applyBucketDrafts(
                    context = context,
                    bucketDrafts = bucketDrafts
                )
            } else {
                BucketMutationResult(finalSelectedBucketUuid = settings.selectedBucketUuid)
            }

            UpdateBudgetSettingsMutation(
                insertedPolicies = insertedPolicies,
                activeAdjustments = budgetAdjustmentDao.getActiveForCycle(context.currentPolicy.cycleStart.toString())
                    .map { it.toDomainModel() },
                activeBuckets = budgetBucketDao.getAllForSnapshot()
                    .filter { it.deletedAtEpochMs == null }
                    .map { it.toDomainModel() },
                activeBucketPolicies = bucketAllocationPolicyDao.getAllForSnapshot()
                    .filter { it.deletedAtEpochMs == null }
                    .map { it.toDomainModel() },
                activeBucketAdjustments = bucketAllocationAdjustmentDao.getAllForSnapshot()
                    .filter { it.deletedAtEpochMs == null }
                    .map { it.toDomainModel() },
                finalSelectedBucketUuid = bucketMutation.finalSelectedBucketUuid
            )
        }

        persistUpdatedSettingsAndUndo(
            request = request,
            budgetChanged = budgetChanged,
            paydayChanged = paydayChanged,
            bucketChanged = bucketChanged,
            context = context,
            mutation = mutation
        )

        val rewrittenCurrentCycleEnd = context.paydayPlan?.rewrittenCurrentCycle?.cycleEndExclusive
            ?: context.currentPolicy.cycleEndExclusive
        val nextCycleStart = context.paydayPlan?.firstRegularCycle?.cycleStart
            ?: context.currentPolicy.cycleEndExclusive
        return UpdateBudgetSettingsResult(
            summaryMessage = buildSummaryMessage(
                settings = settings,
                request = request,
                effectiveDate = context.today,
                originalCurrentCycleEnd = context.currentPolicy.cycleEndExclusive,
                rewrittenCurrentCycleEnd = rewrittenCurrentCycleEnd,
                nextCycleStart = nextCycleStart,
                paydayChanged = paydayChanged,
                budgetChanged = budgetChanged,
                bucketChanged = bucketChanged
            )
        )
    }

    private suspend fun prepareUpdateContext(
        request: UpdateBudgetSettingsRequest
    ): Pair<UpdateBudgetSettingsContext, List<BucketDraft>> {
        var context = buildUpdateContext(request)
        var bucketDrafts = resolveBucketDrafts(request, context)
        val requestIncludesBucketChanges = when {
            request.buckets.isEmpty() && context.buckets.none { it.deletedAtEpochMs == null } -> false
            else -> hasBucketChanges(bucketDrafts, context)
        }
        if (!requestIncludesBucketChanges && restorePendingUndoBeforeApplyingNewSave()) {
            context = buildUpdateContext(request)
            bucketDrafts = resolveBucketDrafts(request, context)
        }
        return context to bucketDrafts
    }

    private suspend fun persistUpdatedSettingsAndUndo(
        request: UpdateBudgetSettingsRequest,
        budgetChanged: Boolean,
        paydayChanged: Boolean,
        bucketChanged: Boolean,
        context: UpdateBudgetSettingsContext,
        mutation: UpdateBudgetSettingsMutation
    ) {
        if (budgetChanged) {
            userSettingsStore.updatePortfolioMonthlyBudget(request.portfolioMonthlyBudgetCents)
        }
        if (paydayChanged) {
            userSettingsStore.updatePaydayDate(request.paydayDate)
        }
        userSettingsStore.updateSelectedBucket(mutation.finalSelectedBucketUuid)
        if ((budgetChanged || paydayChanged) && !bucketChanged) {
            userSettingsStore.savePendingPaydayUndo(
                buildPendingPaydayUndo(
                    context = context,
                    mutation = mutation
                )
            )
        } else {
            userSettingsStore.clearPendingPaydayUndo()
        }
    }

    private suspend fun restorePendingUndoBeforeApplyingNewSave(): Boolean {
        val settings = userSettingsStore.ensureIdentity()
        val today = syncObservedDateUseCase(settings, currentDateProvider.currentDate())
        val pendingUndo = userSettingsStore.pendingPaydayUndo.first() ?: return false
        if (!today.isBefore(pendingUndo.expiresAtExclusiveDate())) {
            userSettingsStore.clearPendingPaydayUndo()
            return false
        }

        val nowEpochMs = System.currentTimeMillis()
        transactionRunner.inTransaction {
            pendingUndo.policiesToDeactivate.forEach {
                deactivateInsertedPolicy(budgetPolicyDao, it, settings.installDeviceId, nowEpochMs, hybridLogicalClockService)
            }
            pendingUndo.policiesToRestore.forEach { restorePolicy(budgetPolicyDao, it) }
            pendingUndo.adjustmentsToDeactivate.forEach {
                deactivateInsertedAdjustment(budgetAdjustmentDao, it, settings.installDeviceId, nowEpochMs, hybridLogicalClockService)
            }
            pendingUndo.adjustmentsToRestore.forEach { restoreAdjustment(budgetAdjustmentDao, it) }
            pendingUndo.bucketPoliciesToDeactivate.forEach {
                deactivateInsertedBucketPolicy(bucketAllocationPolicyDao, it, settings.installDeviceId, nowEpochMs, hybridLogicalClockService)
            }
            pendingUndo.bucketPoliciesToRestore.forEach { restoreBucketPolicy(bucketAllocationPolicyDao, it) }
            pendingUndo.bucketAdjustmentsToDeactivate.forEach {
                deactivateInsertedBucketAdjustment(bucketAllocationAdjustmentDao, it, settings.installDeviceId, nowEpochMs, hybridLogicalClockService)
            }
            pendingUndo.bucketAdjustmentsToRestore.forEach {
                restoreBucketAdjustment(bucketAllocationAdjustmentDao, it)
            }
        }
        userSettingsStore.restoreFromSnapshot(
            settings = pendingUndo.previousSettings,
            onboardingCompleted = pendingUndo.previousSettings.isOnboardingCompleted
        )
        userSettingsStore.clearPendingPaydayUndo()
        return true
    }

    private suspend fun buildUpdateContext(
        request: UpdateBudgetSettingsRequest
    ): UpdateBudgetSettingsContext {
        val settings = userSettingsStore.ensureIdentity()
        val today = syncObservedDateUseCase(settings, currentDateProvider.currentDate())
        val policies = budgetPolicyDao.getAllForSnapshot()
            .filter { it.deletedAtEpochMs == null }
            .map { it.toDomainModel() }
            .sortedBy { it.cycleStartDate }
        val currentPolicy = cycleScheduleResolver.resolvePolicyForDate(today, settings, policies)
        val currentPolicyRecord = policies.firstOrNull { policy ->
            policy.cycleStart() == currentPolicy.cycleStart &&
                policy.cycleEndExclusive() == currentPolicy.cycleEndExclusive
        }
        val currentAdjustments = budgetAdjustmentDao.getActiveForCycle(currentPolicy.cycleStart.toString())
            .map { it.toDomainModel() }
        val futurePolicies = policies
            .filter { !it.cycleStart().isBefore(currentPolicy.cycleEndExclusive) }
            .sortedBy { it.cycleStartDate }
        val paydayPlan = request.paydayDate.takeIf { it != settings.paydayDate }?.let { targetPayday ->
            cycleScheduleResolver.planImmediatePaydayChange(
                currentCycle = currentPolicy,
                today = today,
                targetMonthlyBudgetCents = request.portfolioMonthlyBudgetCents,
                newPaydayDayOfMonth = targetPayday
            )
        }
        return UpdateBudgetSettingsContext(
            settings = settings,
            today = today,
            policies = policies,
            currentPolicy = currentPolicy,
            currentPolicyRecord = currentPolicyRecord,
            currentAdjustments = currentAdjustments,
            futurePolicies = futurePolicies,
            paydayPlan = paydayPlan,
            buckets = budgetBucketDao.getAllForSnapshot()
                .map { it.toDomainModel() },
            bucketPolicies = bucketAllocationPolicyDao.getAllForSnapshot()
                .map { it.toDomainModel() },
            bucketAdjustments = bucketAllocationAdjustmentDao.getAllForSnapshot()
                .map { it.toDomainModel() }
        )
    }

    private fun resolveBucketDrafts(
        request: UpdateBudgetSettingsRequest,
        context: UpdateBudgetSettingsContext
    ): List<BucketDraft> {
        if (request.buckets.isNotEmpty()) {
            return normalizeBucketDrafts(
                drafts = request.buckets.sortedBy { it.sortOrder },
                context = context,
                portfolioMonthlyBudgetCents = request.portfolioMonthlyBudgetCents
            )
        }
        if (context.buckets.none { it.deletedAtEpochMs == null }) {
            return normalizeBucketDrafts(
                drafts = listOf(
                BucketDraft(
                    bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                    name = DEFAULT_SPENDING_BUCKET_NAME,
                    trackingMode = BucketTrackingMode.DAILY_TARGET,
                    balanceBehavior = BucketBalanceBehavior.RETURN_TO_PORTFOLIO,
                    defaultAllocatedAmountCents = context.settings.monthlyBudgetCents,
                    sortOrder = 0,
                    closeRequested = false
                )
                ),
                context = context,
                portfolioMonthlyBudgetCents = request.portfolioMonthlyBudgetCents
            )
        }
        return normalizeBucketDrafts(
            drafts = context.buckets
                .filter { it.deletedAtEpochMs == null }
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
            portfolioMonthlyBudgetCents = request.portfolioMonthlyBudgetCents
        )
    }

    private fun validateRequest(
        request: UpdateBudgetSettingsRequest,
        bucketDrafts: List<BucketDraft>,
        context: UpdateBudgetSettingsContext
    ) {
        require(request.portfolioMonthlyBudgetCents > 0L) {
            "Portfolio budget must be greater than zero."
        }
        require(request.paydayDate in 1..31) {
            "Payday must be between 1 and 31."
        }

        val existingByUuid = context.buckets.associateBy { it.bucketUuid }
        bucketDrafts.forEach { draft ->
            require(draft.name.isNotBlank()) { "Bucket name cannot be blank." }
            require(draft.defaultAllocatedAmountCents >= 0L) { "Bucket allocation cannot be negative." }
            val existing = existingByUuid[draft.bucketUuid]
            require(existing == null || existing.deletedAtEpochMs != null || !existing.isClosed || draft.closeRequested) {
                "Closed buckets cannot be reopened."
            }
        }

        val openBuckets = bucketDrafts.filterNot { it.closeRequested }
        require(openBuckets.isNotEmpty()) { "At least one bucket must remain open." }
        val defaultBucket = openBuckets.firstOrNull { it.bucketUuid == DEFAULT_SPENDING_BUCKET_UUID }
            ?: throw IllegalArgumentException("The default bucket must remain open.")
        require(defaultBucket.trackingMode == BucketTrackingMode.DAILY_TARGET) {
            "The default bucket tracking mode is fixed."
        }
        require(defaultBucket.balanceBehavior == BucketBalanceBehavior.RETURN_TO_PORTFOLIO) {
            "The default bucket balance behavior is fixed."
        }

        val duplicateName = openBuckets
            .groupBy { it.name.trim().lowercase(Locale.getDefault()) }
            .values
            .firstOrNull { it.size > 1 }
        require(duplicateName == null) { "Bucket names must be unique." }

        require(
            openBuckets
                .filterNot { it.bucketUuid == DEFAULT_SPENDING_BUCKET_UUID }
                .sumOf { it.defaultAllocatedAmountCents } <= request.portfolioMonthlyBudgetCents
        ) {
            "Bucket allocations cannot exceed the portfolio budget."
        }
    }

    private fun hasBucketChanges(
        bucketDrafts: List<BucketDraft>,
        context: UpdateBudgetSettingsContext
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

    private suspend fun insertCurrentCycleAdjustment(
        context: UpdateBudgetSettingsContext,
        request: UpdateBudgetSettingsRequest
    ): BudgetAdjustment? {
        val currentMonthlyBudget = budgetAdjustmentResolver.currentMonthlyBudget(
            cycleStart = context.currentPolicy.cycleStart,
            cycleEndExclusive = context.currentPolicy.cycleEndExclusive,
            baseMonthlyBudgetCents = context.currentPolicy.budgetAmountCents,
            adjustments = context.currentAdjustments,
            onDate = context.today
        )
        if (currentMonthlyBudget == request.portfolioMonthlyBudgetCents) {
            return null
        }
        val adjustment = newBudgetAdjustment(
            cycleStart = context.currentPolicy.cycleStart,
            effectiveDate = context.today,
            previousMonthlyBudgetCents = currentMonthlyBudget,
            newMonthlyBudgetCents = request.portfolioMonthlyBudgetCents,
            installId = context.settings.installDeviceId,
            nowEpochMs = System.currentTimeMillis(),
            hybridLogicalClockService = hybridLogicalClockService
        )
        budgetAdjustmentDao.insert(adjustment.toEntity())
        return adjustment
    }

    private suspend fun normalizeCycleAdjustments(
        settings: UserSettings,
        cycleStart: LocalDate,
        baseMonthlyBudgetCents: Long
    ) {
        val activeAdjustments = budgetAdjustmentDao.getActiveForCycle(cycleStart.toString())
        if (activeAdjustments.isEmpty()) {
            return
        }

        val nowEpochMs = System.currentTimeMillis()
        val redundantAdjustmentUuids = mutableSetOf<String>()
        var currentBudget = baseMonthlyBudgetCents

        activeAdjustments
            .groupBy { it.effectiveDate }
            .toSortedMap()
            .values
            .forEach { sameDayAdjustments ->
                sameDayAdjustments.dropLast(1).forEach { redundantAdjustmentUuids += it.adjustmentUuid }
                val latestAdjustment = sameDayAdjustments.last()
                if (latestAdjustment.newMonthlyBudgetCents == currentBudget) {
                    redundantAdjustmentUuids += latestAdjustment.adjustmentUuid
                } else {
                    if (latestAdjustment.previousMonthlyBudgetCents != currentBudget) {
                        budgetAdjustmentDao.update(
                            normalizedAdjustmentEntity(
                                entity = latestAdjustment,
                                previousMonthlyBudgetCents = currentBudget,
                                settings = settings,
                                nowEpochMs = nowEpochMs
                            )
                        )
                    }
                    currentBudget = latestAdjustment.newMonthlyBudgetCents
                }
            }

        if (redundantAdjustmentUuids.isNotEmpty()) {
            budgetAdjustmentDao.deleteByAdjustmentUuids(redundantAdjustmentUuids.toList())
        }
    }

    private suspend fun applyBucketDrafts(
        context: UpdateBudgetSettingsContext,
        bucketDrafts: List<BucketDraft>
    ): BucketMutationResult {
        val settings = context.settings
        val nowEpochMs = context.today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val existingByUuid = context.buckets.associateBy { it.bucketUuid }
        val openDrafts = bucketDrafts.filterNot { it.closeRequested }
        val selectedBucketUuid = resolveSelectedOpenBucketUuid(
            selectedBucketUuid = settings.selectedBucketUuid,
            openBuckets = openDrafts.map { draft ->
                existingByUuid[draft.bucketUuid]?.copy(
                    name = draft.name.trim(),
                    defaultAllocatedAmountCents = draft.defaultAllocatedAmountCents,
                    sortOrder = draft.sortOrder,
                    closedAtEpochMs = null,
                    deletedAtEpochMs = null
                ) ?: BudgetBucket(
                    bucketUuid = draft.bucketUuid.ifBlank { UUID.randomUUID().toString() },
                    name = draft.name.trim(),
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

        bucketDrafts
            .sortedBy { it.sortOrder }
            .forEach { draft ->
                val existing = existingByUuid[draft.bucketUuid]
                if (existing == null) {
                    insertNewBucket(
                        settings = settings,
                        draft = draft,
                        nowEpochMs = nowEpochMs
                    )
                    if (!draft.closeRequested) {
                        if (draft.bucketUuid == DEFAULT_SPENDING_BUCKET_UUID) {
                            upsertCurrentCycleDefaultBucketPolicy(
                                draft = draft,
                                context = context,
                                nowEpochMs = nowEpochMs
                            )
                        } else {
                            ensureCurrentCycleBucketPolicy(
                                bucketUuid = draft.bucketUuid,
                                allocatedAmountCents = draft.defaultAllocatedAmountCents,
                                context = context,
                                nowEpochMs = nowEpochMs
                            )
                        }
                    }
                    return@forEach
                }

                updateExistingBucket(
                    existing = existing,
                    draft = draft,
                    settings = settings,
                    nowEpochMs = nowEpochMs
                )

                if (draft.closeRequested) {
                    zeroClosedBucket(
                        bucket = existing,
                        context = context,
                        nowEpochMs = nowEpochMs
                    )
                    softDeleteFutureBucketPoliciesAndAdjustments(
                        bucketUuid = draft.bucketUuid,
                        context = context,
                        currentCycleEndExclusive = context.currentPolicy.cycleEndExclusive,
                        settings = settings,
                        nowEpochMs = nowEpochMs
                    )
                } else {
                    if (draft.bucketUuid == DEFAULT_SPENDING_BUCKET_UUID) {
                        upsertCurrentCycleDefaultBucketPolicy(
                            draft = draft,
                            context = context,
                            nowEpochMs = nowEpochMs
                        )
                    } else {
                        ensureCurrentCycleBucketPolicy(
                            bucketUuid = draft.bucketUuid,
                            allocatedAmountCents = existing.defaultAllocatedAmountCents,
                            context = context,
                            nowEpochMs = nowEpochMs
                        )
                        updateCurrentCycleBucketAllocationIfNeeded(
                            bucket = existing,
                            draft = draft,
                            context = context,
                            nowEpochMs = nowEpochMs
                        )
                    }
                    softDeleteFutureBucketPoliciesAndAdjustments(
                        bucketUuid = draft.bucketUuid,
                        context = context,
                        currentCycleEndExclusive = context.currentPolicy.cycleEndExclusive,
                        settings = settings,
                        nowEpochMs = nowEpochMs
                    )
                }
            }

        return BucketMutationResult(
            finalSelectedBucketUuid = selectedBucketUuid
        )
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

    private suspend fun upsertCurrentCycleDefaultBucketPolicy(
        draft: BucketDraft,
        context: UpdateBudgetSettingsContext,
        nowEpochMs: Long
    ) {
        val existing = bucketAllocationPolicyDao.findActivePolicyForCycle(
            bucketUuid = draft.bucketUuid,
            cycleStartDate = context.currentPolicy.cycleStart.toString()
        )
        if (existing == null) {
            ensureCurrentCycleBucketPolicy(
                bucketUuid = draft.bucketUuid,
                allocatedAmountCents = draft.defaultAllocatedAmountCents,
                context = context,
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
        val activeAdjustments = bucketAllocationAdjustmentDao.getActiveForCycle(
            bucketUuid = draft.bucketUuid,
            cycleStartDate = context.currentPolicy.cycleStart.toString()
        )
        if (activeAdjustments.isNotEmpty()) {
            bucketAllocationAdjustmentDao.deleteByAdjustmentUuids(activeAdjustments.map { it.adjustmentUuid })
        }
    }

    private suspend fun ensureCurrentCycleBucketPolicy(
        bucketUuid: String,
        allocatedAmountCents: Long,
        context: UpdateBudgetSettingsContext,
        nowEpochMs: Long
    ) {
        val existing = bucketAllocationPolicyDao.findActivePolicyForCycle(
            bucketUuid = bucketUuid,
            cycleStartDate = context.currentPolicy.cycleStart.toString()
        )
        if (existing != null) return
        bucketAllocationPolicyDao.insert(
            newBucketAllocationPolicy(
                bucketUuid = bucketUuid,
                cycleStart = context.currentPolicy.cycleStart,
                cycleEndExclusive = context.currentPolicy.cycleEndExclusive,
                allocatedAmountCents = allocatedAmountCents,
                installId = context.settings.installDeviceId,
                nowEpochMs = nowEpochMs,
                hybridLogicalClockService = hybridLogicalClockService
            ).toEntity()
        )
    }

    private suspend fun updateCurrentCycleBucketAllocationIfNeeded(
        bucket: BudgetBucket,
        draft: BucketDraft,
        context: UpdateBudgetSettingsContext,
        nowEpochMs: Long
    ) {
        val currentPolicyEntity = bucketAllocationPolicyDao.findActivePolicyForCycle(
            bucketUuid = bucket.bucketUuid,
            cycleStartDate = context.currentPolicy.cycleStart.toString()
        )
        val baseAllocationCents = currentPolicyEntity?.allocatedAmountCents ?: bucket.defaultAllocatedAmountCents
        val currentAdjustments = bucketAllocationAdjustmentDao.getActiveForCycle(
            bucketUuid = bucket.bucketUuid,
            cycleStartDate = context.currentPolicy.cycleStart.toString()
        ).map { it.toDomainModel() }
        val currentAllocation = bucketAllocationResolver.currentAllocatedAmount(
            cycleStart = context.currentPolicy.cycleStart,
            cycleEndExclusive = context.currentPolicy.cycleEndExclusive,
            baseAllocatedAmountCents = baseAllocationCents,
            adjustments = currentAdjustments,
            onDate = context.today
        )
        if (currentAllocation == draft.defaultAllocatedAmountCents) {
            return
        }
        bucketAllocationAdjustmentDao.insert(
            newBucketAllocationAdjustment(
                bucketUuid = bucket.bucketUuid,
                cycleStart = context.currentPolicy.cycleStart,
                effectiveDate = context.today,
                previousAllocatedAmountCents = currentAllocation,
                newAllocatedAmountCents = draft.defaultAllocatedAmountCents,
                installId = context.settings.installDeviceId,
                nowEpochMs = nowEpochMs,
                hybridLogicalClockService = hybridLogicalClockService
            ).toEntity()
        )
        normalizeBucketAdjustments(
            bucketUuid = bucket.bucketUuid,
            settings = context.settings,
            cycleStart = context.currentPolicy.cycleStart,
            baseAllocatedAmountCents = baseAllocationCents
        )
    }

    private suspend fun zeroClosedBucket(
        bucket: BudgetBucket,
        context: UpdateBudgetSettingsContext,
        nowEpochMs: Long
    ) {
        val currentPolicyEntity = bucketAllocationPolicyDao.findActivePolicyForCycle(
            bucketUuid = bucket.bucketUuid,
            cycleStartDate = context.currentPolicy.cycleStart.toString()
        )
        val baseAllocationCents = currentPolicyEntity?.allocatedAmountCents ?: bucket.defaultAllocatedAmountCents
        val currentAdjustments = bucketAllocationAdjustmentDao.getActiveForCycle(
            bucketUuid = bucket.bucketUuid,
            cycleStartDate = context.currentPolicy.cycleStart.toString()
        ).map { it.toDomainModel() }
        val currentAllocation = bucketAllocationResolver.currentAllocatedAmount(
            cycleStart = context.currentPolicy.cycleStart,
            cycleEndExclusive = context.currentPolicy.cycleEndExclusive,
            baseAllocatedAmountCents = baseAllocationCents,
            adjustments = currentAdjustments,
            onDate = context.today
        )
        if (currentAllocation == 0L) {
            return
        }
        bucketAllocationAdjustmentDao.insert(
            newBucketAllocationAdjustment(
                bucketUuid = bucket.bucketUuid,
                cycleStart = context.currentPolicy.cycleStart,
                effectiveDate = context.today,
                previousAllocatedAmountCents = currentAllocation,
                newAllocatedAmountCents = 0L,
                installId = context.settings.installDeviceId,
                nowEpochMs = nowEpochMs,
                hybridLogicalClockService = hybridLogicalClockService
            ).toEntity()
        )
        normalizeBucketAdjustments(
            bucketUuid = bucket.bucketUuid,
            settings = context.settings,
            cycleStart = context.currentPolicy.cycleStart,
            baseAllocatedAmountCents = baseAllocationCents
        )
    }

    private suspend fun softDeleteFutureBucketPoliciesAndAdjustments(
        bucketUuid: String,
        context: UpdateBudgetSettingsContext,
        currentCycleEndExclusive: LocalDate,
        settings: UserSettings,
        nowEpochMs: Long
    ) {
        context.bucketPolicies
            .filter { it.bucketUuid == bucketUuid }
            .filter { it.deletedAtEpochMs == null && !it.cycleStart().isBefore(currentCycleEndExclusive) }
            .forEach { policy ->
                deactivateInsertedBucketPolicy(bucketAllocationPolicyDao, policy, settings.installDeviceId, nowEpochMs, hybridLogicalClockService)
            }
        context.bucketAdjustments
            .filter { it.bucketUuid == bucketUuid }
            .filter { it.deletedAtEpochMs == null && !it.cycleStart().isBefore(currentCycleEndExclusive) }
            .forEach { adjustment ->
                deactivateInsertedBucketAdjustment(bucketAllocationAdjustmentDao, adjustment, settings.installDeviceId, nowEpochMs, hybridLogicalClockService)
            }
    }

    private suspend fun normalizeBucketAdjustments(
        bucketUuid: String,
        settings: UserSettings,
        cycleStart: LocalDate,
        baseAllocatedAmountCents: Long
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

    private fun normalizedAdjustmentEntity(
        entity: BudgetAdjustmentEntity,
        previousMonthlyBudgetCents: Long,
        settings: UserSettings,
        nowEpochMs: Long
    ): BudgetAdjustmentEntity {
        return entity.copy(
            previousMonthlyBudgetCents = previousMonthlyBudgetCents,
            updatedAtEpochMs = nowEpochMs,
            lastModifiedByInstallId = settings.installDeviceId,
            modClock = hybridLogicalClockService.next(
                previousClock = entity.modClock,
                nowEpochMs = nowEpochMs,
                installId = settings.installDeviceId
            )
        )
    }

    private suspend fun regeneratePolicies(
        settings: UserSettings,
        request: UpdateBudgetSettingsRequest,
        today: LocalDate,
        currentPolicy: ResolvedCyclePolicy,
        currentPolicyRecord: BudgetPolicy?,
        futurePolicies: List<BudgetPolicy>,
        paydayPlan: ImmediatePaydayChangePlan?
    ): List<BudgetPolicy> {
        if (paydayPlan == null && futurePolicies.isEmpty()) {
            return emptyList()
        }

        val nowEpochMs = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        softDeletePolicies(futurePolicies, settings, nowEpochMs)

        val insertedPolicies = mutableListOf<BudgetPolicy>()
        if (paydayPlan != null) {
            currentPolicyRecord?.let { softDeletePolicy(it, settings, nowEpochMs) }
            insertedPolicies += insertBudgetPolicy(
                settings = settings,
                cycleStart = paydayPlan.rewrittenCurrentCycle.cycleStart,
                cycleEndExclusive = paydayPlan.rewrittenCurrentCycle.cycleEndExclusive,
                budgetAmountCents = paydayPlan.rewrittenCurrentCycle.budgetAmountCents,
                paydayDayOfMonth = paydayPlan.rewrittenCurrentCycle.paydayDayOfMonth,
                nowEpochMs = nowEpochMs
            )
            insertedPolicies += insertBudgetPolicy(
                settings = settings,
                cycleStart = paydayPlan.firstRegularCycle.cycleStart,
                cycleEndExclusive = paydayPlan.firstRegularCycle.cycleEndExclusive,
                budgetAmountCents = paydayPlan.firstRegularCycle.budgetAmountCents,
                paydayDayOfMonth = paydayPlan.firstRegularCycle.paydayDayOfMonth,
                nowEpochMs = nowEpochMs
            )
        } else {
            insertedPolicies += insertNextCyclePolicy(
                settings = settings,
                currentPolicy = currentPolicy,
                targetBudget = request.portfolioMonthlyBudgetCents,
                targetPayday = request.paydayDate,
                nowEpochMs = nowEpochMs
            )
        }
        return insertedPolicies
    }

    private suspend fun softDeletePolicies(
        policies: List<BudgetPolicy>,
        settings: UserSettings,
        nowEpochMs: Long
    ) {
        policies.forEach { policy ->
            softDeletePolicy(policy, settings, nowEpochMs)
        }
    }

    private suspend fun softDeletePolicy(
        policy: BudgetPolicy,
        settings: UserSettings,
        nowEpochMs: Long
    ) {
        val entity = budgetPolicyDao.findByPolicyUuid(policy.policyUuid) ?: return
        budgetPolicyDao.update(
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

    private suspend fun insertNextCyclePolicy(
        settings: UserSettings,
        currentPolicy: ResolvedCyclePolicy,
        targetBudget: Long,
        targetPayday: Int,
        nowEpochMs: Long
    ): BudgetPolicy {
        val nextCycleStart = currentPolicy.cycleEndExclusive
        val nextCycleEndExclusive = cycleScheduleResolver.policyForCycleStart(
            cycleStart = nextCycleStart,
            settings = settings.copy(
                paydayDate = targetPayday,
                portfolioMonthlyBudgetCents = targetBudget
            ),
            policies = emptyList()
        ).cycleEndExclusive
        return insertBudgetPolicy(
            settings = settings,
            cycleStart = nextCycleStart,
            cycleEndExclusive = nextCycleEndExclusive,
            budgetAmountCents = targetBudget,
            paydayDayOfMonth = targetPayday,
            nowEpochMs = nowEpochMs
        )
    }

    private suspend fun insertBudgetPolicy(
        settings: UserSettings,
        cycleStart: LocalDate,
        cycleEndExclusive: LocalDate,
        budgetAmountCents: Long,
        paydayDayOfMonth: Int,
        nowEpochMs: Long
    ): BudgetPolicy {
        val policy = newBudgetPolicy(
            cycleStart = cycleStart,
            cycleEndExclusive = cycleEndExclusive,
            budgetAmountCents = budgetAmountCents,
            paydayDayOfMonth = paydayDayOfMonth,
            installId = settings.installDeviceId,
            nowEpochMs = nowEpochMs,
            hybridLogicalClockService = hybridLogicalClockService
        )
        budgetPolicyDao.insert(policy.toEntity())
        return policy
    }

    private fun buildPendingPaydayUndo(
        context: UpdateBudgetSettingsContext,
        mutation: UpdateBudgetSettingsMutation
    ): PendingPaydayUndo {
        val policiesToRestore = context.policies.filter { it.deletedAtEpochMs == null }
        val adjustmentsToRestore = context.currentAdjustments
        return PendingPaydayUndo(
            previousSettings = context.settings,
            policiesToRestore = policiesToRestore,
            policiesToDeactivate = mutation.insertedPolicies,
            adjustmentsToRestore = adjustmentsToRestore,
            adjustmentsToDeactivate = mutation.activeAdjustments,
            expiresAtExclusive = listOfNotNull(
                context.paydayPlan?.rewrittenCurrentCycle?.cycleEndExclusive,
                context.currentPolicy.cycleEndExclusive
            ).min().toString()
        )
    }

    private fun buildSummaryMessage(
        settings: UserSettings,
        request: UpdateBudgetSettingsRequest,
        effectiveDate: LocalDate,
        originalCurrentCycleEnd: LocalDate,
        rewrittenCurrentCycleEnd: LocalDate,
        nextCycleStart: LocalDate,
        paydayChanged: Boolean,
        budgetChanged: Boolean,
        bucketChanged: Boolean
    ): String {
        val parts = mutableListOf<String>()
        if (budgetChanged) {
            parts += when (request.budgetChangeMode) {
                BudgetChangeMode.PRORATE_CURRENT_CYCLE -> "Portfolio budget prorated from $effectiveDate."
                BudgetChangeMode.APPLY_NEXT_CYCLE -> "Portfolio budget changes on $nextCycleStart."
            }
        }
        if (paydayChanged) {
            parts += "Payday switches from ${settings.paydayDate} to ${request.paydayDate} now."
            parts += when {
                rewrittenCurrentCycleEnd.isBefore(originalCurrentCycleEnd) ->
                    "This cycle now ends on $rewrittenCurrentCycleEnd."
                rewrittenCurrentCycleEnd.isAfter(originalCurrentCycleEnd) ->
                    "This cycle extends to $rewrittenCurrentCycleEnd."
                else ->
                    "This cycle still ends on $rewrittenCurrentCycleEnd."
            }
        }
        if (bucketChanged) {
            parts += "Bucket settings updated."
        }
        return parts.joinToString(" ").ifBlank { "No settings changed." }
    }

    private data class BucketMutationResult(
        val finalSelectedBucketUuid: String?
    )

    private fun normalizeBucketDrafts(
        drafts: List<BucketDraft>,
        context: UpdateBudgetSettingsContext,
        portfolioMonthlyBudgetCents: Long
    ): List<BucketDraft> {
        val existingDefaultBucket = context.buckets.firstOrNull { it.bucketUuid == DEFAULT_SPENDING_BUCKET_UUID }
        val providedDefaultBucket = drafts.firstOrNull { it.bucketUuid == DEFAULT_SPENDING_BUCKET_UUID }
        val namedOpenAllocationTotal = drafts
            .filterNot { it.closeRequested || it.bucketUuid == DEFAULT_SPENDING_BUCKET_UUID }
            .sumOf { it.defaultAllocatedAmountCents }
        val normalizedDrafts = drafts
            .filterNot { it.bucketUuid == DEFAULT_SPENDING_BUCKET_UUID }
            .map { it.copy(name = it.name.trim()) }
            .toMutableList()
        normalizedDrafts += BucketDraft(
            bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
            name = when {
                providedDefaultBucket != null -> providedDefaultBucket.name.trim()
                existingDefaultBucket != null -> existingDefaultBucket.name
                else -> DEFAULT_SPENDING_BUCKET_NAME
            }.ifBlank { DEFAULT_SPENDING_BUCKET_NAME },
            trackingMode = BucketTrackingMode.DAILY_TARGET,
            balanceBehavior = BucketBalanceBehavior.RETURN_TO_PORTFOLIO,
            defaultAllocatedAmountCents = (portfolioMonthlyBudgetCents - namedOpenAllocationTotal).coerceAtLeast(0L),
            sortOrder = 0,
            closeRequested = false
        )
        return normalizedDrafts.sortedBy { it.sortOrder }
    }
}
