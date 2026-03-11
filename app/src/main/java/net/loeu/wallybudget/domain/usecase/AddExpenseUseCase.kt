package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import java.util.UUID

class AddExpenseUseCase(
    private val expenseDao: ExpenseDao,
    private val userSettingsStore: UserSettingsStore,
    private val hybridLogicalClockService: HybridLogicalClockService
) {
    suspend operator fun invoke(expense: Expense): Long {
        val settings = userSettingsStore.ensureIdentity()
        val installId = settings.installDeviceId
        val now = expense.updatedAtEpochMs.takeIf { it > 0L } ?: System.currentTimeMillis()
        val existingClock = expense.modClock.takeIf { it.isNotBlank() }
        return expenseDao.insert(
            expense.copy(
                recordUuid = expense.recordUuid.ifBlank { UUID.randomUUID().toString() },
                originInstallId = expense.originInstallId.ifBlank { installId },
                lastModifiedByInstallId = expense.lastModifiedByInstallId.ifBlank { installId },
                createdAtEpochMs = expense.createdAtEpochMs.takeIf { it > 0L } ?: now,
                updatedAtEpochMs = now,
                deletedAtEpochMs = expense.deletedAtEpochMs,
                modClock = hybridLogicalClockService.next(existingClock, now, installId)
            ).toEntity()
        )
    }
}
