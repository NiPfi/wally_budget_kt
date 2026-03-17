package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.first
import net.loeu.wallybudget.data.local.dao.BudgetAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.db.TransactionRunner
import net.loeu.wallybudget.data.local.entity.BudgetAdjustmentEntity
import net.loeu.wallybudget.data.local.entity.toDomainModel as adjustmentToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel as policyToDomainModel
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.data.time.CurrentDateProvider
import net.loeu.wallybudget.domain.model.BudgetAdjustment
import net.loeu.wallybudget.domain.model.BudgetChangeMode
import net.loeu.wallybudget.domain.model.BudgetPolicy
import net.loeu.wallybudget.domain.model.PendingSettingsUndo
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BudgetAdjustmentResolver
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import net.loeu.wallybudget.domain.service.ImmediatePaydayChangePlan
import net.loeu.wallybudget.domain.service.ResolvedCyclePolicy
import net.loeu.wallybudget.domain.usecase.internal.newBudgetAdjustment
import net.loeu.wallybudget.domain.usecase.internal.newBudgetPolicy
import java.time.LocalDate
import java.time.ZoneId

data class UpdateBudgetSettingsRequest(
    val monthlyBudgetCents: Long,
    val paydayDate: Int,
    val budgetChangeMode: BudgetChangeMode
)

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
    val paydayPlan: ImmediatePaydayChangePlan?
)

private data class UpdateBudgetSettingsMutation(
    val insertedPolicies: List<BudgetPolicy>,
    val activeAdjustments: List<BudgetAdjustment>
)

@Suppress("TooManyFunctions")
class UpdateBudgetSettingsUseCase(
    private val transactionRunner: TransactionRunner,
    private val userSettingsStore: UserSettingsStore,
    private val budgetPolicyDao: BudgetPolicyDao,
    private val budgetAdjustmentDao: BudgetAdjustmentDao,
    private val currentDateProvider: CurrentDateProvider,
    private val cycleScheduleResolver: CycleScheduleResolver,
    private val budgetAdjustmentResolver: BudgetAdjustmentResolver,
    private val hybridLogicalClockService: HybridLogicalClockService
) {
    private val syncObservedDateUseCase = SyncObservedDateUseCase(userSettingsStore)

    suspend operator fun invoke(request: UpdateBudgetSettingsRequest): UpdateBudgetSettingsResult {
        restorePendingUndoBeforeApplyingNewSave()
        val context = buildUpdateContext(request)
        val settings = context.settings
        val budgetChanged = request.monthlyBudgetCents != settings.monthlyBudgetCents
        val paydayChanged = request.paydayDate != settings.paydayDate
        if (!budgetChanged && !paydayChanged) {
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

            UpdateBudgetSettingsMutation(
                insertedPolicies = regeneratePolicies(
                    settings = settings,
                    request = request,
                    today = context.today,
                    currentPolicy = context.currentPolicy,
                    currentPolicyRecord = context.currentPolicyRecord,
                    futurePolicies = context.futurePolicies,
                    paydayPlan = context.paydayPlan
                ),
                activeAdjustments = budgetAdjustmentDao.getActiveForCycle(context.currentPolicy.cycleStart.toString())
                    .map { it.adjustmentToDomainModel() }
            )
        }

        persistUpdatedSettingsAndUndo(
            request = request,
            budgetChanged = budgetChanged,
            paydayChanged = paydayChanged,
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
                budgetChanged = budgetChanged
            )
        )
    }

    private suspend fun persistUpdatedSettingsAndUndo(
        request: UpdateBudgetSettingsRequest,
        budgetChanged: Boolean,
        paydayChanged: Boolean,
        context: UpdateBudgetSettingsContext,
        mutation: UpdateBudgetSettingsMutation
    ) {
        if (budgetChanged || paydayChanged) {
            userSettingsStore.updateCycleSettings(
                monthlyBudgetCents = request.monthlyBudgetCents,
                paydayDate = request.paydayDate
            )
        }
        userSettingsStore.savePendingSettingsUndo(
            buildPendingSettingsUndo(
                context = context,
                mutation = mutation
            )
        )
    }

    private suspend fun restorePendingUndoBeforeApplyingNewSave() {
        val settings = userSettingsStore.ensureIdentity()
        val today = syncObservedDateUseCase(settings, currentDateProvider.currentDate())
        val pendingUndo = userSettingsStore.pendingSettingsUndo.first() ?: return
        if (!today.isBefore(pendingUndo.expiresAtExclusiveDate())) {
            userSettingsStore.clearPendingSettingsUndo()
            return
        }

        val nowEpochMs = System.currentTimeMillis()
        transactionRunner.inTransaction {
            pendingUndo.policiesToDeactivate.forEach { deactivateInsertedPolicy(it, settings, nowEpochMs) }
            pendingUndo.policiesToRestore.forEach { restorePolicy(it) }
            pendingUndo.adjustmentsToDeactivate.forEach { deactivateInsertedAdjustment(it, settings, nowEpochMs) }
            pendingUndo.adjustmentsToRestore.forEach { restoreAdjustment(it) }
        }
        userSettingsStore.restoreFromSnapshot(
            settings = pendingUndo.previousSettings,
            onboardingCompleted = pendingUndo.previousSettings.isOnboardingCompleted
        )
        userSettingsStore.clearPendingSettingsUndo()
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
                targetMonthlyBudgetCents = request.monthlyBudgetCents,
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
            paydayPlan = paydayPlan
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
        if (currentMonthlyBudget == request.monthlyBudgetCents) {
            return null
        }
        val adjustment = newBudgetAdjustment(
            cycleStart = context.currentPolicy.cycleStart,
            effectiveDate = context.today,
            previousMonthlyBudgetCents = currentMonthlyBudget,
            newMonthlyBudgetCents = request.monthlyBudgetCents,
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
                targetBudget = request.monthlyBudgetCents,
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
                monthlyBudgetCents = targetBudget
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

    private fun buildPendingSettingsUndo(
        context: UpdateBudgetSettingsContext,
        mutation: UpdateBudgetSettingsMutation
    ): PendingSettingsUndo {
        val policiesToRestore = buildList {
            context.currentPolicyRecord?.let { policy ->
                if (context.paydayPlan != null) add(policy)
            }
            addAll(context.futurePolicies)
        }
        return PendingSettingsUndo(
            previousSettings = context.settings,
            policiesToRestore = policiesToRestore,
            policiesToDeactivate = mutation.insertedPolicies,
            adjustmentsToRestore = context.currentAdjustments,
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
        budgetChanged: Boolean
    ): String {
        val parts = mutableListOf<String>()
        if (budgetChanged) {
            parts += when (request.budgetChangeMode) {
                BudgetChangeMode.PRORATE_CURRENT_CYCLE -> "Budget prorated from $effectiveDate."
                BudgetChangeMode.APPLY_NEXT_CYCLE -> "Budget changes on $nextCycleStart."
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
        return parts.joinToString(" ").ifBlank { "No settings changed." }
    }
}
