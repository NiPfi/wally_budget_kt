package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.dao.BucketAllocationAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationPolicyDao
import net.loeu.wallybudget.data.local.dao.BucketMonthlyHistoryDao
import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.entity.toDomainModel as bucketAdjustmentToDomainModel
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.domain.model.BucketMonthlyHistory
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BucketAllocationResolver
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.usecase.internal.lastResetDateOrNull
import net.loeu.wallybudget.domain.usecase.internal.toStartOfDayMillis
import java.time.LocalDate

class RebuildBucketMonthlyHistoryUseCase(
    private val bucketAllocationPolicyDao: BucketAllocationPolicyDao,
    private val bucketAllocationAdjustmentDao: BucketAllocationAdjustmentDao,
    private val expenseDao: ExpenseDao,
    private val bucketMonthlyHistoryDao: BucketMonthlyHistoryDao,
    private val budgetCalculationService: BudgetCalculationService,
    private val bucketAllocationResolver: BucketAllocationResolver
) {
    suspend operator fun invoke(settings: UserSettings, replaceExisting: Boolean = false) {
        val completedUntil = settings.lastResetDateOrNull()
        if (completedUntil == null) {
            if (replaceExisting) {
                bucketMonthlyHistoryDao.deleteAll()
            }
            return
        }

        val applicablePolicies = bucketAllocationPolicyDao.getAllForSnapshot()
            .filter { it.deletedAtEpochMs == null }
            .filter { it.cycleEndDateExclusive <= completedUntil.toString() }
            .sortedWith(compareBy({ it.bucketUuid }, { it.cycleStartDate }))

        if (replaceExisting || applicablePolicies.isEmpty()) {
            bucketMonthlyHistoryDao.deleteAll()
        }
        if (applicablePolicies.isEmpty()) {
            return
        }

        val allExpenses = expenseDao.getAllForSnapshot()
            .filter { it.deletedAtEpochMs == null }

        applicablePolicies.forEach { policy ->
            val adjustments = bucketAllocationAdjustmentDao.getActiveForCycle(
                bucketUuid = policy.bucketUuid,
                cycleStartDate = policy.cycleStartDate
            ).map { it.bucketAdjustmentToDomainModel() }
            val cycleStart = LocalDate.parse(policy.cycleStartDate)
            val cycleEndExclusive = LocalDate.parse(policy.cycleEndDateExclusive)
            val effectiveBudgetAmount = bucketAllocationResolver.resolveEffectiveCycleAllocationAmount(
                cycleStart = cycleStart,
                cycleEndExclusive = cycleEndExclusive,
                baseAllocatedAmountCents = policy.allocatedAmountCents,
                adjustments = adjustments
            )
            val totalSpentCents = allExpenses
                .asSequence()
                .filter { it.bucketUuid == policy.bucketUuid }
                .filter { it.expenseDate >= policy.cycleStartDate && it.expenseDate < policy.cycleEndDateExclusive }
                .sumOf { it.amountCents }

            bucketMonthlyHistoryDao.insert(
                BucketMonthlyHistory(
                    bucketUuid = policy.bucketUuid,
                    cycleStartDate = policy.cycleStartDate,
                    budgetAmountCents = effectiveBudgetAmount,
                    totalSpentCents = totalSpentCents,
                    surplusCents = budgetCalculationService.calculateSurplus(
                        monthlyBudgetCents = effectiveBudgetAmount,
                        totalSpentCents = totalSpentCents
                    ),
                    cycleEndDate = policy.cycleEndDateExclusive,
                    endTimestamp = cycleEndExclusive.toStartOfDayMillis()
                ).toEntity()
            )
        }
    }
}
