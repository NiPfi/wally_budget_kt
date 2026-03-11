package net.loeu.wallybudget.ui.screens.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.ExpenseCategory
import net.loeu.wallybudget.domain.model.ExpenseCycleSection
import net.loeu.wallybudget.domain.model.recordedDate
import net.loeu.wallybudget.ui.components.TimelineLockBanner
import net.loeu.wallybudget.ui.screens.home.AddExpenseSheet
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryScreenScaffold(
    historySections: List<ExpenseCycleSection>,
    compactPagerSections: List<ExpenseCycleSection>,
    pagerCurrentPage: Int,
    pagerState: androidx.compose.foundation.pager.PagerState,
    modifier: Modifier,
    embedded: Boolean,
    isCompact: Boolean,
    showInitialSwipeHint: Boolean,
    snackbarHostState: SnackbarHostState,
    timelineLockReason: String?,
    onNavigateToSettings: (() -> Unit)?,
    onEditExpense: (Expense) -> Unit,
    onAddExpenseForDate: (LocalDate) -> Unit
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = if (embedded) WindowInsets() else ScaffoldDefaults.contentWindowInsets,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!embedded && timelineLockReason != null) {
                TimelineLockBanner(
                    reason = timelineLockReason,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            when {
                historySections.isEmpty() -> EmptyHistoryState(
                    modifier = Modifier.weight(1f)
                )
                !embedded && isCompact -> CompactHistoryContent(
                    compactPagerSections = compactPagerSections,
                    pagerCurrentPage = pagerCurrentPage,
                    pagerState = pagerState,
                    showInitialSwipeHint = showInitialSwipeHint,
                    onNavigateToSettings = onNavigateToSettings,
                    onEditExpense = onEditExpense,
                    onAddExpenseForDate = onAddExpenseForDate,
                    modifier = Modifier.weight(1f)
                )
                else -> FullHistoryContent(
                    historySections = historySections,
                    embedded = embedded,
                    onEditExpense = onEditExpense,
                    onAddExpenseForDate = onAddExpenseForDate,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun EmptyHistoryState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No cycle data yet.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactHistoryContent(
    compactPagerSections: List<ExpenseCycleSection>,
    pagerCurrentPage: Int,
    pagerState: androidx.compose.foundation.pager.PagerState,
    showInitialSwipeHint: Boolean,
    onNavigateToSettings: (() -> Unit)?,
    onEditExpense: (Expense) -> Unit,
    onAddExpenseForDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CompactHistoryHeader(
            pageCount = compactPagerSections.size,
            currentPage = pagerCurrentPage,
            onNavigateToSettings = onNavigateToSettings
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                CycleLedgerPage(
                    section = compactPagerSections[page],
                    onEditExpense = onEditExpense,
                    onAddExpenseForDate = onAddExpenseForDate,
                    contentPadding = PaddingValues(bottom = 24.dp)
                )
            }

            CyclePagerHint(
                currentPage = pagerCurrentPage,
                pageCount = compactPagerSections.size,
                showSwipeHint = showInitialSwipeHint,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(y = (-24).dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FullHistoryContent(
    historySections: List<ExpenseCycleSection>,
    embedded: Boolean,
    onEditExpense: (Expense) -> Unit,
    onAddExpenseForDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val sectionsToShow = if (embedded) historySections.take(1) else historySections
    Box(
        modifier = modifier
            .fillMaxWidth()
    ) {
        LazyColumn(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = if (embedded) 560.dp else 760.dp)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = if (embedded) 8.dp else 14.dp,
                bottom = 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            sectionsToShow.forEach { section ->
                if (!embedded) {
                    stickyHeader(key = "cycle-${section.title}") {
                        CycleHeader(section = section)
                    }
                }
                items(
                    items = section.daySections,
                    key = { daySection -> "${section.title}-${daySection.date.toEpochDay()}" }
                ) { daySection ->
                    LedgerDaySection(
                        daySection = daySection,
                        onEditExpense = onEditExpense,
                        onAddExpenseForDate = onAddExpenseForDate
                    )
                }
            }
        }
    }
}

@Composable
internal fun HistoryAddExpenseSheet(
    isAddSheetVisible: Boolean,
    interactionsEnabled: Boolean,
    selectedDateEpochDay: Long,
    onDismiss: () -> Unit,
    onAddExpense: (Long, String, ExpenseCategory?, LocalDate) -> Unit
) {
    if (!isAddSheetVisible || !interactionsEnabled) return

    val selectedDate = LocalDate.ofEpochDay(selectedDateEpochDay)
    AddExpenseSheet(
        onDismiss = onDismiss,
        onSubmitExpense = { amountCents, description, icon ->
            onAddExpense(amountCents, description, icon, selectedDate)
            onDismiss()
        },
        title = "Add expense for ${selectedDate.format(DateTimeFormatter.ofPattern("MMM d"))}",
        confirmButtonText = "Save expense",
        dateLabel = "Recorded for ${selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))}"
    )
}

@Composable
internal fun HistoryEditExpenseSheet(
    editingExpense: Expense?,
    interactionsEnabled: Boolean,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    onDismiss: () -> Unit,
    onUpdateExpense: (Expense) -> Unit,
    onDeleteExpense: (Expense) -> Unit,
    onRestoreExpense: (Expense) -> Unit
) {
    val editableExpense = editingExpense?.takeIf { interactionsEnabled } ?: return

    AddExpenseSheet(
        onDismiss = onDismiss,
        onSubmitExpense = { amountCents, description, icon ->
            onUpdateExpense(
                editableExpense.copy(
                    amountCents = amountCents,
                    description = description,
                    icon = icon
                )
            )
            onDismiss()
        },
        onDeleteExpense = {
            onDeleteExpense(editableExpense)
            scope.launch {
                val dismissJob = launch {
                    delay(5000)
                    snackbarHostState.currentSnackbarData?.dismiss()
                }
                val result = snackbarHostState.showSnackbar(
                    message = deletedHistoryExpenseMessage(editableExpense),
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    dismissJob.cancel()
                    onRestoreExpense(editableExpense)
                }
            }
            onDismiss()
        },
        title = "Edit expense",
        confirmButtonText = "Save changes",
        dateLabel = "Recorded for ${
            editableExpense.recordedDate().format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
        }",
        initialAmountCents = editableExpense.amountCents,
        initialDescription = editableExpense.description,
        initialIcon = editableExpense.icon
    )
}

private fun deletedHistoryExpenseMessage(expense: Expense): String {
    return if (expense.description.isNotBlank()) {
        "Deleted \"${expense.description}\""
    } else {
        "Deleted expense"
    }
}
