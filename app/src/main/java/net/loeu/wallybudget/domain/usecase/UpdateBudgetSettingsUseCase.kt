@file:Suppress("LargeClass", "LongMethod", "MaxLineLength", "ReturnCount")

package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.first
import net.loeu.wallybudget.data.planning.BudgetSettingsPlanningMutationApplier
import net.loeu.wallybudget.data.planning.DefaultPlanningRepository
import net.loeu.wallybudget.data.planning.BudgetSettingsPlanningMutationContext
import net.loeu.wallybudget.data.local.dao.BudgetAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BudgetBucketDao
import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationPolicyDao
import net.loeu.wallybudget.data.local.db.TransactionRunner
import net.loeu.wallybudget.data.local.entity.BudgetAdjustmentEntity
import net.loeu.wallybudget.data.local.entity.toDomainModel as adjustmentToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel as bucketAdjustmentToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel as bucketPolicyToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel as bucketToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel as policyToDomainModel
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
import net.loeu.wallybudget.domain.planning.PlanningConfig
import net.loeu.wallybudget.domain.planning.PlanningContext
import net.loeu.wallybudget.domain.planning.PlanningEngine
import net.loeu.wallybudget.domain.planning.SavePlanningRequest
import net.loeu.wallybudget.domain.planning.toDraft
import net.loeu.wallybudget.domain.planning.toPlanningBucket
import net.loeu.wallybudget.domain.service.BudgetAdjustmentResolver
import net.loeu.wallybudget.domain.service.BucketAllocationResolver
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import net.loeu.wallybudget.domain.service.ImmediatePaydayChangePlan
import net.loeu.wallybudget.domain.service.ResolvedCyclePolicy
import net.loeu.wallybudget.domain.usecase.internal.newBudgetAdjustment
import net.loeu.wallybudget.domain.usecase.internal.newBudgetPolicy
import net.loeu.wallybudget.domain.usecase.internal.newBucketAllocationAdjustment
import net.loeu.wallybudget.domain.usecase.internal.newBucketAllocationPolicy
import net.loeu.wallybudget.domain.usecase.internal.resolveSelectedOpenBucketUuid
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class UpdateBudgetSettingsRequest(
    val portfolioMonthlyBudgetCents: Long,
    val paydayDate: Int,
    val leftoverReceiverBucketUuid: String? = null,
    val buckets: List<BucketDraft> = emptyList(),
    val budgetChangeMode: BudgetChangeMode
) {
    constructor(
        monthlyBudgetCents: Long,
        paydayDate: Int,
        budgetChangeMode: BudgetChangeMode,
        leftoverReceiverBucketUuid: String? = null,
        buckets: List<BucketDraft> = emptyList()
    ) : this(
        portfolioMonthlyBudgetCents = monthlyBudgetCents,
        paydayDate = paydayDate,
        leftoverReceiverBucketUuid = leftoverReceiverBucketUuid,
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
    private val planningEngine = PlanningEngine()
    private val planningRepository = DefaultPlanningRepository(userSettingsStore)
    private val planningMutationApplier = BudgetSettingsPlanningMutationApplier(
        budgetBucketDao = budgetBucketDao,
        bucketAllocationPolicyDao = bucketAllocationPolicyDao,
        bucketAllocationAdjustmentDao = bucketAllocationAdjustmentDao,
        hybridLogicalClockService = hybridLogicalClockService,
        bucketAllocationResolver = bucketAllocationResolver
    )

    @Suppress("CyclomaticComplexMethod")
    suspend operator fun invoke(request: UpdateBudgetSettingsRequest): UpdateBudgetSettingsResult {
        val (context, bucketDrafts) = prepareUpdateContext(request)
        val planningChangeSet = planningEngine.buildChangeSet(
            context = context.toPlanningContext(),
            request = request.toPlanningRequest(bucketDrafts)
        )
        require(planningChangeSet.state.isValid) {
            planningChangeSet.state.validationErrors.first()
        }

        val settings = context.settings
        val shouldApplyBucketDrafts = request.buckets.isNotEmpty() || context.buckets.any { it.deletedAtEpochMs == null }
        val budgetChanged = request.portfolioMonthlyBudgetCents != settings.resolvedPortfolioMonthlyBudgetCents
        val paydayChanged = request.paydayDate != settings.paydayDate
        val bucketChanged = when {
            request.buckets.isEmpty() && context.buckets.none { it.deletedAtEpochMs == null } -> false
            else -> planningChangeSet.bucketChanged
        }
        val resolvedLeftoverReceiverBucketUuid = if (shouldApplyBucketDrafts) {
            planningChangeSet.state.resolvedLeftoverReceiverBucketUuid
        } else {
            settings.leftoverReceiverBucketUuid
        }
        val leftoverReceiverChanged = planningChangeSet.leftoverReceiverChanged
        if (hasNoSettingsChanges(budgetChanged, paydayChanged, bucketChanged, leftoverReceiverChanged)) {
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
                BucketMutationResult(
                    finalSelectedBucketUuid = planningMutationApplier.apply(
                        context = context.toMutationContext(),
                        bucketDrafts = planningChangeSet.state.normalizedDrafts,
                        leftoverReceiverBucketUuid = resolvedLeftoverReceiverBucketUuid
                    )
                )
            } else BucketMutationResult(finalSelectedBucketUuid = settings.selectedBucketUuid)

            UpdateBudgetSettingsMutation(
                insertedPolicies = insertedPolicies,
                activeAdjustments = budgetAdjustmentDao.getActiveForCycle(context.currentPolicy.cycleStart.toString())
                    .map { it.adjustmentToDomainModel() },
                activeBuckets = budgetBucketDao.getAllForSnapshot()
                    .filter { it.deletedAtEpochMs == null }
                    .map { it.bucketToDomainModel() },
                activeBucketPolicies = bucketAllocationPolicyDao.getAllForSnapshot()
                    .filter { it.deletedAtEpochMs == null }
                    .map { it.bucketPolicyToDomainModel() },
                activeBucketAdjustments = bucketAllocationAdjustmentDao.getAllForSnapshot()
                    .filter { it.deletedAtEpochMs == null }
                    .map { it.bucketAdjustmentToDomainModel() },
                finalSelectedBucketUuid = bucketMutation.finalSelectedBucketUuid
            )
        }

        persistUpdatedSettingsAndUndo(
            request = request,
            budgetChanged = budgetChanged,
            paydayChanged = paydayChanged,
            bucketChanged = bucketChanged || leftoverReceiverChanged,
            leftoverReceiverBucketUuid = resolvedLeftoverReceiverBucketUuid,
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
                bucketChanged = bucketChanged || leftoverReceiverChanged
            )
        )
    }

    private suspend fun prepareUpdateContext(
        request: UpdateBudgetSettingsRequest
    ): Pair<UpdateBudgetSettingsContext, List<BucketDraft>> {
        var context = buildUpdateContext(request)
        var bucketDrafts = planningEngine.normalize(
            context = context.toPlanningContext(),
            request = request.toPlanningRequest()
        ).normalizedDrafts
        val requestIncludesBucketChanges = when {
            request.buckets.isEmpty() && context.buckets.none { it.deletedAtEpochMs == null } -> false
            else -> planningEngine.hasBucketChanges(bucketDrafts, context.toPlanningContext().buckets)
        }
        if (!requestIncludesBucketChanges && restorePendingUndoBeforeApplyingNewSave()) {
            context = buildUpdateContext(request)
            bucketDrafts = planningEngine.normalize(
                context = context.toPlanningContext(),
                request = request.toPlanningRequest()
            ).normalizedDrafts
        }
        return context to bucketDrafts
    }

    private suspend fun persistUpdatedSettingsAndUndo(
        request: UpdateBudgetSettingsRequest,
        budgetChanged: Boolean,
        paydayChanged: Boolean,
        bucketChanged: Boolean,
        leftoverReceiverBucketUuid: String?,
        context: UpdateBudgetSettingsContext,
        mutation: UpdateBudgetSettingsMutation
    ) {
        if (budgetChanged) {
            userSettingsStore.updatePortfolioMonthlyBudget(request.portfolioMonthlyBudgetCents)
        }
        if (paydayChanged) {
            userSettingsStore.updatePaydayDate(request.paydayDate)
        }
        planningRepository.persistPlanningSettings(
            leftoverReceiverBucketUuid = leftoverReceiverBucketUuid,
            selectedBucketUuid = mutation.finalSelectedBucketUuid
        )
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

    private fun hasNoSettingsChanges(
        budgetChanged: Boolean,
        paydayChanged: Boolean,
        bucketChanged: Boolean,
        leftoverReceiverChanged: Boolean
    ): Boolean {
        return !budgetChanged && !paydayChanged && !bucketChanged && !leftoverReceiverChanged
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
            pendingUndo.policiesToDeactivate.forEach { deactivateInsertedPolicy(it, settings, nowEpochMs) }
            pendingUndo.policiesToRestore.forEach { restorePolicy(it) }
            pendingUndo.adjustmentsToDeactivate.forEach { deactivateInsertedAdjustment(it, settings, nowEpochMs) }
            pendingUndo.adjustmentsToRestore.forEach { restoreAdjustment(it) }
            pendingUndo.bucketPoliciesToDeactivate.forEach {
                deactivateInsertedBucketPolicy(it, settings, nowEpochMs)
            }
            pendingUndo.bucketPoliciesToRestore.forEach { restoreBucketPolicy(it) }
            pendingUndo.bucketAdjustmentsToDeactivate.forEach {
                deactivateInsertedBucketAdjustment(it, settings, nowEpochMs)
            }
            pendingUndo.bucketAdjustmentsToRestore.forEach { restoreBucketAdjustment(it) }
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
            .map { it.policyToDomainModel() }
            .sortedBy { it.cycleStartDate }
        val currentPolicy = cycleScheduleResolver.resolvePolicyForDate(today, settings, policies)
        val currentPolicyRecord = policies.firstOrNull { policy ->
            policy.cycleStart() == currentPolicy.cycleStart &&
                policy.cycleEndExclusive() == currentPolicy.cycleEndExclusive
        }
        val currentAdjustments = budgetAdjustmentDao.getActiveForCycle(currentPolicy.cycleStart.toString())
            .map { it.adjustmentToDomainModel() }
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
                .map { it.bucketToDomainModel() },
            bucketPolicies = bucketAllocationPolicyDao.getAllForSnapshot()
                .map { it.bucketPolicyToDomainModel() },
            bucketAdjustments = bucketAllocationAdjustmentDao.getAllForSnapshot()
                .map { it.bucketAdjustmentToDomainModel() }
        )
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

    private suspend fun deactivateInsertedPolicy(
        policy: BudgetPolicy,
        settings: UserSettings,
        nowEpochMs: Long
    ) {
        val entity = budgetPolicyDao.findByPolicyUuid(policy.policyUuid) ?: return
        if (entity.deletedAtEpochMs != null) return
        val tombstoneEpochMs = maxOf(nowEpochMs, entity.updatedAtEpochMs + 1L)
        budgetPolicyDao.update(
            entity.copy(
                deletedAtEpochMs = tombstoneEpochMs,
                updatedAtEpochMs = tombstoneEpochMs,
                lastModifiedByInstallId = settings.installDeviceId,
                modClock = hybridLogicalClockService.next(
                    previousClock = entity.modClock,
                    nowEpochMs = tombstoneEpochMs,
                    installId = settings.installDeviceId
                )
            )
        )
    }

    private suspend fun restorePolicy(policy: BudgetPolicy) {
        val entity = budgetPolicyDao.findByPolicyUuid(policy.policyUuid)
        if (entity == null) {
            budgetPolicyDao.insert(policy.toEntity())
        } else {
            budgetPolicyDao.update(policy.toEntity(id = entity.id))
        }
    }

    private suspend fun deactivateInsertedAdjustment(
        adjustment: BudgetAdjustment,
        settings: UserSettings,
        nowEpochMs: Long
    ) {
        val entity = budgetAdjustmentDao.findByAdjustmentUuid(adjustment.adjustmentUuid) ?: return
        if (entity.deletedAtEpochMs != null) return
        val tombstoneEpochMs = maxOf(nowEpochMs, entity.updatedAtEpochMs + 1L)
        budgetAdjustmentDao.update(
            entity.copy(
                deletedAtEpochMs = tombstoneEpochMs,
                updatedAtEpochMs = tombstoneEpochMs,
                lastModifiedByInstallId = settings.installDeviceId,
                modClock = hybridLogicalClockService.next(
                    previousClock = entity.modClock,
                    nowEpochMs = tombstoneEpochMs,
                    installId = settings.installDeviceId
                )
            )
        )
    }

    private suspend fun restoreAdjustment(adjustment: BudgetAdjustment) {
        val entity = budgetAdjustmentDao.findByAdjustmentUuid(adjustment.adjustmentUuid)
        if (entity == null) {
            budgetAdjustmentDao.insert(adjustment.toEntity())
        } else {
            budgetAdjustmentDao.update(adjustment.toEntity(id = entity.id))
        }
    }

    private suspend fun deactivateInsertedBucket(
        bucket: BudgetBucket,
        settings: UserSettings,
        nowEpochMs: Long
    ) {
        val entity = budgetBucketDao.findByBucketUuid(bucket.bucketUuid) ?: return
        if (entity.deletedAtEpochMs != null) return
        val tombstoneEpochMs = maxOf(nowEpochMs, entity.updatedAtEpochMs + 1L)
        budgetBucketDao.update(
            entity.copy(
                deletedAtEpochMs = tombstoneEpochMs,
                updatedAtEpochMs = tombstoneEpochMs,
                lastModifiedByInstallId = settings.installDeviceId,
                modClock = hybridLogicalClockService.next(
                    previousClock = entity.modClock,
                    nowEpochMs = tombstoneEpochMs,
                    installId = settings.installDeviceId
                )
            )
        )
    }

    private suspend fun restoreBucket(bucket: BudgetBucket) {
        val entity = budgetBucketDao.findByBucketUuid(bucket.bucketUuid)
        if (entity == null) {
            budgetBucketDao.insert(bucket.toEntity())
        } else {
            budgetBucketDao.update(bucket.toEntity(id = entity.id))
        }
    }

    private suspend fun deactivateInsertedBucketPolicy(
        policy: BucketAllocationPolicy,
        settings: UserSettings,
        nowEpochMs: Long
    ) {
        val entity = bucketAllocationPolicyDao.findByAllocationUuid(policy.allocationUuid) ?: return
        if (entity.deletedAtEpochMs != null) return
        val tombstoneEpochMs = maxOf(nowEpochMs, entity.updatedAtEpochMs + 1L)
        bucketAllocationPolicyDao.update(
            entity.copy(
                deletedAtEpochMs = tombstoneEpochMs,
                updatedAtEpochMs = tombstoneEpochMs,
                lastModifiedByInstallId = settings.installDeviceId,
                modClock = hybridLogicalClockService.next(
                    previousClock = entity.modClock,
                    nowEpochMs = tombstoneEpochMs,
                    installId = settings.installDeviceId
                )
            )
        )
    }

    private suspend fun restoreBucketPolicy(policy: BucketAllocationPolicy) {
        val entity = bucketAllocationPolicyDao.findByAllocationUuid(policy.allocationUuid)
        if (entity == null) {
            bucketAllocationPolicyDao.insert(policy.toEntity())
        } else {
            bucketAllocationPolicyDao.update(policy.toEntity(id = entity.id))
        }
    }

    private suspend fun deactivateInsertedBucketAdjustment(
        adjustment: BucketAllocationAdjustment,
        settings: UserSettings,
        nowEpochMs: Long
    ) {
        val entity = bucketAllocationAdjustmentDao.findByAdjustmentUuid(adjustment.adjustmentUuid) ?: return
        if (entity.deletedAtEpochMs != null) return
        val tombstoneEpochMs = maxOf(nowEpochMs, entity.updatedAtEpochMs + 1L)
        bucketAllocationAdjustmentDao.update(
            entity.copy(
                deletedAtEpochMs = tombstoneEpochMs,
                updatedAtEpochMs = tombstoneEpochMs,
                lastModifiedByInstallId = settings.installDeviceId,
                modClock = hybridLogicalClockService.next(
                    previousClock = entity.modClock,
                    nowEpochMs = tombstoneEpochMs,
                    installId = settings.installDeviceId
                )
            )
        )
    }

    private suspend fun restoreBucketAdjustment(adjustment: BucketAllocationAdjustment) {
        val entity = bucketAllocationAdjustmentDao.findByAdjustmentUuid(adjustment.adjustmentUuid)
        if (entity == null) {
            bucketAllocationAdjustmentDao.insert(adjustment.toEntity())
        } else {
            bucketAllocationAdjustmentDao.update(adjustment.toEntity(id = entity.id))
        }
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

    private fun UpdateBudgetSettingsContext.toPlanningContext(): PlanningContext {
        return PlanningContext(
            config = PlanningConfig(
                portfolioMonthlyBudgetCents = settings.resolvedPortfolioMonthlyBudgetCents,
                leftoverReceiverBucketUuid = settings.leftoverReceiverBucketUuid
            ),
            buckets = buckets.map { it.toPlanningBucket() },
            selectedBucketUuid = settings.selectedBucketUuid
        )
    }

    private fun UpdateBudgetSettingsContext.toMutationContext(): BudgetSettingsPlanningMutationContext {
        return BudgetSettingsPlanningMutationContext(
            settings = settings,
            today = today,
            currentCycleStart = currentPolicy.cycleStart,
            currentCycleEndExclusive = currentPolicy.cycleEndExclusive,
            buckets = buckets,
            bucketPolicies = bucketPolicies,
            bucketAdjustments = bucketAdjustments
        )
    }

    private fun UpdateBudgetSettingsRequest.toPlanningRequest(
        bucketDrafts: List<BucketDraft> = buckets
    ): SavePlanningRequest {
        return SavePlanningRequest(
            portfolioMonthlyBudgetCents = portfolioMonthlyBudgetCents,
            leftoverReceiverBucketUuid = leftoverReceiverBucketUuid,
            buckets = bucketDrafts
        )
    }

}
