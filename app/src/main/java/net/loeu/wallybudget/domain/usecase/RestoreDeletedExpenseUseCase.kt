package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.data.time.WallyTime
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.service.HybridLogicalClockService

class RestoreDeletedExpenseUseCase(
    private val expenseDao: ExpenseDao,
    private val userSettingsStore: UserSettingsStore,
    private val hybridLogicalClockService: HybridLogicalClockService
) {
    suspend operator fun invoke(expense: Expense) {
        val settings = userSettingsStore.ensureIdentity()
        val installId = settings.installDeviceId
        val now = WallyTime.currentEpochTimeMs()
        expenseDao.update(
            expense.copy(
                lastModifiedByInstallId = installId,
                updatedAtEpochMs = now,
                deletedAtEpochMs = null,
                modClock = hybridLogicalClockService.next(expense.modClock, now, installId)
            ).toEntity()
        )
    }
}
