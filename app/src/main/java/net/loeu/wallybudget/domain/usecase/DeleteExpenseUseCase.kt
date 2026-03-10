package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.domain.model.Expense

class DeleteExpenseUseCase(
    private val expenseDao: ExpenseDao
) {
    suspend operator fun invoke(expense: Expense) {
        expenseDao.delete(expense.toEntity())
    }
}
