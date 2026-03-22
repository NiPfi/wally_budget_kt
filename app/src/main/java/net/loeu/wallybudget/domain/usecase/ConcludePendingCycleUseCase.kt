package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.dao.BudgetAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationPolicyDao
import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.dao.FundDao
import net.loeu.wallybudget.data.local.dao.FundTransactionDao
import net.loeu.wallybudget.data.local.dao.MonthlyHistoryDao
import net.loeu.wallybudget.data.local.db.TransactionRunner
import net.loeu.wallybudget.data.local.entity.FundTransactionEntity
import net.loeu.wallybudget.data.local.entity.toDomainModel as adjustmentToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel as policyToDomainModel
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.domain.model.FundTransactionType
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BudgetAdjustmentResolver
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import net.loeu.wallybudget.domain.usecase.internal.archiveCycleIfNeeded
import net.loeu.wallybudget.domain.usecase.internal.emptyBucketAllocationPolicyDao
import net.loeu.wallybudget.domain.usecase.internal.emptyFundDao
import net.loeu.wallybudget.domain.usecase.internal.emptyFundTransactionDao
import net.loeu.wallybudget.domain.usecase.internal.pendingCycleRangeOrNull
import java.time.ZoneId
import java.util.UUID

class ConcludePendingCycleUseCase(
    private val transactionRunner: TransactionRunner,
    private val expenseDao: ExpenseDao,
    private val budgetPolicyDao: BudgetPolicyDao,
    private val budgetAdjustmentDao: BudgetAdjustmentDao,
    private val bucketAllocationPolicyDao: BucketAllocationPolicyDao = emptyBucketAllocationPolicyDao,
    private val monthlyHistoryDao: MonthlyHistoryDao,
    private val fundDao: FundDao = emptyFundDao,
    private val fundTransactionDao: FundTransactionDao = emptyFundTransactionDao,
    private val userSettingsStore: UserSettingsStore,
    private val budgetCalculationService: BudgetCalculationService,
    private val cycleScheduleResolver: CycleScheduleResolver,
    private val budgetAdjustmentResolver: BudgetAdjustmentResolver,
    private val rebuildBucketMonthlyHistoryUseCase: RebuildBucketMonthlyHistoryUseCase
) {
    suspend operator fun invoke(settings: UserSettings) {
        val pendingCycle = settings.pendingCycleRangeOrNull() ?: return
        transactionRunner.inTransaction {
            val policies = budgetPolicyDao.getAllForSnapshot()
                .filter { it.deletedAtEpochMs == null }
                .map { it.policyToDomainModel() }
            val cyclePolicy = cycleScheduleResolver.policyForCycleStart(
                cycleStart = pendingCycle.start,
                settings = settings,
                policies = policies
            )
            archiveCycleIfNeeded(
                expenseDao = expenseDao,
                budgetPolicyDao = budgetPolicyDao,
                monthlyHistoryDao = monthlyHistoryDao,
                budgetCalculationService = budgetCalculationService,
                budgetAdjustmentResolver = budgetAdjustmentResolver,
                cyclePolicy = cyclePolicy,
                adjustments = budgetAdjustmentDao.getActiveForCycle(pendingCycle.start.toString())
                    .map { it.adjustmentToDomainModel() },
                settings = settings,
                cycleStart = pendingCycle.start,
                cycleEnd = pendingCycle.endExclusive
            )
            distributeCycleCloseoutToFunds(
                pendingCycle = pendingCycle,
                settings = settings
            )
        }
        userSettingsStore.clearPendingCycle()
        rebuildBucketMonthlyHistoryUseCase(settings, replaceExisting = true)
    }

    private suspend fun distributeCycleCloseoutToFunds(
        pendingCycle: net.loeu.wallybudget.domain.usecase.internal.CycleRange,
        settings: UserSettings
    ) {
        val cyclePolicies = budgetPolicyDao.getAllForSnapshot()
            .filter { it.deletedAtEpochMs == null }
            .map { it.policyToDomainModel() }
        val cyclePolicy = cycleScheduleResolver.policyForCycleStart(
            cycleStart = pendingCycle.start,
            settings = settings,
            policies = cyclePolicies
        )
        val cycleExpenses = expenseDao.getAllForSnapshot()
            .filter { it.deletedAtEpochMs == null }
            .filter {
                it.expenseDate >= pendingCycle.start.toString() &&
                    it.expenseDate < pendingCycle.endExclusive.toString()
            }
        val bucketPolicies = bucketAllocationPolicyDao.getAllForSnapshot()
            .filter { it.deletedAtEpochMs == null && it.cycleStartDate == pendingCycle.start.toString() }
        val activeFunds = fundDao.getAllActive()
        if (activeFunds.isEmpty()) return

        val totalSurplusCents = bucketPolicies.sumOf { policy ->
            val spent = cycleExpenses
                .filter { it.bucketUuid == policy.bucketUuid && it.deletedAtEpochMs == null }
                .sumOf { it.amountCents }
            (policy.allocatedAmountCents - spent).coerceAtLeast(0L)
        }

        val totalFundAllocationCents = activeFunds.sumOf { it.allocationPerCycleCents }
        val closeoutEpochMs = pendingCycle.endExclusive.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        var remainderCents = totalSurplusCents

        activeFunds.sortedBy { it.sortOrder }.forEachIndexed { index, fund ->
            val surplusShare = when {
                totalSurplusCents <= 0L || totalFundAllocationCents <= 0L -> 0L
                index == activeFunds.lastIndex -> remainderCents
                else -> ((totalSurplusCents * fund.allocationPerCycleCents) / totalFundAllocationCents).also {
                    remainderCents -= it
                }
            }
            val depositAmount = fund.allocationPerCycleCents + surplusShare
            if (depositAmount <= 0L) return@forEachIndexed

            fundTransactionDao.insert(
                FundTransactionEntity(
                    uuid = UUID.randomUUID().toString(),
                    fundUuid = fund.uuid,
                    amountCents = depositAmount,
                    type = FundTransactionType.DEPOSIT,
                    description = "Cycle closeout deposit",
                    dateEpochMs = closeoutEpochMs
                )
            )
            fundDao.update(
                fund.copy(
                    balanceCents = fund.balanceCents + depositAmount,
                    updatedAtEpochMs = closeoutEpochMs
                )
            )
        }
    }
}
