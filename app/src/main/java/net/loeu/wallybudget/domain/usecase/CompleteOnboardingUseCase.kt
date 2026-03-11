package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.MonthlyHistoryDao
import net.loeu.wallybudget.data.local.entity.toEntity as budgetPolicyToEntity
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.data.local.db.TransactionRunner
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.data.time.CurrentDateProvider
import net.loeu.wallybudget.domain.model.MonthlyHistory
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import net.loeu.wallybudget.domain.usecase.internal.newBudgetPolicy
import net.loeu.wallybudget.domain.usecase.internal.toStartOfDayMillis
import java.time.LocalDate
import java.time.ZoneId

@Suppress("LongMethod")
class CompleteOnboardingUseCase(
    private val transactionRunner: TransactionRunner,
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
        }

        userSettingsStore.updateMonthlyBudget(monthlyBudgetCents)
        userSettingsStore.updatePaydayDate(paydayDate)
        userSettingsStore.updateLastResetTimestamp(cycleStartDate.toStartOfDayMillis())
        userSettingsStore.updateLastSeenDate(currentDateProvider.currentDate())
        userSettingsStore.completeOnboarding()
    }
}
