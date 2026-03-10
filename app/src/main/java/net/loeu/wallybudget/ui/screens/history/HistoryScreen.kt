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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.loeu.wallybudget.data.local.entity.Expense
import net.loeu.wallybudget.domain.model.ExpenseCategory
import net.loeu.wallybudget.ui.model.ExpenseCycleSection
import net.loeu.wallybudget.data.local.entity.recordedDate
import net.loeu.wallybudget.ui.model.ExpenseDaySection
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
    val isCompact = !currentWindowAdaptiveInfo().windowSizeClass
        .isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)
    val compactPagerSections = if (embedded) {
        historySections.take(1)
    } else {
        historySections.reversed()
    }
    val pagerState = rememberPagerState(
        initialPage = compactPagerSections.lastIndex.coerceAtLeast(0)
    ) {
        compactPagerSections.size.coerceAtLeast(1)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val selectedDateEpochDay = rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var showInitialSwipeHint by rememberSaveable { mutableStateOf(true) }
    var expenseBeingEdited by remember { mutableStateOf<Expense?>(null) }
    var isAddSheetVisible by remember { mutableStateOf(false) }

    LaunchedEffect(interactionsEnabled) {
        if (!interactionsEnabled) {
            expenseBeingEdited = null
            isAddSheetVisible = false
        }
    }

    LaunchedEffect(pagerState.currentPage, compactPagerSections.size) {
        val currentCyclePage = compactPagerSections.lastIndex
        if (compactPagerSections.size > 1 && pagerState.currentPage != currentCyclePage) {
            showInitialSwipeHint = false
        }
    }

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

            if (historySections.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
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
            } else if (!embedded && isCompact) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CompactHistoryHeader(
                        pageCount = compactPagerSections.size,
                        currentPage = pagerState.currentPage,
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
                                onEditExpense = { expenseBeingEdited = it },
                                onAddExpenseForDate = { date ->
                                    selectedDateEpochDay.longValue = date.toEpochDay()
                                    isAddSheetVisible = true
                                },
                                contentPadding = PaddingValues(
                                    bottom = 24.dp
                                )
                            )
                        }

                        CyclePagerHint(
                            currentPage = pagerState.currentPage,
                            pageCount = compactPagerSections.size,
                            showSwipeHint = showInitialSwipeHint,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(y = (-24).dp)
                        )
                    }
                }
            } else {
                val sectionsToShow = if (embedded) historySections.take(1) else historySections
                Box(
                    modifier = Modifier
                        .weight(1f)
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
                                    onEditExpense = { expenseBeingEdited = it },
                                    onAddExpenseForDate = { date ->
                                        selectedDateEpochDay.longValue = date.toEpochDay()
                                        isAddSheetVisible = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (isAddSheetVisible && interactionsEnabled) {
        val selectedDate = LocalDate.ofEpochDay(selectedDateEpochDay.longValue)
        AddExpenseSheet(
            onDismiss = { isAddSheetVisible = false },
            onSubmitExpense = { amountCents, description, icon ->
                onAddExpense(amountCents, description, icon, selectedDate)
                isAddSheetVisible = false
            },
            title = "Add expense for ${selectedDate.format(DateTimeFormatter.ofPattern("MMM d"))}",
            confirmButtonText = "Save expense",
            dateLabel = "Recorded for ${selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))}"
        )
    }

    expenseBeingEdited?.takeIf { interactionsEnabled }?.let { editingExpense ->
        AddExpenseSheet(
            onDismiss = { expenseBeingEdited = null },
            onSubmitExpense = { amountCents, description, icon ->
                onUpdateExpense(
                    editingExpense.copy(
                        amountCents = amountCents,
                        description = description,
                        icon = icon
                    )
                )
                expenseBeingEdited = null
            },
            onDeleteExpense = {
                onDeleteExpense(editingExpense)
                scope.launch {
                    val dismissJob = launch {
                        delay(5000)
                        snackbarHostState.currentSnackbarData?.dismiss()
                    }
                    val result = snackbarHostState.showSnackbar(
                        message = if (editingExpense.description.isNotBlank()) {
                            "Deleted \"${editingExpense.description}\""
                        } else {
                            "Deleted expense"
                        },
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        dismissJob.cancel()
                        onRestoreExpense(editingExpense)
                    }
                }
                expenseBeingEdited = null
            },
            title = "Edit expense",
            confirmButtonText = "Save changes",
            dateLabel = "Recorded for ${editingExpense.recordedDate().format(DateTimeFormatter.ofPattern("EEEE, MMM d"))}",
            initialAmountCents = editingExpense.amountCents,
            initialDescription = editingExpense.description,
            initialIcon = editingExpense.icon
        )
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
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
private fun CycleLedgerPage(
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
private fun CompactHistoryHeader(
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
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings"
                )
            }
        }
    }
}

@Composable
private fun CyclePagerHint(
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
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
private fun CycleHeader(
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
private fun LedgerDaySection(
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
                        imageVector = Icons.Default.Add,
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
