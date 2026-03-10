package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.domain.model.Expense

class UpdateExpenseUseCase(
    private val expenseDao: ExpenseDao
) {
    suspend operator fun invoke(expense: Expense) {
        expenseDao.update(expense.toEntity())
    }
}
