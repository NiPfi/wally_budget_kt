package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.dao.BudgetAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BudgetBucketDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationPolicyDao
import net.loeu.wallybudget.data.local.dao.BucketCycleBaselineDao
import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.BucketTransferDao
import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.dao.FundDao
import net.loeu.wallybudget.data.local.dao.FundTransactionDao
import net.loeu.wallybudget.data.local.dao.MonthlyHistoryDao
import net.loeu.wallybudget.data.local.db.TransactionRunner
import net.loeu.wallybudget.data.local.entity.FundEntity
import net.loeu.wallybudget.data.local.entity.FundTransactionEntity
import net.loeu.wallybudget.data.local.entity.toDomainModel as baselineToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel as transferToDomainModel
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.domain.model.DEFAULT_FUND_UUID
import net.loeu.wallybudget.domain.model.FundTransactionType
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BucketAllocationResolver
import net.loeu.wallybudget.domain.service.BudgetAdjustmentResolver
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import net.loeu.wallybudget.domain.service.CurrentCycleBucketAllocationResolver
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import net.loeu.wallybudget.domain.usecase.internal.archiveCycleIfNeeded
import net.loeu.wallybudget.domain.usecase.internal.CycleRange
import net.loeu.wallybudget.domain.usecase.internal.pendingCycleRangeOrNull
import net.loeu.wallybudget.domain.usecase.internal.resolveCurrentCycleAllocationSnapshot
import java.time.ZoneId
import java.util.UUID

class ConcludePendingCycleUseCase(
    private val transactionRunner: TransactionRunner,
    private val expenseDao: ExpenseDao,
    private val budgetPolicyDao: BudgetPolicyDao,
    private val budgetAdjustmentDao: BudgetAdjustmentDao,
    private val budgetBucketDao: BudgetBucketDao,
    private val bucketAllocationPolicyDao: BucketAllocationPolicyDao,
    private val bucketCycleBaselineDao: BucketCycleBaselineDao? = null,
    private val bucketTransferDao: BucketTransferDao? = null,
    private val bucketAllocationAdjustmentDao: BucketAllocationAdjustmentDao,
    private val monthlyHistoryDao: MonthlyHistoryDao,
    private val fundDao: FundDao,
    private val fundTransactionDao: FundTransactionDao,
    private val userSettingsStore: UserSettingsStore,
    private val budgetCalculationService: BudgetCalculationService,
    private val cycleScheduleResolver: CycleScheduleResolver,
    private val budgetAdjustmentResolver: BudgetAdjustmentResolver,
    @Suppress("UNUSED_PARAMETER")
    private val bucketAllocationResolver: BucketAllocationResolver? = null,
    private val currentCycleBucketAllocationResolver: CurrentCycleBucketAllocationResolver =
        CurrentCycleBucketAllocationResolver(),
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
            finalizeSettledClosingBuckets(
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
        // allocationPerCycleCents is retained as a closeout deposit weight/base contribution only.
        val totalFundCloseoutWeightCents = sortedFunds.sumOf { it.allocationPerCycleCents }
        var remainderCents = totalSurplusCents
        val zeroAllocationSurplusRecipientUuid = sortedFunds.firstOrNull { it.uuid == DEFAULT_FUND_UUID }?.uuid
            ?: sortedFunds.first().uuid

        sortedFunds.forEachIndexed { index, fund ->
            val surplusShare = resolveSurplusShare(
                fund = fund,
                index = index,
                lastIndex = sortedFunds.lastIndex,
                totalSurplusCents = totalSurplusCents,
                totalFundCloseoutWeightCents = totalFundCloseoutWeightCents,
                zeroAllocationSurplusRecipientUuid = zeroAllocationSurplusRecipientUuid,
                remainderCents = remainderCents
            )
            if (totalSurplusCents > 0L && totalFundCloseoutWeightCents > 0L && index != sortedFunds.lastIndex) {
                remainderCents -= surplusShare
            }
            val fundCloseoutBaseContribution = fund.allocationPerCycleCents
            val depositAmount = fundCloseoutBaseContribution + surplusShare
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
        val spentByBucketUuid = expenseDao.totalSpentPerBucketInRange(
            startDateInclusive = pendingCycle.start.toString(),
            endDateExclusive = pendingCycle.endExclusive.toString()
        ).associate { it.bucketUuid to it.totalSpentCents }
        val buckets = budgetBucketDao.getAllForSnapshot()
            .filter { it.deletedAtEpochMs == null && it.closedAtEpochMs == null }
            .map { it.toDomainModel() }
        val baselines = bucketCycleBaselineDao?.getActiveForCycle(pendingCycle.start.toString())
            ?.map { it.baselineToDomainModel() }.orEmpty()
        val transfers = bucketTransferDao?.getForCycle(pendingCycle.start.toString())
            ?.map { it.transferToDomainModel() }.orEmpty()
        val legacyPolicies = bucketAllocationPolicyDao.getAllForSnapshot()
            .filter { it.deletedAtEpochMs == null && it.cycleStartDate == pendingCycle.start.toString() }
            .map { it.toDomainModel() }
        val allocationsByBucketUuid = resolveCurrentCycleAllocationSnapshot(
            buckets = buckets,
            cycleStart = pendingCycle.start,
            baselines = baselines,
            transfers = transfers,
            legacyPolicies = legacyPolicies,
            currentCycleBucketAllocationResolver = currentCycleBucketAllocationResolver
        )

        val netSurplusCents = if (buckets.isEmpty()) {
            legacyPolicies.sumOf { policy ->
                val spent = spentByBucketUuid[policy.bucketUuid] ?: 0L
                policy.allocatedAmountCents - spent
            }
        } else {
            buckets.sumOf { bucket ->
                val spent = spentByBucketUuid[bucket.bucketUuid] ?: 0L
                val allocation = allocationsByBucketUuid[bucket.bucketUuid] ?: bucket.defaultAllocatedAmountCents
                allocation - spent
            }
        }
        return netSurplusCents.coerceAtLeast(0L)
    }

    private suspend fun finalizeSettledClosingBuckets(
        pendingCycle: CycleRange,
        installId: String
    ) {
        budgetBucketDao.getAllForSnapshot()
            .filter {
                it.deletedAtEpochMs == null &&
                    it.closedAtEpochMs == null &&
                    it.settledCloseCycleEndDateExclusive == pendingCycle.endExclusive.toString()
            }
            .forEach { bucket ->
                budgetBucketDao.update(
                    bucket.copy(
                        settledCloseCycleEndDateExclusive = null,
                        closedAtEpochMs = pendingCycle.endExclusive
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli(),
                        updatedAtEpochMs = pendingCycle.endExclusive
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli(),
                        lastModifiedByInstallId = installId,
                        modClock = hybridLogicalClockService.next(
                            previousClock = bucket.modClock,
                            nowEpochMs = pendingCycle.endExclusive
                                .atStartOfDay(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli(),
                            installId = installId
                        )
                    )
                )
            }
    }

    private fun resolveSurplusShare(
        fund: FundEntity,
        index: Int,
        lastIndex: Int,
        totalSurplusCents: Long,
        totalFundCloseoutWeightCents: Long,
        zeroAllocationSurplusRecipientUuid: String,
        remainderCents: Long
    ): Long {
        return when {
            totalSurplusCents <= 0L -> 0L
            totalFundCloseoutWeightCents <= 0L -> if (fund.uuid == zeroAllocationSurplusRecipientUuid) {
                totalSurplusCents
            } else {
                0L
            }
            index == lastIndex -> remainderCents
            else -> (totalSurplusCents * fund.allocationPerCycleCents) / totalFundCloseoutWeightCents
        }
    }
}
