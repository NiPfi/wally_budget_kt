package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.dao.BudgetAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.dao.MonthlyHistoryDao
import net.loeu.wallybudget.data.local.entity.toDomainModel as adjustmentToDomainModel
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.domain.model.MonthlyHistory
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BudgetAdjustmentResolver
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.usecase.internal.lastResetDateOrNull
import net.loeu.wallybudget.domain.usecase.internal.toStartOfDayMillis
import java.time.LocalDate

class RebuildMonthlyHistoryUseCase(
    private val budgetPolicyDao: BudgetPolicyDao,
    private val budgetAdjustmentDao: BudgetAdjustmentDao,
    private val expenseDao: ExpenseDao,
    private val monthlyHistoryDao: MonthlyHistoryDao,
    private val budgetCalculationService: BudgetCalculationService,
    private val budgetAdjustmentResolver: BudgetAdjustmentResolver
) {
    suspend operator fun invoke(settings: UserSettings, replaceExisting: Boolean = false) {
        val completedUntil = settings.lastResetDateOrNull()
        if (completedUntil == null) {
            if (replaceExisting) {
                monthlyHistoryDao.deleteAll()
            }
            return
        }

        val applicablePolicies = budgetPolicyDao.getAllForSnapshot()
            .filter { it.deletedAtEpochMs == null }
            .filter { it.cycleEndDateExclusive <= completedUntil.toString() }
            .sortedBy { it.cycleStartDate }

        if (applicablePolicies.isEmpty()) {
            if (replaceExisting) {
                monthlyHistoryDao.deleteAll()
            }
            return
        }

        monthlyHistoryDao.deleteAll()

        applicablePolicies.forEach { policy ->
                val adjustments = budgetAdjustmentDao.getActiveForCycle(policy.cycleStartDate)
                    .map { it.adjustmentToDomainModel() }
                val effectiveBudgetAmount = budgetAdjustmentResolver.resolveEffectiveCycleBudgetAmount(
                    cycleStart = LocalDate.parse(policy.cycleStartDate),
                    cycleEndExclusive = LocalDate.parse(policy.cycleEndDateExclusive),
                    baseMonthlyBudgetCents = policy.budgetAmountCents,
                    adjustments = adjustments
                )
                val totalSpentCents = expenseDao.totalSpentInRange(
                    startDateInclusive = policy.cycleStartDate,
                    endDateExclusive = policy.cycleEndDateExclusive
                ) ?: 0L
                monthlyHistoryDao.insert(
                    MonthlyHistory(
                        cycleStartDate = policy.cycleStartDate,
                        budgetAmountCents = effectiveBudgetAmount,
                        totalSpentCents = totalSpentCents,
                        surplusCents = budgetCalculationService.calculateSurplus(
                            monthlyBudgetCents = effectiveBudgetAmount,
                            totalSpentCents = totalSpentCents
                        ),
                        cycleEndDate = policy.cycleEndDateExclusive,
                        endTimestamp = LocalDate.parse(policy.cycleEndDateExclusive).toStartOfDayMillis()
                    ).toEntity()
                )
        }
    }
}
