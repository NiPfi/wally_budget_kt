@file:Suppress("LongMethod", "ReturnCount", "TooManyFunctions")

package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.first
import net.loeu.wallybudget.data.local.dao.BudgetAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationPolicyDao
import net.loeu.wallybudget.data.local.db.TransactionRunner
import net.loeu.wallybudget.data.local.entity.toDomainModel
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.data.time.CurrentDateProvider
import net.loeu.wallybudget.domain.model.BudgetAdjustment
import net.loeu.wallybudget.domain.model.BudgetPolicy
import net.loeu.wallybudget.domain.model.BucketAllocationAdjustment
import net.loeu.wallybudget.domain.model.BucketAllocationPolicy
import net.loeu.wallybudget.domain.model.PendingPaydayUndo
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import net.loeu.wallybudget.domain.service.ImmediatePaydayChangePlan
import net.loeu.wallybudget.domain.service.ResolvedCyclePolicy
import net.loeu.wallybudget.domain.usecase.internal.deactivateInsertedAdjustment
import net.loeu.wallybudget.domain.usecase.internal.deactivateInsertedBucketAdjustment
import net.loeu.wallybudget.domain.usecase.internal.deactivateInsertedBucketPolicy
import net.loeu.wallybudget.domain.usecase.internal.deactivateInsertedPolicy
import net.loeu.wallybudget.domain.usecase.internal.newBudgetAdjustment
import net.loeu.wallybudget.domain.usecase.internal.newBucketAllocationAdjustment
import net.loeu.wallybudget.domain.usecase.internal.newBucketAllocationPolicy
import net.loeu.wallybudget.domain.usecase.internal.newBudgetPolicy
import net.loeu.wallybudget.domain.usecase.internal.restoreAdjustment
import net.loeu.wallybudget.domain.usecase.internal.restoreBucketAdjustment
import net.loeu.wallybudget.domain.usecase.internal.restoreBucketPolicy
import net.loeu.wallybudget.domain.usecase.internal.restorePolicy
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

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
    val bucketPolicies: List<BucketAllocationPolicy>,
    val bucketAdjustments: List<BucketAllocationAdjustment>,
    val futurePolicies: List<BudgetPolicy>,
    val paydayPlan: ImmediatePaydayChangePlan
)

private data class UpdatePaydayMutation(
    val insertedPolicies: List<BudgetPolicy>,
    val activeAdjustments: List<BudgetAdjustment>,
    val insertedBucketPolicies: List<BucketAllocationPolicy>,
    val insertedBucketAdjustments: List<BucketAllocationAdjustment>
)

class UpdatePaydayUseCase(
    private val transactionRunner: TransactionRunner,
    private val userSettingsStore: UserSettingsStore,
    private val budgetPolicyDao: BudgetPolicyDao,
    private val budgetAdjustmentDao: BudgetAdjustmentDao,
    private val bucketAllocationPolicyDao: BucketAllocationPolicyDao,
    private val bucketAllocationAdjustmentDao: BucketAllocationAdjustmentDao,
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
            val insertedPolicies = regeneratePolicies(context)
            val rewrittenAdjustments = rewriteBudgetAdjustments(
                context = context,
                targetPaydayDate = request.paydayDate,
                insertedPolicies = insertedPolicies
            )
            val rewrittenBucketSchedules = rewriteBucketSchedules(
                context = context,
                targetPaydayDate = request.paydayDate,
                insertedPolicies = insertedPolicies
            )
            UpdatePaydayMutation(
                insertedPolicies = insertedPolicies,
                activeAdjustments = rewrittenAdjustments,
                insertedBucketPolicies = rewrittenBucketSchedules.insertedPolicies,
                insertedBucketAdjustments = rewrittenBucketSchedules.insertedAdjustments
            )
        }

        userSettingsStore.updatePaydayDate(request.paydayDate)
        userSettingsStore.savePendingPaydayUndo(buildPendingPaydayUndo(context, mutation))

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
            .map { it.toDomainModel() }
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
            currentAdjustments = budgetAdjustmentDao.getAllForSnapshot()
                .map { it.toDomainModel() }
                .filter { it.deletedAtEpochMs == null && !it.cycleStart().isBefore(currentPolicy.cycleStart) },
            bucketPolicies = bucketAllocationPolicyDao.getAllForSnapshot()
                .map { it.toDomainModel() }
                .filter { it.deletedAtEpochMs == null && !it.cycleStart().isBefore(currentPolicy.cycleStart) },
            bucketAdjustments = bucketAllocationAdjustmentDao.getAllForSnapshot()
                .map { it.toDomainModel() }
                .filter { it.deletedAtEpochMs == null && !it.cycleStart().isBefore(currentPolicy.cycleStart) },
            futurePolicies = futurePolicies,
            paydayPlan = paydayPlan
        )
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

    private suspend fun softDeleteAdjustment(
        adjustment: BudgetAdjustment,
        settings: UserSettings,
        nowEpochMs: Long
    ) {
        val entity = budgetAdjustmentDao.findByAdjustmentUuid(adjustment.adjustmentUuid) ?: return
        budgetAdjustmentDao.update(
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

    private data class RewrittenBucketSchedules(
        val insertedPolicies: List<BucketAllocationPolicy>,
        val insertedAdjustments: List<BucketAllocationAdjustment>
    )

    private suspend fun rewriteBudgetAdjustments(
        context: UpdatePaydayContext,
        targetPaydayDate: Int,
        insertedPolicies: List<BudgetPolicy>
    ): List<BudgetAdjustment> {
        if (context.currentAdjustments.isEmpty()) return emptyList()

        val nowEpochMs = context.today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val targetSettings = context.settings.copy(paydayDate = targetPaydayDate)
        val rewrittenAdjustments = mutableListOf<BudgetAdjustment>()

        context.currentAdjustments.forEach { adjustment ->
            val targetCycle = cycleScheduleResolver.resolvePolicyForDate(
                date = adjustment.effectiveDate(),
                settings = targetSettings,
                policies = insertedPolicies
            )
            softDeleteAdjustment(adjustment, context.settings, nowEpochMs)
            val rewrittenAdjustment = newBudgetAdjustment(
                cycleStart = targetCycle.cycleStart,
                effectiveDate = adjustment.effectiveDate(),
                previousMonthlyBudgetCents = adjustment.previousMonthlyBudgetCents,
                newMonthlyBudgetCents = adjustment.newMonthlyBudgetCents,
                installId = context.settings.installDeviceId,
                nowEpochMs = nowEpochMs,
                hybridLogicalClockService = hybridLogicalClockService
            )
            budgetAdjustmentDao.insert(rewrittenAdjustment.toEntity())
            rewrittenAdjustments += rewrittenAdjustment
        }

        return rewrittenAdjustments
    }

    private suspend fun rewriteBucketSchedules(
        context: UpdatePaydayContext,
        targetPaydayDate: Int,
        insertedPolicies: List<BudgetPolicy>
    ): RewrittenBucketSchedules {
        if (context.bucketPolicies.isEmpty() && context.bucketAdjustments.isEmpty()) {
            return RewrittenBucketSchedules(emptyList(), emptyList())
        }

        val nowEpochMs = context.today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val targetSettings = context.settings.copy(paydayDate = targetPaydayDate)

        val insertedBucketPolicies = mutableListOf<BucketAllocationPolicy>()
        context.bucketPolicies.forEach { policy ->
            val targetCycle = cycleScheduleResolver.resolvePolicyForDate(
                date = anchorDateForPolicy(policy),
                settings = targetSettings,
                policies = insertedPolicies
            )
            softDeleteBucketPolicy(policy, context.settings, nowEpochMs)
            val rewrittenPolicy = newBucketAllocationPolicy(
                bucketUuid = policy.bucketUuid,
                cycleStart = targetCycle.cycleStart,
                cycleEndExclusive = targetCycle.cycleEndExclusive,
                allocatedAmountCents = policy.allocatedAmountCents,
                installId = context.settings.installDeviceId,
                nowEpochMs = nowEpochMs,
                hybridLogicalClockService = hybridLogicalClockService
            )
            bucketAllocationPolicyDao.insert(rewrittenPolicy.toEntity())
            insertedBucketPolicies += rewrittenPolicy
        }

        val insertedBucketAdjustments = mutableListOf<BucketAllocationAdjustment>()
        context.bucketAdjustments.forEach { adjustment ->
            val targetCycle = cycleScheduleResolver.resolvePolicyForDate(
                date = adjustment.effectiveDate(),
                settings = targetSettings,
                policies = insertedPolicies
            )
            softDeleteBucketAdjustment(adjustment, context.settings, nowEpochMs)
            val rewrittenAdjustment = newBucketAllocationAdjustment(
                bucketUuid = adjustment.bucketUuid,
                cycleStart = targetCycle.cycleStart,
                effectiveDate = adjustment.effectiveDate(),
                previousAllocatedAmountCents = adjustment.previousAllocatedAmountCents,
                newAllocatedAmountCents = adjustment.newAllocatedAmountCents,
                installId = context.settings.installDeviceId,
                nowEpochMs = nowEpochMs,
                hybridLogicalClockService = hybridLogicalClockService
            )
            bucketAllocationAdjustmentDao.insert(rewrittenAdjustment.toEntity())
            insertedBucketAdjustments += rewrittenAdjustment
        }

        return RewrittenBucketSchedules(insertedBucketPolicies, insertedBucketAdjustments)
    }

    private fun anchorDateForPolicy(policy: BucketAllocationPolicy): LocalDate {
        val cycleLengthDays = ChronoUnit.DAYS.between(policy.cycleStart(), policy.cycleEndExclusive())
            .coerceAtLeast(1L)
        return policy.cycleStart().plusDays(cycleLengthDays / 2L)
    }

    private fun buildPendingPaydayUndo(
        context: UpdatePaydayContext,
        mutation: UpdatePaydayMutation
    ): PendingPaydayUndo {
        return PendingPaydayUndo(
            previousSettings = context.settings,
            policiesToRestore = context.policies.filter { it.deletedAtEpochMs == null },
            policiesToDeactivate = mutation.insertedPolicies,
            adjustmentsToRestore = context.currentAdjustments,
            adjustmentsToDeactivate = mutation.activeAdjustments,
            bucketPoliciesToRestore = context.bucketPolicies,
            bucketPoliciesToDeactivate = mutation.insertedBucketPolicies,
            bucketAdjustmentsToRestore = context.bucketAdjustments,
            bucketAdjustmentsToDeactivate = mutation.insertedBucketAdjustments,
            expiresAtExclusive = minOf(
                context.paydayPlan.rewrittenCurrentCycle.cycleEndExclusive,
                context.currentPolicy.cycleEndExclusive
            ).toString()
        )
    }

    private suspend fun softDeleteBucketPolicy(
        policy: BucketAllocationPolicy,
        settings: UserSettings,
        nowEpochMs: Long
    ) {
        val entity = bucketAllocationPolicyDao.findByAllocationUuid(policy.allocationUuid) ?: return
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

    private suspend fun softDeleteBucketAdjustment(
        adjustment: BucketAllocationAdjustment,
        settings: UserSettings,
        nowEpochMs: Long
    ) {
        val entity = bucketAllocationAdjustmentDao.findByAdjustmentUuid(adjustment.adjustmentUuid) ?: return
        bucketAllocationAdjustmentDao.update(
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

}
