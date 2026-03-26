package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.data.time.CurrentDateProvider
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.service.HybridLogicalClockService

class ExpenseEditNotAllowedException(
    message: String
) : IllegalStateException(message)

class UpdateExpenseUseCase(
    private val expenseDao: ExpenseDao,
    private val userSettingsStore: UserSettingsStore,
    private val currentDateProvider: CurrentDateProvider,
    private val hybridLogicalClockService: HybridLogicalClockService
) {
    suspend operator fun invoke(expense: Expense) {
        val persistedExpense = expenseDao.findByRecordUuid(expense.recordUuid)
        if (persistedExpense?.expenseDate != currentDateProvider.currentDate().toString()) {
            throw ExpenseEditNotAllowedException("Only current-day expenses can be edited.")
        }
        val settings = userSettingsStore.ensureIdentity()
        val installId = settings.installDeviceId
        val now = System.currentTimeMillis()
        expenseDao.update(
            expense.copy(
                lastModifiedByInstallId = installId,
                updatedAtEpochMs = now,
                modClock = hybridLogicalClockService.next(expense.modClock, now, installId)
            ).toEntity()
        )
    }
}
