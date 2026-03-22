package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.dao.BucketAllocationAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationPolicyDao
import net.loeu.wallybudget.data.local.dao.BucketMonthlyHistoryDao
import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.entity.BucketAllocationPolicyEntity
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
    private data class CycleWindow(
        val startDate: String,
        val endDateExclusive: String
    )

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

        val totalSpentByWindowAndBucket = loadTotalSpentByWindowAndBucket(applicablePolicies)

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
            val totalSpentCents = totalSpentByWindowAndBucket[
                CycleWindow(
                    startDate = policy.cycleStartDate,
                    endDateExclusive = policy.cycleEndDateExclusive
                )
            ]?.get(policy.bucketUuid) ?: 0L

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

    private suspend fun loadTotalSpentByWindowAndBucket(
        applicablePolicies: List<BucketAllocationPolicyEntity>
    ): Map<CycleWindow, Map<String, Long>> {
        return applicablePolicies
            .map { CycleWindow(startDate = it.cycleStartDate, endDateExclusive = it.cycleEndDateExclusive) }
            .distinct()
            .associateWith { window ->
                expenseDao.totalSpentPerBucketInRange(
                    startDateInclusive = window.startDate,
                    endDateExclusive = window.endDateExclusive
                ).associate { row ->
                    row.bucketUuid to row.totalSpentCents
                }
            }
    }
}
