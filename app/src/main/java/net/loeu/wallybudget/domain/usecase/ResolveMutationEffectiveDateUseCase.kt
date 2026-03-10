package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.usecase.internal.buildTimelineLockState
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import java.time.LocalDate

class ResolveMutationEffectiveDateUseCase(
    private val userSettingsStore: UserSettingsStore,
    private val expenseDao: ExpenseDao,
    private val budgetCalculationService: BudgetCalculationService
) {
    private val syncObservedDateUseCase = SyncObservedDateUseCase(userSettingsStore)

    suspend operator fun invoke(settings: UserSettings, observedDate: LocalDate): LocalDate? {
        val effectiveDate = syncObservedDateUseCase(settings, observedDate)
        val latestExpenseDate = expenseDao.findLatestExpenseDate()
            ?.let(LocalDate::parse)
        val timelineLockState = buildTimelineLockState(
            settings = settings,
            effectiveCurrentDate = effectiveDate,
            latestExpenseDate = latestExpenseDate,
            budgetCalculationService = budgetCalculationService
        )
        return effectiveDate.takeUnless { timelineLockState.isLocked }
    }
}
