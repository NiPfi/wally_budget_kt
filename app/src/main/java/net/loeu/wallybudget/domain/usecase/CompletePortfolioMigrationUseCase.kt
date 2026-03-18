package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.dao.BudgetAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.BudgetBucketDao
import net.loeu.wallybudget.data.local.db.TransactionRunner
import net.loeu.wallybudget.data.local.entity.toDomainModel as bucketToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel as policyToDomainModel
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.data.time.CurrentDateProvider
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import net.loeu.wallybudget.domain.usecase.internal.lastResetDateOrNull
import net.loeu.wallybudget.domain.usecase.internal.newBudgetPolicy
import java.time.ZoneId

class CompletePortfolioMigrationUseCase(
    private val transactionRunner: TransactionRunner,
    private val userSettingsStore: UserSettingsStore,
    private val budgetPolicyDao: BudgetPolicyDao,
    private val budgetAdjustmentDao: BudgetAdjustmentDao,
    private val budgetBucketDao: BudgetBucketDao,
    private val currentDateProvider: CurrentDateProvider,
    private val budgetCalculationService: BudgetCalculationService,
    private val hybridLogicalClockService: HybridLogicalClockService
) {
    suspend operator fun invoke(portfolioMonthlyBudgetCents: Long) {
        val settings = userSettingsStore.ensureIdentity()
        val defaultBucket = budgetBucketDao.findByBucketUuid(DEFAULT_SPENDING_BUCKET_UUID)?.bucketToDomainModel()
            ?: throw IllegalStateException("Default spending bucket is missing.")
        require(portfolioMonthlyBudgetCents >= defaultBucket.defaultAllocatedAmountCents) {
            "Portfolio budget must cover the default spending bucket."
        }
        val now = currentDateProvider.currentDate()
        val nowEpochMs = now.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val currentCycleStart = settings.lastResetDateOrNull()
            ?: budgetCalculationService.getCycleStartDate(now, settings.paydayDate)
        val currentCycleEnd = budgetCalculationService.getNextCycleStartDate(currentCycleStart, settings.paydayDate)
        val installId = settings.installDeviceId

        transactionRunner.inTransaction {
            budgetPolicyDao.getAllForSnapshot()
                .filter { it.deletedAtEpochMs == null }
                .filter { it.cycleStartDate >= currentCycleStart.toString() }
                .forEach { policy ->
                    budgetPolicyDao.update(
                        policy.copy(
                            deletedAtEpochMs = nowEpochMs,
                            updatedAtEpochMs = nowEpochMs,
                            lastModifiedByInstallId = installId,
                            modClock = hybridLogicalClockService.next(policy.modClock, nowEpochMs, installId)
                        )
                    )
                }
            budgetAdjustmentDao.getAllForSnapshot()
                .filter { it.deletedAtEpochMs == null }
                .filter { it.cycleStartDate >= currentCycleStart.toString() }
                .forEach { adjustment ->
                    budgetAdjustmentDao.update(
                        adjustment.copy(
                            deletedAtEpochMs = nowEpochMs,
                            updatedAtEpochMs = nowEpochMs,
                            lastModifiedByInstallId = installId,
                            modClock = hybridLogicalClockService.next(adjustment.modClock, nowEpochMs, installId)
                        )
                    )
                }

            budgetPolicyDao.insert(
                newBudgetPolicy(
                    cycleStart = currentCycleStart,
                    cycleEndExclusive = currentCycleEnd,
                    budgetAmountCents = portfolioMonthlyBudgetCents,
                    paydayDayOfMonth = settings.paydayDate,
                    installId = installId,
                    nowEpochMs = nowEpochMs,
                    hybridLogicalClockService = hybridLogicalClockService
                ).toEntity()
            )
        }

        userSettingsStore.updatePortfolioMonthlyBudget(portfolioMonthlyBudgetCents)
        val primaryBucketUuid = settings.primaryBucketUuid ?: DEFAULT_SPENDING_BUCKET_UUID
        val selectedBucketUuid = settings.selectedBucketUuid ?: primaryBucketUuid
        userSettingsStore.updateBucketSelection(primaryBucketUuid, selectedBucketUuid)
    }
}
