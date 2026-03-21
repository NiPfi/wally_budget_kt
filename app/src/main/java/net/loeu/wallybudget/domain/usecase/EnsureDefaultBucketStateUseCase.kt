package net.loeu.wallybudget.domain.usecase

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
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import net.loeu.wallybudget.domain.usecase.internal.resolveSelectedOpenBucketUuid
import java.time.LocalDate
import java.time.ZoneId

class EnsureDefaultBucketStateUseCase(
    private val transactionRunner: TransactionRunner,
    private val userSettingsStore: UserSettingsStore,
    private val budgetBucketDao: BudgetBucketDao,
    private val hybridLogicalClockService: HybridLogicalClockService
) {
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    suspend operator fun invoke(now: LocalDate) {
        var settings = userSettingsStore.ensureIdentity()
        if (settings.portfolioMonthlyBudgetCents == null) {
            userSettingsStore.updatePortfolioMonthlyBudget(settings.monthlyBudgetCents)
            settings = userSettingsStore.ensureIdentity()
        }
        if (settings.leftoverReceiverBucketUuid == null) {
            userSettingsStore.updateLeftoverReceiverBucket(DEFAULT_SPENDING_BUCKET_UUID)
            settings = userSettingsStore.ensureIdentity()
        }

        val allBuckets = budgetBucketDao.getAllForSnapshot().map { it.toDomainModel() }
        val defaultBucket = allBuckets.firstOrNull { it.bucketUuid == DEFAULT_SPENDING_BUCKET_UUID }
        val nowEpochMs = now.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val installId = settings.installDeviceId

        transactionRunner.inTransaction {
            when {
                defaultBucket == null -> {
                    budgetBucketDao.insert(
                        BudgetBucket(
                            bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                            name = DEFAULT_SPENDING_BUCKET_NAME,
                            trackingMode = BucketTrackingMode.DAILY_TARGET,
                            balanceBehavior = BucketBalanceBehavior.RETURN_TO_PORTFOLIO,
                            defaultAllocatedAmountCents = settings.resolvedPortfolioMonthlyBudgetCents,
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
                    defaultBucket.trackingMode != BucketTrackingMode.DAILY_TARGET ||
                    defaultBucket.balanceBehavior != BucketBalanceBehavior.RETURN_TO_PORTFOLIO ||
                    defaultBucket.sortOrder != 0 -> {
                    val entity = budgetBucketDao.findByBucketUuid(DEFAULT_SPENDING_BUCKET_UUID) ?: return@inTransaction
                    budgetBucketDao.update(
                        defaultBucket.copy(
                            trackingMode = BucketTrackingMode.DAILY_TARGET,
                            balanceBehavior = BucketBalanceBehavior.RETURN_TO_PORTFOLIO,
                            sortOrder = 0,
                            updatedAtEpochMs = nowEpochMs,
                            lastModifiedByInstallId = installId,
                            closedAtEpochMs = null,
                            deletedAtEpochMs = null,
                            modClock = hybridLogicalClockService.next(defaultBucket.modClock, nowEpochMs, installId)
                        ).toEntity(id = entity.id)
                    )
                }
            }
        }

        val openBuckets = budgetBucketDao.getAllActive().map { it.toDomainModel() }
        val resolvedSelectedBucketUuid = resolveSelectedOpenBucketUuid(settings.selectedBucketUuid, openBuckets)
        if (resolvedSelectedBucketUuid != settings.selectedBucketUuid) {
            userSettingsStore.updateSelectedBucket(resolvedSelectedBucketUuid)
        }
    }
}
