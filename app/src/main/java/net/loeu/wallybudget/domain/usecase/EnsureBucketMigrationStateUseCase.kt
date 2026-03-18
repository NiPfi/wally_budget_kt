package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.dao.BucketAllocationPolicyDao
import net.loeu.wallybudget.data.local.dao.BudgetBucketDao
import net.loeu.wallybudget.data.local.db.TransactionRunner
import net.loeu.wallybudget.data.local.entity.toDomainModel
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.BucketBalanceBehavior
import net.loeu.wallybudget.domain.model.BucketTrackingMode
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_NAME
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import net.loeu.wallybudget.domain.usecase.internal.lastResetDateOrNull
import net.loeu.wallybudget.domain.usecase.internal.newBucketAllocationPolicy
import java.time.LocalDate
import java.time.ZoneId

class EnsureBucketMigrationStateUseCase(
    private val transactionRunner: TransactionRunner,
    private val userSettingsStore: UserSettingsStore,
    private val budgetBucketDao: BudgetBucketDao,
    private val bucketAllocationPolicyDao: BucketAllocationPolicyDao,
    private val budgetCalculationService: BudgetCalculationService,
    private val hybridLogicalClockService: HybridLogicalClockService
) {
    @Suppress("LongMethod")
    suspend operator fun invoke(now: LocalDate) {
        val settings = userSettingsStore.ensureIdentity()
        val installId = settings.installDeviceId
        val activeBuckets = budgetBucketDao.getAllActive().map { it.toDomainModel() }
        val defaultBucket = activeBuckets.firstOrNull { it.bucketUuid == DEFAULT_SPENDING_BUCKET_UUID }
        val nowEpochMs = now.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        transactionRunner.inTransaction {
            if (defaultBucket == null) {
                budgetBucketDao.insert(
                    BudgetBucket(
                        bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                        name = DEFAULT_SPENDING_BUCKET_NAME,
                        trackingMode = BucketTrackingMode.DAILY_TARGET,
                        balanceBehavior = BucketBalanceBehavior.RETURN_TO_PORTFOLIO,
                        defaultAllocatedAmountCents = settings.monthlyBudgetCents,
                        sortOrder = 0,
                        isPrimary = true,
                        originInstallId = installId,
                        lastModifiedByInstallId = installId,
                        createdAtEpochMs = nowEpochMs,
                        updatedAtEpochMs = nowEpochMs,
                        modClock = hybridLogicalClockService.format(nowEpochMs, 0, installId)
                    ).toEntity()
                )
            } else if (defaultBucket.defaultAllocatedAmountCents == 0L && settings.monthlyBudgetCents > 0L) {
                budgetBucketDao.update(
                    defaultBucket.copy(
                        defaultAllocatedAmountCents = settings.monthlyBudgetCents,
                        updatedAtEpochMs = nowEpochMs,
                        lastModifiedByInstallId = installId,
                        modClock = hybridLogicalClockService.next(defaultBucket.modClock, nowEpochMs, installId)
                    ).toEntity()
                )
            }

            val cycleStart = settings.lastResetDateOrNull()
                ?: budgetCalculationService.getCycleStartDate(now, settings.paydayDate)
            val cycleEnd = budgetCalculationService.getNextCycleStartDate(cycleStart, settings.paydayDate)
            if (
                bucketAllocationPolicyDao.findActivePolicyForCycle(
                    bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                    cycleStartDate = cycleStart.toString()
                ) == null
            ) {
                bucketAllocationPolicyDao.insert(
                    newBucketAllocationPolicy(
                        bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                        cycleStart = cycleStart,
                        cycleEndExclusive = cycleEnd,
                        allocatedAmountCents = settings.monthlyBudgetCents,
                        installId = installId,
                        nowEpochMs = nowEpochMs,
                        hybridLogicalClockService = hybridLogicalClockService
                    ).toEntity()
                )
            }
        }

        val activeBucketUuid = DEFAULT_SPENDING_BUCKET_UUID.takeIf {
            budgetBucketDao.findByBucketUuid(it)?.deletedAtEpochMs == null
        }
        if (settings.primaryBucketUuid == null || settings.selectedBucketUuid == null) {
            userSettingsStore.updateBucketSelection(
                primaryBucketUuid = settings.primaryBucketUuid ?: activeBucketUuid,
                selectedBucketUuid = settings.selectedBucketUuid ?: settings.primaryBucketUuid ?: activeBucketUuid
            )
        }
    }
}
