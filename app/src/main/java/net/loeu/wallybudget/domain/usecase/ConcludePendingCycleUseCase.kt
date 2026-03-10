package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.dao.MonthlyHistoryDao
import net.loeu.wallybudget.data.local.db.TransactionRunner
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.domain.model.MonthlyHistory
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.usecase.internal.pendingCycleRangeOrNull
import net.loeu.wallybudget.domain.usecase.internal.toStartOfDayMillis
import java.time.LocalDate

class ConcludePendingCycleUseCase(
    private val transactionRunner: TransactionRunner,
    private val expenseDao: ExpenseDao,
    private val monthlyHistoryDao: MonthlyHistoryDao,
    private val userSettingsStore: UserSettingsStore,
    private val budgetCalculationService: BudgetCalculationService
) {
    suspend operator fun invoke(settings: UserSettings) {
        val pendingCycle = settings.pendingCycleRangeOrNull() ?: return
        transactionRunner.inTransaction {
            archiveCycleIfNeeded(settings, pendingCycle.start, pendingCycle.endExclusive)
        }
        userSettingsStore.clearPendingCycle()
    }

    private suspend fun archiveCycleIfNeeded(
        settings: UserSettings,
        cycleStart: LocalDate,
        cycleEnd: LocalDate
    ) {
        val totalSpentCents = expenseDao.totalSpentInRange(
            cycleStart.toString(),
            cycleEnd.toString()
        ) ?: 0L
        val expenseCount = expenseDao.countInRange(
            cycleStart.toString(),
            cycleEnd.toString()
        )
        if (expenseCount == 0) {
            return
        }

        monthlyHistoryDao.insert(
            MonthlyHistory(
                cycleStartDate = cycleStart.toString(),
                budgetAmountCents = settings.monthlyBudgetCents,
                totalSpentCents = totalSpentCents,
                surplusCents = budgetCalculationService.calculateSurplus(
                    settings.monthlyBudgetCents,
                    totalSpentCents
                ),
                cycleEndDate = cycleEnd.toString(),
                endTimestamp = cycleEnd.toStartOfDayMillis()
            ).toEntity()
        )
    }
}
