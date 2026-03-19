package net.loeu.wallybudget

import androidx.compose.runtime.Composable
import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.ExpenseCategory
import net.loeu.wallybudget.domain.model.recordedDate
import net.loeu.wallybudget.ui.screens.home.AddExpenseSheet
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun PendingCycleExpenseSheets(
    showAddExpenseSheet: Boolean,
    selectedDate: LocalDate,
    onHideAddExpenseSheet: () -> Unit,
    onAddExpense: (String, Long, String, ExpenseCategory?, LocalDate) -> Unit,
    expenseBeingEdited: Expense?,
    onDismissExpenseEditor: () -> Unit,
    onUpdateExpense: (Expense) -> Unit,
    onDeleteExpense: (Expense) -> Unit,
    allBuckets: List<BudgetBucket>,
    selectedBucketUuid: String?
) {
    if (showAddExpenseSheet) {
        AddExpenseSheet(
            onDismiss = onHideAddExpenseSheet,
            bucketOptions = allBuckets.filterNot { it.isClosed },
            initialBucketUuid = selectedBucketUuid,
            initialBucketName = allBuckets.firstOrNull { it.bucketUuid == selectedBucketUuid }?.name,
            onSubmitExpense = { bucketUuid, amountCents, description, icon ->
                onAddExpense(bucketUuid, amountCents, description, icon, selectedDate)
            },
            title = "Add expense for ${selectedDate.format(DateTimeFormatter.ofPattern("MMM d"))}",
            confirmButtonText = "Add to ${selectedDate.format(DateTimeFormatter.ofPattern("MMM d"))}",
            dateLabel = "Recorded for ${selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))}"
        )
    }

    expenseBeingEdited?.let { editingExpense ->
        val openBuckets = allBuckets.filterNot { it.isClosed }
        AddExpenseSheet(
            onDismiss = onDismissExpenseEditor,
            bucketOptions = openBuckets,
            initialBucketUuid = editingExpense.bucketUuid,
            initialBucketName = allBuckets.firstOrNull { it.bucketUuid == editingExpense.bucketUuid }?.name,
            onSubmitExpense = { bucketUuid, amountCents, description, icon ->
                onUpdateExpense(
                    editingExpense.copy(
                        bucketUuid = bucketUuid,
                        amountCents = amountCents,
                        description = description,
                        icon = icon
                    )
                )
                onDismissExpenseEditor()
            },
            onDeleteExpense = {
                onDeleteExpense(editingExpense)
                onDismissExpenseEditor()
            },
            title = "Edit cycle expense",
            confirmButtonText = "Save changes",
            dateLabel = buildString {
                append("Recorded for ")
                append(
                    editingExpense.recordedDate()
                        .format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
                )
            },
            initialAmountCents = editingExpense.amountCents,
            initialDescription = editingExpense.description,
            initialIcon = editingExpense.icon
        )
    }
}
