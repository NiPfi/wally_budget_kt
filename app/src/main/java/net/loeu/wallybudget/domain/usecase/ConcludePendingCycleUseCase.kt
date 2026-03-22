package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.dao.BudgetAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationPolicyDao
import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.dao.FundDao
import net.loeu.wallybudget.data.local.dao.FundTransactionDao
import net.loeu.wallybudget.data.local.dao.MonthlyHistoryDao
import net.loeu.wallybudget.data.local.db.TransactionRunner
import net.loeu.wallybudget.data.local.entity.FundEntity
import net.loeu.wallybudget.data.local.entity.FundTransactionEntity
import net.loeu.wallybudget.data.local.entity.toDomainModel
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.domain.model.DEFAULT_FUND_UUID
import net.loeu.wallybudget.domain.model.FundTransactionType
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BucketAllocationResolver
import net.loeu.wallybudget.domain.service.BudgetAdjustmentResolver
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import net.loeu.wallybudget.domain.usecase.internal.archiveCycleIfNeeded
import net.loeu.wallybudget.domain.usecase.internal.emptyBucketAllocationAdjustmentDao
import net.loeu.wallybudget.domain.usecase.internal.emptyBucketAllocationPolicyDao
import net.loeu.wallybudget.domain.usecase.internal.emptyFundDao
import net.loeu.wallybudget.domain.usecase.internal.CycleRange
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
    private val bucketAllocationAdjustmentDao: BucketAllocationAdjustmentDao = emptyBucketAllocationAdjustmentDao,
    private val monthlyHistoryDao: MonthlyHistoryDao,
    private val fundDao: FundDao = emptyFundDao,
    private val fundTransactionDao: FundTransactionDao = emptyFundTransactionDao,
    private val userSettingsStore: UserSettingsStore,
    private val budgetCalculationService: BudgetCalculationService,
    private val cycleScheduleResolver: CycleScheduleResolver,
    private val budgetAdjustmentResolver: BudgetAdjustmentResolver,
    private val bucketAllocationResolver: BucketAllocationResolver,
    private val hybridLogicalClockService: HybridLogicalClockService,
    private val rebuildBucketMonthlyHistoryUseCase: RebuildBucketMonthlyHistoryUseCase
) {
    suspend operator fun invoke(settings: UserSettings) {
        val pendingCycle = settings.pendingCycleRangeOrNull() ?: return
        val identitySettings = userSettingsStore.ensureIdentity()
        transactionRunner.inTransaction {
            val policies = budgetPolicyDao.getAllForSnapshot()
                .filter { it.deletedAtEpochMs == null }
                .map { it.toDomainModel() }
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
                    .map { it.toDomainModel() },
                settings = settings,
                cycleStart = pendingCycle.start,
                cycleEnd = pendingCycle.endExclusive
            )
            distributeCycleCloseoutToFunds(
                pendingCycle = pendingCycle,
                installId = identitySettings.installDeviceId
            )
        }
        userSettingsStore.clearPendingCycle()
        rebuildBucketMonthlyHistoryUseCase(settings, replaceExisting = true)
    }

    private suspend fun distributeCycleCloseoutToFunds(
        pendingCycle: CycleRange,
        installId: String
    ) {
        val activeFunds = fundDao.getAllActive()
        if (activeFunds.isEmpty()) return

        val totalSurplusCents = calculateCycleCloseoutSurplusCents(pendingCycle)
        depositCloseoutAmounts(
            activeFunds = activeFunds,
            totalSurplusCents = totalSurplusCents,
            closeoutEpochMs = pendingCycle.endExclusive.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            installId = installId
        )
    }

    private suspend fun depositCloseoutAmounts(
        activeFunds: List<FundEntity>,
        totalSurplusCents: Long,
        closeoutEpochMs: Long,
        installId: String
    ) {
        val sortedFunds = activeFunds.sortedWith(compareBy({ it.sortOrder }, { it.createdAtEpochMs }, { it.uuid }))
        val totalFundAllocationCents = sortedFunds.sumOf { it.allocationPerCycleCents }
        var remainderCents = totalSurplusCents
        val zeroAllocationSurplusRecipientUuid = sortedFunds.firstOrNull { it.uuid == DEFAULT_FUND_UUID }?.uuid
            ?: sortedFunds.first().uuid

        sortedFunds.forEachIndexed { index, fund ->
            val surplusShare = resolveSurplusShare(
                fund = fund,
                index = index,
                lastIndex = sortedFunds.lastIndex,
                totalSurplusCents = totalSurplusCents,
                totalFundAllocationCents = totalFundAllocationCents,
                zeroAllocationSurplusRecipientUuid = zeroAllocationSurplusRecipientUuid,
                remainderCents = remainderCents
            )
            if (totalSurplusCents > 0L && totalFundAllocationCents > 0L && index != sortedFunds.lastIndex) {
                remainderCents -= surplusShare
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
                    updatedAtEpochMs = closeoutEpochMs,
                    lastModifiedByInstallId = installId,
                    modClock = hybridLogicalClockService.next(fund.modClock, closeoutEpochMs, installId)
                )
            )
        }
    }

    private suspend fun calculateCycleCloseoutSurplusCents(
        pendingCycle: CycleRange
    ): Long {
        val cycleExpenses = expenseDao.getInRange(
            startDateInclusive = pendingCycle.start.toString(),
            endDateExclusive = pendingCycle.endExclusive.toString()
        )
        val bucketPolicies = bucketAllocationPolicyDao.getAllForSnapshot()
            .filter { it.deletedAtEpochMs == null && it.cycleStartDate == pendingCycle.start.toString() }

        return bucketPolicies.sumOf { policy ->
            val adjustments = bucketAllocationAdjustmentDao.getActiveForCycle(
                bucketUuid = policy.bucketUuid,
                cycleStartDate = policy.cycleStartDate
            ).map { it.toDomainModel() }
            val effectiveAllocatedAmount = bucketAllocationResolver.resolveEffectiveCycleAllocationAmount(
                cycleStart = pendingCycle.start,
                cycleEndExclusive = pendingCycle.endExclusive,
                baseAllocatedAmountCents = policy.allocatedAmountCents,
                adjustments = adjustments
            )
            val spent = cycleExpenses
                .asSequence()
                .filter { it.bucketUuid == policy.bucketUuid }
                .sumOf { it.amountCents }
            (effectiveAllocatedAmount - spent).coerceAtLeast(0L)
        }
    }

    private fun resolveSurplusShare(
        fund: FundEntity,
        index: Int,
        lastIndex: Int,
        totalSurplusCents: Long,
        totalFundAllocationCents: Long,
        zeroAllocationSurplusRecipientUuid: String,
        remainderCents: Long
    ): Long {
        return when {
            totalSurplusCents <= 0L -> 0L
            totalFundAllocationCents <= 0L -> if (fund.uuid == zeroAllocationSurplusRecipientUuid) {
                totalSurplusCents
            } else {
                0L
            }
            index == lastIndex -> remainderCents
            else -> (totalSurplusCents * fund.allocationPerCycleCents) / totalFundAllocationCents
        }
    }
}
