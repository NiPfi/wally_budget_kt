package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.dao.BudgetBucketDao
import net.loeu.wallybudget.data.local.entity.BudgetBucketEntity
import net.loeu.wallybudget.data.local.entity.ExpenseEntity
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.data.time.CurrentDateProvider
import net.loeu.wallybudget.data.time.WallyTime
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import java.time.format.DateTimeParseException
import java.time.LocalDate

class ExpenseEditNotAllowedException(
    message: String
) : IllegalStateException(message)

class UpdateExpenseUseCase(
    private val expenseDao: ExpenseDao,
    private val budgetBucketDao: BudgetBucketDao,
    private val userSettingsStore: UserSettingsStore,
    private val currentDateProvider: CurrentDateProvider,
    private val hybridLogicalClockService: HybridLogicalClockService
) {
    suspend operator fun invoke(expense: Expense) {
        val persistedExpense = expenseDao.findByRecordUuid(expense.recordUuid)
        val targetBucket = budgetBucketDao.findByBucketUuid(expense.bucketUuid)
        val currentDate = currentDateProvider.currentDate()
        val validationMessage = validateExpenseEdit(
            expense = expense,
            persistedExpense = persistedExpense,
            targetBucket = targetBucket,
            currentDate = currentDate
        )
        if (validationMessage != null) {
            throw ExpenseEditNotAllowedException(validationMessage)
        }
        val persistedExpenseEntity = persistedExpense ?: return
        val settings = userSettingsStore.ensureIdentity()
        val installId = settings.installDeviceId
        val now = WallyTime.currentEpochTimeMs()
        expenseDao.update(
            expense.copy(
                expenseDate = persistedExpenseEntity.expenseDate,
                lastModifiedByInstallId = installId,
                updatedAtEpochMs = now,
                modClock = hybridLogicalClockService.next(expense.modClock, now, installId)
            ).toEntity()
        )
    }

    private fun validateExpenseEdit(
        expense: Expense,
        persistedExpense: ExpenseEntity?,
        targetBucket: BudgetBucketEntity?,
        currentDate: LocalDate
    ): String? {
        val persistedExpenseDate = persistedExpense?.let { parseExpenseDateOrNull(it.expenseDate) }
        val requestedExpenseDate = parseExpenseDateOrNull(expense.expenseDate)

        return when {
            persistedExpense == null -> "Expense not found."
            targetBucket == null -> "Bucket not found."
            persistedExpenseDate == null || requestedExpenseDate == null -> "Invalid expense date."
            persistedExpenseDate.isAfter(currentDate) || requestedExpenseDate.isAfter(currentDate) -> {
                "Future-dated expenses cannot be edited."
            }
            !targetBucket.monthScoped && persistedExpenseDate != currentDate -> {
                "Only current-day expenses can be edited."
            }
            else -> null
        }
    }

    private fun parseExpenseDateOrNull(expenseDate: String): LocalDate? {
        return try {
            LocalDate.parse(expenseDate)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
