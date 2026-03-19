package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.dao.BucketAllocationPolicyDao
import net.loeu.wallybudget.data.local.dao.BudgetBucketDao
import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.MonthlyHistoryDao
import net.loeu.wallybudget.data.local.entity.toEntity as budgetPolicyToEntity
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.data.local.db.TransactionRunner
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.data.time.CurrentDateProvider
import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.BucketBalanceBehavior
import net.loeu.wallybudget.domain.model.BucketTrackingMode
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_NAME
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import net.loeu.wallybudget.domain.model.MonthlyHistory
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import net.loeu.wallybudget.domain.usecase.internal.newBucketAllocationPolicy
import net.loeu.wallybudget.domain.usecase.internal.newBudgetPolicy
import net.loeu.wallybudget.domain.usecase.internal.toStartOfDayMillis
import java.time.LocalDate
import java.time.ZoneId

@Suppress("LongMethod")
class CompleteOnboardingUseCase(
    private val transactionRunner: TransactionRunner,
    private val budgetBucketDao: BudgetBucketDao,
    private val bucketAllocationPolicyDao: BucketAllocationPolicyDao,
    private val budgetPolicyDao: BudgetPolicyDao,
    private val monthlyHistoryDao: MonthlyHistoryDao,
    private val userSettingsStore: UserSettingsStore,
    private val currentDateProvider: CurrentDateProvider,
    private val budgetCalculationService: BudgetCalculationService,
    private val hybridLogicalClockService: HybridLogicalClockService
) {
    suspend operator fun invoke(
        monthlyBudgetCents: Long,
        paydayDate: Int,
        cycleStartDate: LocalDate,
        previousExpensesCents: Long
    ) {
        val settings = userSettingsStore.ensureIdentity()
        val installId = settings.installDeviceId
        val nowEpochMs = currentDateProvider.currentDate()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        if (previousExpensesCents > 0L) {
            val previousCycleStart = budgetCalculationService.getCycleStartDate(
                cycleStartDate.minusDays(1),
                paydayDate
            )
            transactionRunner.inTransaction {
                budgetPolicyDao.insert(
                    newBudgetPolicy(
                        cycleStart = previousCycleStart,
                        cycleEndExclusive = cycleStartDate,
                        budgetAmountCents = monthlyBudgetCents,
                        paydayDayOfMonth = paydayDate,
                        installId = installId,
                        nowEpochMs = nowEpochMs,
                        hybridLogicalClockService = hybridLogicalClockService
                    ).budgetPolicyToEntity()
                )
                monthlyHistoryDao.insert(
                    MonthlyHistory(
                        cycleStartDate = previousCycleStart.toString(),
                        budgetAmountCents = monthlyBudgetCents,
                        totalSpentCents = previousExpensesCents,
                        surplusCents = budgetCalculationService.calculateSurplus(
                            monthlyBudgetCents = monthlyBudgetCents,
                            totalSpentCents = previousExpensesCents
                        ),
                        cycleEndDate = cycleStartDate.toString(),
                        endTimestamp = cycleStartDate.toStartOfDayMillis()
                    ).toEntity()
                )
            }
        }

        transactionRunner.inTransaction {
            if (budgetBucketDao.findByBucketUuid(DEFAULT_SPENDING_BUCKET_UUID) == null) {
                budgetBucketDao.insert(
                    BudgetBucket(
                        bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                        name = DEFAULT_SPENDING_BUCKET_NAME,
                        trackingMode = BucketTrackingMode.DAILY_TARGET,
                        balanceBehavior = BucketBalanceBehavior.RETURN_TO_PORTFOLIO,
                        defaultAllocatedAmountCents = monthlyBudgetCents,
                        sortOrder = 0,
                        originInstallId = installId,
                        lastModifiedByInstallId = installId,
                        createdAtEpochMs = nowEpochMs,
                        updatedAtEpochMs = nowEpochMs,
                        modClock = hybridLogicalClockService.format(nowEpochMs, 0, installId)
                    ).toEntity()
                )
            }
            val currentCycleEnd = budgetCalculationService.getNextCycleStartDate(cycleStartDate, paydayDate)
            if (budgetPolicyDao.findActivePolicyForCycle(cycleStartDate.toString()) == null) {
                budgetPolicyDao.insert(
                    newBudgetPolicy(
                        cycleStart = cycleStartDate,
                        cycleEndExclusive = currentCycleEnd,
                        budgetAmountCents = monthlyBudgetCents,
                        paydayDayOfMonth = paydayDate,
                        installId = installId,
                        nowEpochMs = nowEpochMs,
                        hybridLogicalClockService = hybridLogicalClockService
                    ).budgetPolicyToEntity()
                )
            }
            if (
                bucketAllocationPolicyDao.findActivePolicyForCycle(
                    bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                    cycleStartDate = cycleStartDate.toString()
                ) == null
            ) {
                bucketAllocationPolicyDao.insert(
                    newBucketAllocationPolicy(
                        bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                        cycleStart = cycleStartDate,
                        cycleEndExclusive = currentCycleEnd,
                        allocatedAmountCents = monthlyBudgetCents,
                        installId = installId,
                        nowEpochMs = nowEpochMs,
                        hybridLogicalClockService = hybridLogicalClockService
                    ).toEntity()
                )
            }
        }

        userSettingsStore.updateMonthlyBudget(monthlyBudgetCents)
        userSettingsStore.updatePortfolioMonthlyBudget(monthlyBudgetCents)
        userSettingsStore.updatePaydayDate(paydayDate)
        userSettingsStore.updateSelectedBucket(DEFAULT_SPENDING_BUCKET_UUID)
        userSettingsStore.updateLastResetTimestamp(cycleStartDate.toStartOfDayMillis())
        userSettingsStore.updateLastSeenDate(currentDateProvider.currentDate())
        userSettingsStore.completeOnboarding()
    }
}
