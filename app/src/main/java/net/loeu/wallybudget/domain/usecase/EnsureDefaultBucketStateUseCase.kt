package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.dao.BucketAllocationAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationPolicyDao
import net.loeu.wallybudget.data.local.dao.BucketCycleBaselineDao
import net.loeu.wallybudget.data.local.dao.BucketTransferDao
import net.loeu.wallybudget.data.local.dao.BudgetBucketDao
import net.loeu.wallybudget.data.local.db.TransactionRunner
import net.loeu.wallybudget.data.local.entity.toDomainModel
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_NAME
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.CurrentCycleBucketAllocationResolver
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import net.loeu.wallybudget.domain.usecase.internal.lastResetDateOrNull
import net.loeu.wallybudget.domain.usecase.internal.newBucketAllocationPolicy
import net.loeu.wallybudget.domain.usecase.internal.resolveSelectedOpenBucketUuid
import net.loeu.wallybudget.domain.usecase.internal.resolveCurrentCycleDefaultAllocation
import net.loeu.wallybudget.domain.usecase.internal.upsertCurrentCycleBucketBaselineAmount
import java.time.LocalDate
import java.time.ZoneId

class EnsureDefaultBucketStateUseCase(
    private val transactionRunner: TransactionRunner,
    private val userSettingsStore: UserSettingsStore,
    private val budgetBucketDao: BudgetBucketDao,
    private val bucketAllocationPolicyDao: BucketAllocationPolicyDao,
    private val bucketCycleBaselineDao: BucketCycleBaselineDao? = null,
    private val bucketTransferDao: BucketTransferDao? = null,
    private val bucketAllocationAdjustmentDao: BucketAllocationAdjustmentDao,
    private val budgetCalculationService: BudgetCalculationService,
    private val currentCycleBucketAllocationResolver: CurrentCycleBucketAllocationResolver =
        CurrentCycleBucketAllocationResolver(),
    private val hybridLogicalClockService: HybridLogicalClockService
) {
    private data class CurrentCycleDefaultRepair(
        val cycleStart: LocalDate,
        val cycleEndExclusive: LocalDate,
        val allocatedAmountCents: Long
    )

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    suspend operator fun invoke(now: LocalDate) {
        var settings = userSettingsStore.ensureIdentity()
        if (settings.portfolioMonthlyBudgetCents == null) {
            userSettingsStore.updatePortfolioMonthlyBudget(settings.monthlyBudgetCents)
            settings = userSettingsStore.ensureIdentity()
        }

        val allBuckets = budgetBucketDao.getAllForSnapshot().map { it.toDomainModel() }
        val openOtherBuckets = allBuckets.filterNot { it.isClosed || it.bucketUuid == DEFAULT_SPENDING_BUCKET_UUID }
        val defaultBucketAllocation = (
            settings.resolvedPortfolioMonthlyBudgetCents - openOtherBuckets.sumOf { it.defaultAllocatedAmountCents }
        ).coerceAtLeast(0L)
        val defaultBucket = allBuckets.firstOrNull { it.bucketUuid == DEFAULT_SPENDING_BUCKET_UUID }
        val nowEpochMs = now.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val installId = settings.installDeviceId
        val currentCycleDefaultRepair = currentCycleDefaultRepair(settings, now, allBuckets)

        transactionRunner.inTransaction {
            val currentPolicy = bucketAllocationPolicyDao.findActivePolicyForCycle(
                bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                cycleStartDate = currentCycleDefaultRepair.cycleStart.toString()
            )
            val repairedCurrentDefaultAllocation = currentCycleDefaultRepair.allocatedAmountCents
            when {
                defaultBucket == null -> {
                    budgetBucketDao.insert(
                        BudgetBucket(
                            bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                            name = DEFAULT_SPENDING_BUCKET_NAME,
                            defaultAllocatedAmountCents = defaultBucketAllocation,
                            sortOrder = 0,
                            originInstallId = installId,
                            lastModifiedByInstallId = installId,
                            createdAtEpochMs = nowEpochMs,
                            updatedAtEpochMs = nowEpochMs,
                            modClock = hybridLogicalClockService.format(nowEpochMs, 0, installId)
                        ).toEntity()
                    )
                }

                defaultBucket.isClosed ||
                    defaultBucket.sortOrder != 0 ||
                    defaultBucket.defaultAllocatedAmountCents != defaultBucketAllocation -> {
                    val entity = budgetBucketDao.findByBucketUuid(DEFAULT_SPENDING_BUCKET_UUID) ?: return@inTransaction
                    // Only reopen the default bucket when it was actually closed/deleted;
                    // preserve its closed/deleted state when the trigger is just a
                    // sort-order or allocation mismatch.
                    budgetBucketDao.update(
                        defaultBucket.copy(
                            defaultAllocatedAmountCents = defaultBucketAllocation,
                            sortOrder = 0,
                            updatedAtEpochMs = nowEpochMs,
                            lastModifiedByInstallId = installId,
                            closedAtEpochMs = if (defaultBucket.isClosed) null else defaultBucket.closedAtEpochMs,
                            deletedAtEpochMs = if (defaultBucket.isClosed) null else defaultBucket.deletedAtEpochMs,
                            modClock = hybridLogicalClockService.next(defaultBucket.modClock, nowEpochMs, installId)
                        ).toEntity(id = entity.id)
                    )
                }
            }

            when {
                currentPolicy == null -> {
                    bucketAllocationPolicyDao.insert(
                        newBucketAllocationPolicy(
                            bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                            cycleStart = currentCycleDefaultRepair.cycleStart,
                            cycleEndExclusive = currentCycleDefaultRepair.cycleEndExclusive,
                            allocatedAmountCents = repairedCurrentDefaultAllocation,
                            installId = installId,
                            nowEpochMs = nowEpochMs,
                            hybridLogicalClockService = hybridLogicalClockService
                        ).toEntity()
                    )
                }

                currentPolicy.allocatedAmountCents != repairedCurrentDefaultAllocation -> {
                    bucketAllocationPolicyDao.update(
                        currentPolicy.copy(
                            allocatedAmountCents = repairedCurrentDefaultAllocation,
                            updatedAtEpochMs = nowEpochMs,
                            lastModifiedByInstallId = installId,
                            modClock = hybridLogicalClockService.next(
                                previousClock = currentPolicy.modClock,
                                nowEpochMs = nowEpochMs,
                                installId = installId
                            )
                        )
                    )
                    val activeAdjustments = bucketAllocationAdjustmentDao.getActiveForCycle(
                        bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                        cycleStartDate = currentCycleDefaultRepair.cycleStart.toString()
                    )
                    if (activeAdjustments.isNotEmpty()) {
                        bucketAllocationAdjustmentDao.deleteByAdjustmentUuids(
                            activeAdjustments.map { it.adjustmentUuid }
                        )
                    }
                }
            }

            bucketCycleBaselineDao?.let { baselineDao ->
                upsertCurrentCycleBucketBaselineAmount(
                    bucketCycleBaselineDao = baselineDao,
                    bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                    cycleStart = currentCycleDefaultRepair.cycleStart,
                    cycleEndExclusive = currentCycleDefaultRepair.cycleEndExclusive,
                    baselineAmountCents = currentCycleDefaultRepair.allocatedAmountCents,
                    installId = installId,
                    nowEpochMs = nowEpochMs,
                    hybridLogicalClockService = hybridLogicalClockService
                )
            }
        }

        // Bucket selection update runs outside the transaction. A concurrent
        // UpdateBudgetSettingsUseCase could race, but the worst outcome is a redundant
        // re-selection on the next app launch — no data corruption is possible.
        val openBuckets = budgetBucketDao.getAllActive().map { it.toDomainModel() }
        val resolvedSelectedBucketUuid = resolveSelectedOpenBucketUuid(settings.selectedBucketUuid, openBuckets)
        if (resolvedSelectedBucketUuid != settings.selectedBucketUuid) {
            userSettingsStore.updateSelectedBucket(resolvedSelectedBucketUuid)
        }
    }

    private suspend fun currentCycleDefaultRepair(
        settings: UserSettings,
        now: LocalDate,
        allBuckets: List<BudgetBucket>
    ): CurrentCycleDefaultRepair {
        val cycleStart = settings.lastResetDateOrNull()
            ?: budgetCalculationService.getCycleStartDate(now, settings.paydayDate)
        val cycleEnd = budgetCalculationService.getNextCycleStartDate(cycleStart, settings.paydayDate)
        val namedBucketsByUuid = allBuckets
            .filter { it.bucketUuid != DEFAULT_SPENDING_BUCKET_UUID && it.isVisibleInCurrentCycle }
            .associateBy { it.bucketUuid }
        val baselines = bucketCycleBaselineDao
            ?.getActiveForCycle(cycleStart.toString())
            ?.map { it.toDomainModel() }
            .orEmpty()
        val transfers = bucketTransferDao
            ?.getForCycle(cycleStart.toString())
            ?.map { it.toDomainModel() }
            .orEmpty()
        val legacyPolicies = bucketAllocationPolicyDao.getAllForSnapshot().map { it.toDomainModel() }
        return CurrentCycleDefaultRepair(
            cycleStart = cycleStart,
            cycleEndExclusive = cycleEnd,
            allocatedAmountCents = resolveCurrentCycleDefaultAllocation(
                portfolioMonthlyBudgetCents = settings.resolvedPortfolioMonthlyBudgetCents,
                namedBuckets = namedBucketsByUuid.values.toList(),
                cycleStart = cycleStart,
                baselines = baselines,
                transfers = transfers,
                legacyPolicies = legacyPolicies,
                currentCycleBucketAllocationResolver = currentCycleBucketAllocationResolver
            )
        )
    }
}
