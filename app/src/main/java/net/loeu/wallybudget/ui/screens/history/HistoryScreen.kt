package net.loeu.wallybudget.ui.screens.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.loeu.wallybudget.R
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.ExpenseCategory
import net.loeu.wallybudget.domain.model.ExpenseCycleSection
import net.loeu.wallybudget.domain.model.recordedDate
import net.loeu.wallybudget.domain.model.ExpenseDaySection
import net.loeu.wallybudget.ui.components.TimelineLockBanner
import net.loeu.wallybudget.ui.screens.expenses.ExpenseItem
import net.loeu.wallybudget.ui.screens.home.AddExpenseSheet
import net.loeu.wallybudget.util.CurrencyFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3AdaptiveApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun HistoryScreen(
    historySections: List<ExpenseCycleSection>,
    onAddExpense: (Long, String, ExpenseCategory?, LocalDate) -> Unit,
    onRestoreExpense: (Expense) -> Unit,
    onUpdateExpense: (Expense) -> Unit,
    onDeleteExpense: (Expense) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToSettings: (() -> Unit)? = null,
    embedded: Boolean = false,
    interactionsEnabled: Boolean = true,
    timelineLockReason: String? = null
) {
    val isCompact = isCompactHistoryLayout()
    val compactPagerSections = compactPagerSections(historySections, embedded)
    val pagerState = rememberPagerState(
        initialPage = compactPagerSections.lastIndex.coerceAtLeast(0)
    ) {
        compactPagerSections.size.coerceAtLeast(1)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val selectedDateEpochDay = rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var showInitialSwipeHint by rememberSaveable { mutableStateOf(true) }
    var isAddSheetVisible by remember { mutableStateOf(false) }
    var expenseBeingEdited by remember { mutableStateOf<Expense?>(null) }
    HistoryScreenEffects(
        interactionsEnabled = interactionsEnabled,
        currentPage = pagerState.currentPage,
        pageCount = compactPagerSections.size,
        onResetEditing = {
            expenseBeingEdited = null
            isAddSheetVisible = false
        },
        onDismissInitialSwipeHint = { showInitialSwipeHint = false }
    )

    HistoryScreenScaffold(
        historySections = historySections,
        compactPagerSections = compactPagerSections,
        pagerCurrentPage = pagerState.currentPage,
        pagerState = pagerState,
        modifier = modifier,
        embedded = embedded,
        isCompact = isCompact,
        showInitialSwipeHint = showInitialSwipeHint,
        snackbarHostState = snackbarHostState,
        timelineLockReason = timelineLockReason,
        onNavigateToSettings = onNavigateToSettings,
        onEditExpense = { expenseBeingEdited = it },
        onAddExpenseForDate = { date ->
            selectedDateEpochDay.longValue = date.toEpochDay()
            isAddSheetVisible = true
        }
    )

    HistoryAddExpenseSheet(
        isAddSheetVisible = isAddSheetVisible,
        interactionsEnabled = interactionsEnabled,
        selectedDateEpochDay = selectedDateEpochDay.longValue,
        onDismiss = { isAddSheetVisible = false },
        onAddExpense = onAddExpense
    )

    HistoryEditExpenseSheet(
        editingExpense = expenseBeingEdited,
        interactionsEnabled = interactionsEnabled,
        snackbarHostState = snackbarHostState,
        scope = scope,
        onDismiss = { expenseBeingEdited = null },
        onUpdateExpense = onUpdateExpense,
        onDeleteExpense = onDeleteExpense,
        onRestoreExpense = onRestoreExpense
    )
}

@Composable
private fun isCompactHistoryLayout(): Boolean {
    return !currentWindowAdaptiveInfo().windowSizeClass
        .isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)
}

private fun compactPagerSections(
    historySections: List<ExpenseCycleSection>,
    embedded: Boolean
): List<ExpenseCycleSection> {
    return if (embedded) historySections.take(1) else historySections.reversed()
}

@Composable
private fun HistoryScreenEffects(
    interactionsEnabled: Boolean,
    currentPage: Int,
    pageCount: Int,
    onResetEditing: () -> Unit,
    onDismissInitialSwipeHint: () -> Unit
) {
    LaunchedEffect(interactionsEnabled) {
        if (!interactionsEnabled) onResetEditing()
    }
    LaunchedEffect(currentPage, pageCount) {
        val currentCyclePage = pageCount - 1
        if (pageCount > 1 && currentPage != currentCyclePage) onDismissInitialSwipeHint()
    }
}


@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CycleLedgerScreen(
    section: ExpenseCycleSection,
    title: String,
    onEditExpense: (Expense) -> Unit,
    onAddExpenseForDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = "Back"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            CycleLedgerPage(
                section = section,
                onEditExpense = onEditExpense,
                onAddExpenseForDate = onAddExpenseForDate,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 760.dp)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 14.dp,
                    bottom = 28.dp
                )
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CycleLedgerPage(
    section: ExpenseCycleSection,
    onEditExpense: (Expense) -> Unit,
    onAddExpenseForDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 24.dp)
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .then(modifier),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        stickyHeader {
            CycleHeader(section = section)
        }
        items(section.daySections, key = { it.date.toEpochDay() }) { daySection ->
            LedgerDaySection(
                daySection = daySection,
                onEditExpense = onEditExpense,
                onAddExpenseForDate = onAddExpenseForDate
            )
        }
    }
}

@Composable
internal fun CompactHistoryHeader(
    pageCount: Int,
    currentPage: Int,
    onNavigateToSettings: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (pageCount > 1) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CyclePagerDots(
                    pageCount = pageCount,
                    currentPage = currentPage
                )
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        if (onNavigateToSettings != null) {
            IconButton(onClick = onNavigateToSettings) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = "Settings"
                )
            }
        }
    }
}

@Composable
internal fun CyclePagerHint(
    currentPage: Int,
    pageCount: Int,
    showSwipeHint: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = showSwipeHint && currentPage == pageCount - 1,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.padding(start = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Swipe for earlier cycles",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CyclePagerDots(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    if (pageCount <= 1) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(pageCount) { page ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (page == currentPage) 8.dp else 6.dp)
                    .background(
                        color = if (page == currentPage) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
internal fun CycleHeader(
    section: ExpenseCycleSection,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.padding(bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (section.isCompletedCycle) {
                    if (section.surplusCents >= 0L) {
                        "Finished ${CurrencyFormatter.format(abs(section.surplusCents))} under budget"
                    } else {
                        "Finished ${CurrencyFormatter.format(abs(section.surplusCents))} over budget"
                    }
                } else {
                    if (section.surplusCents >= 0L) {
                        "Net ${CurrencyFormatter.format(abs(section.surplusCents))} available"
                    } else {
                        "Net ${CurrencyFormatter.format(abs(section.surplusCents))} over budget"
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
internal fun LedgerDaySection(
    daySection: ExpenseDaySection,
    onEditExpense: (Expense) -> Unit,
    onAddExpenseForDate: (LocalDate) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (daySection.isToday) {
                        "Today"
                    } else {
                        daySection.date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = CurrencyFormatter.format(daySection.totalSpentCents),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (daySection.isEditable && !daySection.isToday) {
                IconButton(
                    onClick = { onAddExpenseForDate(daySection.date) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = "Add expense for ${daySection.date}"
                    )
                }
            }
        }

        if (daySection.expenses.isEmpty()) {
            Text(
                text = "No expenses recorded for this day.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        } else {
            daySection.expenses.forEach { expense ->
                ExpenseItem(
                    expense = expense,
                    onEdit = if (daySection.isEditable) {
                        { onEditExpense(expense) }
                    } else {
                        null
                    }
                )
            }
        }
    }
}
