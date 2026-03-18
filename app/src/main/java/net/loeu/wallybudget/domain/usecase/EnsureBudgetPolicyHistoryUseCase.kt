package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.MonthlyHistoryDao
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.data.local.entity.toDomainModel
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import net.loeu.wallybudget.domain.usecase.internal.lastResetDateOrNull
import net.loeu.wallybudget.domain.usecase.internal.newBudgetPolicy
import java.time.LocalDate
import java.time.ZoneId

class EnsureBudgetPolicyHistoryUseCase(
    private val budgetPolicyDao: BudgetPolicyDao,
    private val monthlyHistoryDao: MonthlyHistoryDao,
    private val userSettingsStore: UserSettingsStore,
    private val budgetCalculationService: BudgetCalculationService,
    private val hybridLogicalClockService: HybridLogicalClockService
) {
    suspend operator fun invoke(now: LocalDate) {
        val settings = userSettingsStore.ensureIdentity()
        if (!settings.isOnboardingCompleted) {
            return
        }
        val existingPolicies = budgetPolicyDao.getAllForSnapshot()
        if (existingPolicies.isEmpty()) {
            monthlyHistoryDao.getAll()
                .map { it.toDomainModel() }
                .sortedBy { it.getCycleStart() }
                .forEach { history ->
                    budgetPolicyDao.insert(
                        newBudgetPolicy(
                            cycleStart = history.getCycleStart(),
                            cycleEndExclusive = history.getCycleEnd(),
                            budgetAmountCents = history.budgetAmountCents,
                            paydayDayOfMonth = settings.paydayDate,
                            installId = settings.installDeviceId,
                            nowEpochMs = history.endTimestamp,
                            hybridLogicalClockService = hybridLogicalClockService
                        ).toEntity()
                    )
                }
        }

        val currentCycleStart = settings.lastResetDateOrNull()
            ?: budgetCalculationService.getCycleStartDate(now, settings.paydayDate)
        if (budgetPolicyDao.findActivePolicyForCycle(currentCycleStart.toString()) == null) {
            val cycleEnd = budgetCalculationService.getNextCycleStartDate(currentCycleStart, settings.paydayDate)
            budgetPolicyDao.insert(
                newBudgetPolicy(
                    cycleStart = currentCycleStart,
                    cycleEndExclusive = cycleEnd,
                    budgetAmountCents = settings.resolvedPortfolioMonthlyBudgetCents,
                    paydayDayOfMonth = settings.paydayDate,
                    installId = settings.installDeviceId,
                    nowEpochMs = now.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    hybridLogicalClockService = hybridLogicalClockService
                ).toEntity()
            )
        }
    }
}
