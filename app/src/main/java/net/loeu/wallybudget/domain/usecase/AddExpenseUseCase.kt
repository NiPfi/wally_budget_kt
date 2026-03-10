package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.domain.model.Expense

class AddExpenseUseCase(
    private val expenseDao: ExpenseDao
) {
    suspend operator fun invoke(expense: Expense): Long = expenseDao.insert(expense.toEntity())
}
