package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.entity.toDomainModel
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.usecase.internal.buildTimelineLockState
import net.loeu.wallybudget.domain.usecase.internal.lastResetDateOrNull
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import java.time.LocalDate

class ResolveMutationEffectiveDateUseCase(
    private val userSettingsStore: UserSettingsStore,
    private val expenseDao: ExpenseDao,
    private val budgetPolicyDao: BudgetPolicyDao,
    private val budgetCalculationService: BudgetCalculationService,
    private val cycleScheduleResolver: CycleScheduleResolver
) {
    private val syncObservedDateUseCase = SyncObservedDateUseCase(userSettingsStore)

    suspend operator fun invoke(settings: UserSettings, observedDate: LocalDate): LocalDate? {
        val effectiveDate = syncObservedDateUseCase(settings, observedDate)
        val latestExpenseDate = expenseDao.findLatestExpenseDate()
            ?.let(LocalDate::parse)
        val policies = budgetPolicyDao.getAllForSnapshot()
            .filter { it.deletedAtEpochMs == null }
            .map { it.toDomainModel() }
        val currentPolicy = cycleScheduleResolver.resolvePolicyForDate(
            date = effectiveDate,
            settings = settings,
            policies = policies
        )
        val timelineLockState = buildTimelineLockState(
            effectiveCurrentDate = effectiveDate,
            currentCycleStart = currentPolicy.cycleStart,
            lastResetDate = settings.lastResetDateOrNull(),
            latestExpenseDate = latestExpenseDate,
        )
        return effectiveDate.takeUnless { timelineLockState.isLocked }
    }
}
