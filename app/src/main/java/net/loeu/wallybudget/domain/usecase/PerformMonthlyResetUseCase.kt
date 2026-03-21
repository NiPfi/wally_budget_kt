package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.dao.BudgetAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.dao.MonthlyHistoryDao
import net.loeu.wallybudget.data.local.db.TransactionRunner
import net.loeu.wallybudget.data.local.entity.toDomainModel as adjustmentToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel as policyToDomainModel
import net.loeu.wallybudget.data.local.entity.toEntity as budgetPolicyToEntity
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BudgetAdjustmentResolver
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import net.loeu.wallybudget.domain.usecase.internal.CycleRange
import net.loeu.wallybudget.domain.usecase.internal.archiveCycleIfNeeded
import net.loeu.wallybudget.domain.usecase.internal.lastResetDateOrNull
import net.loeu.wallybudget.domain.usecase.internal.newBudgetPolicy
import net.loeu.wallybudget.domain.usecase.internal.pendingCycleRangeOrNull
import net.loeu.wallybudget.domain.usecase.internal.toStartOfDayMillis
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Suppress("LongMethod")
class PerformMonthlyResetUseCase(
    private val transactionRunner: TransactionRunner,
    private val expenseDao: ExpenseDao,
    private val budgetPolicyDao: BudgetPolicyDao,
    private val budgetAdjustmentDao: BudgetAdjustmentDao,
    private val monthlyHistoryDao: MonthlyHistoryDao,
    private val userSettingsStore: UserSettingsStore,
    private val budgetCalculationService: BudgetCalculationService,
    private val cycleScheduleResolver: CycleScheduleResolver,
    private val budgetAdjustmentResolver: BudgetAdjustmentResolver,
    private val rebuildBucketMonthlyHistoryUseCase: RebuildBucketMonthlyHistoryUseCase,
    private val hybridLogicalClockService: HybridLogicalClockService
) {
    suspend operator fun invoke(settings: UserSettings, now: LocalDate) {
        val ensuredSettings = userSettingsStore.ensureIdentity()
        val lastResetDate = settings.lastResetDateOrNull() ?: return
        val policies = budgetPolicyDao.getAllForSnapshot()
            .filter { it.deletedAtEpochMs == null }
            .map { it.policyToDomainModel() }
        val currentPolicy = cycleScheduleResolver.resolvePolicyForDate(now, settings, policies)
        val currentCycleStart = currentPolicy.cycleStart
        val existingPending = settings.pendingCycleRangeOrNull()
        val recoveryPendingCycle = if (existingPending == null) {
            recoverMissingPendingCycle(settings, currentPolicy, policies)
        } else {
            null
        }

        if (!currentCycleStart.isAfter(lastResetDate)) {
            recoveryPendingCycle?.let { pendingCycle ->
                userSettingsStore.setPendingCycle(
                    cycleStartDate = pendingCycle.start,
                    cycleEndDateExclusive = pendingCycle.endExclusive,
                    detectedAtTimestamp = Instant.now().toEpochMilli()
                )
            }
            return
        }

        var clearPending = false
        var nextPendingCycle: CycleRange? = null

        transactionRunner.inTransaction {
            if (existingPending != null && existingPending.endExclusive.isBefore(currentCycleStart)) {
                val pendingPolicy = cycleScheduleResolver.policyForCycleStart(
                    cycleStart = existingPending.start,
                    settings = settings,
                    policies = policies
                )
                archiveCycleIfNeeded(
                    expenseDao = expenseDao,
                    budgetPolicyDao = budgetPolicyDao,
                    monthlyHistoryDao = monthlyHistoryDao,
                    budgetCalculationService = budgetCalculationService,
                    budgetAdjustmentResolver = budgetAdjustmentResolver,
                    cyclePolicy = pendingPolicy,
                    adjustments = budgetAdjustmentDao.getActiveForCycle(existingPending.start.toString())
                        .map { it.adjustmentToDomainModel() },
                    settings = settings,
                    cycleStart = existingPending.start,
                    cycleEnd = existingPending.endExclusive
                )
                clearPending = true
            }

            val endedCycles = cycleScheduleResolver.completedCyclesBetween(
                fromStartInclusive = lastResetDate,
                untilStartExclusive = currentCycleStart,
                settings = settings,
                policies = policies
            )

            if (endedCycles.isEmpty()) {
                return@inTransaction
            }

            endedCycles.dropLast(1).forEach { cycle ->
                archiveCycleIfNeeded(
                    expenseDao = expenseDao,
                    budgetPolicyDao = budgetPolicyDao,
                    monthlyHistoryDao = monthlyHistoryDao,
                    budgetCalculationService = budgetCalculationService,
                    budgetAdjustmentResolver = budgetAdjustmentResolver,
                    cyclePolicy = cycle,
                    adjustments = budgetAdjustmentDao.getActiveForCycle(cycle.cycleStart.toString())
                        .map { it.adjustmentToDomainModel() },
                    settings = settings,
                    cycleStart = cycle.cycleStart,
                    cycleEnd = cycle.cycleEndExclusive
                )
            }
            nextPendingCycle = endedCycles.last().let { CycleRange(it.cycleStart, it.cycleEndExclusive) }
            ensurePolicyForCycle(
                settings = ensuredSettings,
                cycleStart = currentCycleStart,
                cyclePolicy = currentPolicy
            )
        }

        if (clearPending) userSettingsStore.clearPendingCycle()
        (nextPendingCycle ?: recoveryPendingCycle)?.let { storePendingCycle(it) }
        val updatedLastResetTimestamp = currentCycleStart.toStartOfDayMillis()
        userSettingsStore.updateLastResetTimestamp(updatedLastResetTimestamp)
        rebuildBucketMonthlyHistoryUseCase(
            settings = settings.copy(lastResetTimestamp = updatedLastResetTimestamp),
            replaceExisting = true
        )
    }

    private suspend fun ensurePolicyForCycle(
        settings: UserSettings,
        cycleStart: LocalDate,
        cyclePolicy: net.loeu.wallybudget.domain.service.ResolvedCyclePolicy
    ) {
        if (budgetPolicyDao.findActivePolicyForCycle(cycleStart.toString()) != null) return

        val nowEpochMs = cycleStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        budgetPolicyDao.insert(
            newBudgetPolicy(
                cycleStart = cycleStart,
                cycleEndExclusive = cyclePolicy.cycleEndExclusive,
                budgetAmountCents = cyclePolicy.budgetAmountCents,
                paydayDayOfMonth = cyclePolicy.paydayDayOfMonth,
                installId = settings.installDeviceId,
                nowEpochMs = nowEpochMs,
                hybridLogicalClockService = hybridLogicalClockService
            ).budgetPolicyToEntity()
        )
    }

    private suspend fun storePendingCycle(cycleRange: CycleRange) {
        userSettingsStore.setPendingCycle(
            cycleStartDate = cycleRange.start,
            cycleEndDateExclusive = cycleRange.endExclusive,
            detectedAtTimestamp = Instant.now().toEpochMilli()
        )
    }

    private suspend fun recoverMissingPendingCycle(
        settings: UserSettings,
        currentPolicy: net.loeu.wallybudget.domain.service.ResolvedCyclePolicy,
        policies: List<net.loeu.wallybudget.domain.model.BudgetPolicy>
    ): CycleRange? {
        val previousCycleStart = cycleScheduleResolver.findPreviousCycleStart(
            date = currentPolicy.cycleStart,
            settings = settings,
            policies = policies
        )
        val archivedPreviousCycle = monthlyHistoryDao.findByCycleStart(previousCycleStart.toString())

        val previousCycleExpenseCount = expenseDao.countInRange(
            previousCycleStart.toString(),
            currentPolicy.cycleStart.toString()
        )

        return if (
            previousCycleStart.isBefore(currentPolicy.cycleStart) &&
            archivedPreviousCycle == null &&
            previousCycleExpenseCount > 0
        ) {
            CycleRange(
                start = previousCycleStart,
                endExclusive = currentPolicy.cycleStart
            )
        } else {
            null
        }
    }
}
