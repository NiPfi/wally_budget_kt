package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.dao.BudgetAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.db.TransactionRunner
import net.loeu.wallybudget.data.local.entity.toDomainModel as adjustmentToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel as policyToDomainModel
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.data.time.CurrentDateProvider
import net.loeu.wallybudget.domain.model.BudgetAdjustment
import net.loeu.wallybudget.domain.model.BudgetChangeMode
import net.loeu.wallybudget.domain.model.BudgetPolicy
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BudgetAdjustmentResolver
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
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
        val settings = userSettingsStore.ensureIdentity()
        val today = syncObservedDateUseCase(settings, currentDateProvider.currentDate())
        val policies = budgetPolicyDao.getAllForSnapshot()
            .filter { it.deletedAtEpochMs == null }
            .map { it.policyToDomainModel() }
            .sortedBy { it.cycleStartDate }
        val currentPolicy = cycleScheduleResolver.resolvePolicyForDate(today, settings, policies)
        val currentAdjustments = budgetAdjustmentDao.getActiveForCycle(currentPolicy.cycleStart.toString())
            .map { it.adjustmentToDomainModel() }

        val budgetChanged = request.monthlyBudgetCents != settings.monthlyBudgetCents
        val paydayChanged = request.paydayDate != settings.paydayDate

        transactionRunner.inTransaction {
            if (budgetChanged && request.budgetChangeMode == BudgetChangeMode.PRORATE_CURRENT_CYCLE) {
                val currentMonthlyBudget = budgetAdjustmentResolver.currentMonthlyBudget(
                    cycleStart = currentPolicy.cycleStart,
                    cycleEndExclusive = currentPolicy.cycleEndExclusive,
                    baseMonthlyBudgetCents = currentPolicy.budgetAmountCents,
                    adjustments = currentAdjustments,
                    onDate = today
                )
                if (currentMonthlyBudget != request.monthlyBudgetCents) {
                    budgetAdjustmentDao.insert(
                        newBudgetAdjustment(
                            cycleStart = currentPolicy.cycleStart,
                            effectiveDate = today,
                            previousMonthlyBudgetCents = currentMonthlyBudget,
                            newMonthlyBudgetCents = request.monthlyBudgetCents,
                            installId = settings.installDeviceId,
                            nowEpochMs = System.currentTimeMillis(),
                            hybridLogicalClockService = hybridLogicalClockService
                        ).toEntity()
                    )
                }
            }

            regenerateFuturePolicies(
                settings = settings,
                request = request,
                today = today,
                currentPolicy = currentPolicy,
                existingPolicies = policies
            )
        }

        if (budgetChanged) {
            userSettingsStore.updateMonthlyBudget(request.monthlyBudgetCents)
        }
        if (paydayChanged) {
            userSettingsStore.updatePaydayDate(request.paydayDate)
        }

        return UpdateBudgetSettingsResult(
            summaryMessage = buildSummaryMessage(
                settings = settings,
                request = request,
                currentPolicyEnd = currentPolicy.cycleEndExclusive,
                effectiveDate = today,
                paydayChanged = paydayChanged,
                budgetChanged = budgetChanged
            )
        )
    }

    private suspend fun regenerateFuturePolicies(
        settings: UserSettings,
        request: UpdateBudgetSettingsRequest,
        today: LocalDate,
        currentPolicy: net.loeu.wallybudget.domain.service.ResolvedCyclePolicy,
        existingPolicies: List<BudgetPolicy>
    ) {
        val futurePolicies = existingPolicies
            .filter { !it.cycleStart().isBefore(currentPolicy.cycleEndExclusive) }
            .sortedBy { it.cycleStartDate }
        if (!needsFutureRewrite(futurePolicies, request, settings)) {
            return
        }

        val nowEpochMs = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        softDeleteFuturePolicies(futurePolicies, settings, nowEpochMs)

        val targetBudget = request.monthlyBudgetCents
        val targetPayday = request.paydayDate
        if (targetPayday != currentPolicy.paydayDayOfMonth) {
            insertTransitionPolicies(
                settings = settings,
                currentPolicy = currentPolicy,
                targetBudget = targetBudget,
                targetPayday = targetPayday,
                nowEpochMs = nowEpochMs
            )
        } else if (request.budgetChangeMode == BudgetChangeMode.APPLY_NEXT_CYCLE || futurePolicies.isNotEmpty()) {
            insertNextCyclePolicy(
                settings = settings,
                currentPolicy = currentPolicy,
                targetBudget = targetBudget,
                targetPayday = targetPayday,
                nowEpochMs = nowEpochMs
            )
        }
    }

    private fun needsFutureRewrite(
        futurePolicies: List<BudgetPolicy>,
        request: UpdateBudgetSettingsRequest,
        settings: UserSettings
    ): Boolean {
        return futurePolicies.isNotEmpty() || request.paydayDate != settings.paydayDate
    }

    private suspend fun softDeleteFuturePolicies(
        futurePolicies: List<BudgetPolicy>,
        settings: UserSettings,
        nowEpochMs: Long
    ) {
        futurePolicies.forEach { policy ->
            val entity = budgetPolicyDao.findByPolicyUuid(policy.policyUuid) ?: return@forEach
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
    }

    private suspend fun insertTransitionPolicies(
        settings: UserSettings,
        currentPolicy: net.loeu.wallybudget.domain.service.ResolvedCyclePolicy,
        targetBudget: Long,
        targetPayday: Int,
        nowEpochMs: Long
    ) {
        val transition = cycleScheduleResolver.planPaydayTransition(
            currentCycleEndExclusive = currentPolicy.cycleEndExclusive,
            targetMonthlyBudgetCents = targetBudget,
            newPaydayDayOfMonth = targetPayday
        )
        transition.bridgeCycle?.let { bridge ->
            insertBudgetPolicy(
                settings = settings,
                cycleStart = bridge.cycleStart,
                cycleEndExclusive = bridge.cycleEndExclusive,
                budgetAmountCents = bridge.budgetAmountCents,
                paydayDayOfMonth = bridge.paydayDayOfMonth,
                nowEpochMs = nowEpochMs
            )
        }
        insertBudgetPolicy(
            settings = settings,
            cycleStart = transition.firstRegularCycle.cycleStart,
            cycleEndExclusive = transition.firstRegularCycle.cycleEndExclusive,
            budgetAmountCents = transition.firstRegularCycle.budgetAmountCents,
            paydayDayOfMonth = transition.firstRegularCycle.paydayDayOfMonth,
            nowEpochMs = nowEpochMs
        )
    }

    private suspend fun insertNextCyclePolicy(
        settings: UserSettings,
        currentPolicy: net.loeu.wallybudget.domain.service.ResolvedCyclePolicy,
        targetBudget: Long,
        targetPayday: Int,
        nowEpochMs: Long
    ) {
        val nextCycleStart = currentPolicy.cycleEndExclusive
        val nextCycleEndExclusive = cycleScheduleResolver.policyForCycleStart(
            cycleStart = nextCycleStart,
            settings = settings.copy(
                paydayDate = targetPayday,
                monthlyBudgetCents = targetBudget
            ),
            policies = emptyList()
        ).cycleEndExclusive
        insertBudgetPolicy(
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
    ) {
        budgetPolicyDao.insert(
            newBudgetPolicy(
                cycleStart = cycleStart,
                cycleEndExclusive = cycleEndExclusive,
                budgetAmountCents = budgetAmountCents,
                paydayDayOfMonth = paydayDayOfMonth,
                installId = settings.installDeviceId,
                nowEpochMs = nowEpochMs,
                hybridLogicalClockService = hybridLogicalClockService
            ).toEntity()
        )
    }

    private fun buildSummaryMessage(
        settings: UserSettings,
        request: UpdateBudgetSettingsRequest,
        currentPolicyEnd: LocalDate,
        effectiveDate: LocalDate,
        paydayChanged: Boolean,
        budgetChanged: Boolean
    ): String {
        val parts = mutableListOf<String>()
        if (budgetChanged) {
            parts += when (request.budgetChangeMode) {
                BudgetChangeMode.PRORATE_CURRENT_CYCLE ->
                    "Budget prorated from $effectiveDate."
                BudgetChangeMode.APPLY_NEXT_CYCLE ->
                    "Budget changes on $currentPolicyEnd."
            }
        }
        if (paydayChanged) {
            parts += "Payday switches from ${settings.paydayDate} to ${request.paydayDate} after $currentPolicyEnd."
        }
        return parts.ifEmpty { listOf("No settings changed.") }.joinToString(" ")
    }
}
