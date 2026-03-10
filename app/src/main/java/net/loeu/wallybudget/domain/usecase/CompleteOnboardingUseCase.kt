package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.dao.MonthlyHistoryDao
import net.loeu.wallybudget.data.local.db.BudgetDatabase
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.data.local.preferences.UserPreferencesManager
import net.loeu.wallybudget.data.time.CurrentDateProvider
import net.loeu.wallybudget.domain.model.MonthlyHistory
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.usecase.internal.toStartOfDayMillis
import java.time.LocalDate

class CompleteOnboardingUseCase(
    private val database: BudgetDatabase,
    private val monthlyHistoryDao: MonthlyHistoryDao,
    private val userPreferencesManager: UserPreferencesManager,
    private val currentDateProvider: CurrentDateProvider,
    private val budgetCalculationService: BudgetCalculationService
) {
    suspend operator fun invoke(
        monthlyBudgetCents: Long,
        paydayDate: Int,
        cycleStartDate: LocalDate,
        previousExpensesCents: Long
    ) {
        if (previousExpensesCents > 0L) {
            val previousCycleStart = budgetCalculationService.getCycleStartDate(
                cycleStartDate.minusDays(1),
                paydayDate
            )
            database.inTransaction {
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

        userPreferencesManager.updateMonthlyBudget(monthlyBudgetCents)
        userPreferencesManager.updatePaydayDate(paydayDate)
        userPreferencesManager.updateLastResetTimestamp(cycleStartDate.toStartOfDayMillis())
        userPreferencesManager.updateLastSeenDate(currentDateProvider.currentDate())
        userPreferencesManager.completeOnboarding()
    }
}
