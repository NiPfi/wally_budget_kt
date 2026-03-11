package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.data.time.CurrentDateProvider
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import net.loeu.wallybudget.domain.usecase.internal.newBudgetPolicy

class UpdateMonthlyBudgetUseCase(
    private val userSettingsStore: UserSettingsStore,
    private val expenseDao: ExpenseDao,
    private val budgetPolicyDao: BudgetPolicyDao,
    private val currentDateProvider: CurrentDateProvider,
    private val budgetCalculationService: BudgetCalculationService,
    private val hybridLogicalClockService: HybridLogicalClockService
) {
    suspend operator fun invoke(amountCents: Long) {
        val settings = userSettingsStore.ensureIdentity()
        val currentDate = currentDateProvider.currentDate()
        val cycleStart = budgetCalculationService.getCycleStartDate(currentDate, settings.paydayDate)
        val cycleEnd = budgetCalculationService.getNextCycleStartDate(currentDate, settings.paydayDate)
        val currentCycleExpenseCount = expenseDao.countInRange(cycleStart.toString(), currentDate.plusDays(1).toString())

        if (currentCycleExpenseCount == 0) {
            val currentPolicy = budgetPolicyDao.findActivePolicyForCycle(cycleStart.toString())
            val now = System.currentTimeMillis()
            if (currentPolicy == null) {
                budgetPolicyDao.insert(
                    newBudgetPolicy(
                        cycleStart = cycleStart,
                        cycleEndExclusive = cycleEnd,
                        budgetAmountCents = amountCents,
                        paydayDayOfMonth = settings.paydayDate,
                        installId = settings.installDeviceId,
                        nowEpochMs = now,
                        hybridLogicalClockService = hybridLogicalClockService
                    ).toEntity()
                )
            } else {
                budgetPolicyDao.update(
                    currentPolicy.copy(
                        budgetAmountCents = amountCents,
                        updatedAtEpochMs = now,
                        lastModifiedByInstallId = settings.installDeviceId,
                        modClock = hybridLogicalClockService.next(
                            previousClock = currentPolicy.modClock,
                            nowEpochMs = now,
                            installId = settings.installDeviceId
                        )
                    )
                )
            }
        }
        userSettingsStore.updateMonthlyBudget(amountCents)
    }
}
