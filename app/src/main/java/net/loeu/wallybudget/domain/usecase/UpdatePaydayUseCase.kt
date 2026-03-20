@file:Suppress("LongMethod", "ReturnCount", "TooManyFunctions")

package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.first
import net.loeu.wallybudget.data.local.dao.BudgetAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.db.TransactionRunner
import net.loeu.wallybudget.data.local.entity.toDomainModel as adjustmentToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel as policyToDomainModel
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.data.time.CurrentDateProvider
import net.loeu.wallybudget.domain.model.BudgetAdjustment
import net.loeu.wallybudget.domain.model.BudgetPolicy
import net.loeu.wallybudget.domain.model.PendingSettingsUndo
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import net.loeu.wallybudget.domain.service.ImmediatePaydayChangePlan
import net.loeu.wallybudget.domain.service.ResolvedCyclePolicy
import net.loeu.wallybudget.domain.usecase.internal.newBudgetPolicy
import java.time.LocalDate
import java.time.ZoneId

data class UpdatePaydayRequest(
    val paydayDate: Int
)

data class UpdatePaydayResult(
    val summaryMessage: String
)

private data class UpdatePaydayContext(
    val settings: UserSettings,
    val today: LocalDate,
    val policies: List<BudgetPolicy>,
    val currentPolicy: ResolvedCyclePolicy,
    val currentPolicyRecord: BudgetPolicy?,
    val currentAdjustments: List<BudgetAdjustment>,
    val futurePolicies: List<BudgetPolicy>,
    val paydayPlan: ImmediatePaydayChangePlan
)

private data class UpdatePaydayMutation(
    val insertedPolicies: List<BudgetPolicy>,
    val activeAdjustments: List<BudgetAdjustment>
)

class UpdatePaydayUseCase(
    private val transactionRunner: TransactionRunner,
    private val userSettingsStore: UserSettingsStore,
    private val budgetPolicyDao: BudgetPolicyDao,
    private val budgetAdjustmentDao: BudgetAdjustmentDao,
    private val currentDateProvider: CurrentDateProvider,
    private val cycleScheduleResolver: CycleScheduleResolver,
    private val hybridLogicalClockService: HybridLogicalClockService
) {
    private val syncObservedDateUseCase = SyncObservedDateUseCase(userSettingsStore)

    suspend operator fun invoke(request: UpdatePaydayRequest): UpdatePaydayResult {
        require(request.paydayDate in 1..31) { "Payday must be between 1 and 31." }

        var context = buildContext(request)
        if (context == null) {
            return UpdatePaydayResult("No payday changes.")
        }
        if (restorePendingUndoBeforeApplyingNewSave()) {
            context = buildContext(request)
                ?: return UpdatePaydayResult("No payday changes.")
        }

        val mutation = transactionRunner.inTransaction {
            UpdatePaydayMutation(
                insertedPolicies = regeneratePolicies(context),
                activeAdjustments = budgetAdjustmentDao.getActiveForCycle(context.currentPolicy.cycleStart.toString())
                    .map { it.adjustmentToDomainModel() }
            )
        }

        userSettingsStore.updatePaydayDate(request.paydayDate)
        userSettingsStore.savePendingSettingsUndo(buildPendingSettingsUndo(context, mutation))

        val rewrittenCurrentCycleEnd = context.paydayPlan.rewrittenCurrentCycle.cycleEndExclusive
        val originalCurrentCycleEnd = context.currentPolicy.cycleEndExclusive
        val summary = buildString {
            append("Payday switches from ${context.settings.paydayDate} to ${request.paydayDate} now.")
            append(' ')
            append(
                when {
                    rewrittenCurrentCycleEnd.isBefore(originalCurrentCycleEnd) ->
                        "This cycle now ends on $rewrittenCurrentCycleEnd."
                    rewrittenCurrentCycleEnd.isAfter(originalCurrentCycleEnd) ->
                        "This cycle extends to $rewrittenCurrentCycleEnd."
                    else ->
                        "This cycle still ends on $rewrittenCurrentCycleEnd."
                }
            )
        }
        return UpdatePaydayResult(summary)
    }

    private suspend fun buildContext(request: UpdatePaydayRequest): UpdatePaydayContext? {
        val settings = userSettingsStore.ensureIdentity()
        val today = syncObservedDateUseCase(settings, currentDateProvider.currentDate())
        if (request.paydayDate == settings.paydayDate) {
            return null
        }
        val policies = budgetPolicyDao.getAllForSnapshot()
            .filter { it.deletedAtEpochMs == null }
            .map { it.policyToDomainModel() }
            .sortedBy { it.cycleStartDate }
        val currentPolicy = cycleScheduleResolver.resolvePolicyForDate(today, settings, policies)
        val currentPolicyRecord = policies.firstOrNull { policy ->
            policy.cycleStart() == currentPolicy.cycleStart &&
                policy.cycleEndExclusive() == currentPolicy.cycleEndExclusive
        }
        val futurePolicies = policies
            .filter { !it.cycleStart().isBefore(currentPolicy.cycleEndExclusive) }
            .sortedBy { it.cycleStartDate }
        val paydayPlan = cycleScheduleResolver.planImmediatePaydayChange(
            currentCycle = currentPolicy,
            today = today,
            targetMonthlyBudgetCents = settings.resolvedPortfolioMonthlyBudgetCents,
            newPaydayDayOfMonth = request.paydayDate
        )
        return UpdatePaydayContext(
            settings = settings,
            today = today,
            policies = policies,
            currentPolicy = currentPolicy,
            currentPolicyRecord = currentPolicyRecord,
            currentAdjustments = budgetAdjustmentDao.getActiveForCycle(currentPolicy.cycleStart.toString())
                .map { it.adjustmentToDomainModel() },
            futurePolicies = futurePolicies,
            paydayPlan = paydayPlan
        )
    }

    private suspend fun restorePendingUndoBeforeApplyingNewSave(): Boolean {
        val settings = userSettingsStore.ensureIdentity()
        val today = syncObservedDateUseCase(settings, currentDateProvider.currentDate())
        val pendingUndo = userSettingsStore.pendingSettingsUndo.first() ?: return false
        if (!today.isBefore(pendingUndo.expiresAtExclusiveDate())) {
            userSettingsStore.clearPendingSettingsUndo()
            return false
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
        return true
    }

    private suspend fun regeneratePolicies(context: UpdatePaydayContext): List<BudgetPolicy> {
        val nowEpochMs = context.today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        softDeletePolicies(context.futurePolicies, context.settings, nowEpochMs)
        context.currentPolicyRecord?.let { softDeletePolicy(it, context.settings, nowEpochMs) }
        return listOf(
            insertBudgetPolicy(
                settings = context.settings,
                cycleStart = context.paydayPlan.rewrittenCurrentCycle.cycleStart,
                cycleEndExclusive = context.paydayPlan.rewrittenCurrentCycle.cycleEndExclusive,
                budgetAmountCents = context.paydayPlan.rewrittenCurrentCycle.budgetAmountCents,
                paydayDayOfMonth = context.paydayPlan.rewrittenCurrentCycle.paydayDayOfMonth,
                nowEpochMs = nowEpochMs
            ),
            insertBudgetPolicy(
                settings = context.settings,
                cycleStart = context.paydayPlan.firstRegularCycle.cycleStart,
                cycleEndExclusive = context.paydayPlan.firstRegularCycle.cycleEndExclusive,
                budgetAmountCents = context.paydayPlan.firstRegularCycle.budgetAmountCents,
                paydayDayOfMonth = context.paydayPlan.firstRegularCycle.paydayDayOfMonth,
                nowEpochMs = nowEpochMs
            )
        )
    }

    private suspend fun softDeletePolicies(
        policies: List<BudgetPolicy>,
        settings: UserSettings,
        nowEpochMs: Long
    ) {
        policies.forEach { softDeletePolicy(it, settings, nowEpochMs) }
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
        context: UpdatePaydayContext,
        mutation: UpdatePaydayMutation
    ): PendingSettingsUndo {
        return PendingSettingsUndo(
            previousSettings = context.settings,
            policiesToRestore = context.policies.filter { it.deletedAtEpochMs == null },
            policiesToDeactivate = mutation.insertedPolicies,
            adjustmentsToRestore = context.currentAdjustments,
            adjustmentsToDeactivate = mutation.activeAdjustments,
            expiresAtExclusive = minOf(
                context.paydayPlan.rewrittenCurrentCycle.cycleEndExclusive,
                context.currentPolicy.cycleEndExclusive
            ).toString()
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
}
