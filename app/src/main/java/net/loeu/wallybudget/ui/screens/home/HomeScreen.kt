@file:Suppress("CyclomaticComplexMethod", "LongMethod", "MaxLineLength", "TooManyFunctions")

package net.loeu.wallybudget.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.loeu.wallybudget.R
import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.BucketBalanceBehavior
import net.loeu.wallybudget.domain.model.BucketSummaryState
import net.loeu.wallybudget.domain.model.BucketTrackingMode
import net.loeu.wallybudget.domain.model.DEFAULT_FUND_UUID
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.ExpenseCategory
import net.loeu.wallybudget.domain.model.Fund
import net.loeu.wallybudget.domain.model.PortfolioState
import net.loeu.wallybudget.domain.model.SelectedBucketOverview
import net.loeu.wallybudget.domain.model.SpendingForecast
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.model.displayDescription
import net.loeu.wallybudget.domain.model.recordedDate
import net.loeu.wallybudget.domain.usecase.BucketDraft
import net.loeu.wallybudget.domain.usecase.internal.resolveSelectedOpenBucketUuid
import net.loeu.wallybudget.ui.components.PagerDots
import net.loeu.wallybudget.ui.components.TimelineLockBanner
import net.loeu.wallybudget.ui.screens.expenses.ExpenseItem
import net.loeu.wallybudget.ui.screens.overview.CollapsingSummaryLayout
import net.loeu.wallybudget.ui.screens.overview.CollapsingSummaryLayoutConfig
import net.loeu.wallybudget.ui.screens.overview.LoadingValuePlaceholder
import net.loeu.wallybudget.ui.screens.overview.LocalCollapsingHeaderIsForMeasurement
import net.loeu.wallybudget.ui.screens.overview.MergedSummaryHeaderSurface
import net.loeu.wallybudget.ui.screens.overview.OverviewPage
import net.loeu.wallybudget.ui.screens.overview.PlaceholderShimmerProvider
import net.loeu.wallybudget.ui.screens.overview.rememberOverviewPageLayoutState
import net.loeu.wallybudget.ui.screens.overview.summaryCardColors
import net.loeu.wallybudget.ui.screens.settings.SettingsSaveEffect
import net.loeu.wallybudget.util.CurrencyFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import androidx.compose.material3.rememberModalBottomSheetState
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    bucketSummaries: List<BucketSummaryState>,
    selectedBucketOverview: SelectedBucketOverview,
    allBuckets: List<BudgetBucket>,
    userSettings: UserSettings,
    currentDate: LocalDate,
    spendingForecast: SpendingForecast?,
    onSelectBucket: (String) -> Unit,
    onSavePortfolioPlan: (Long, List<BucketDraft>) -> Unit,
    onAddExpense: (String, Long, String, ExpenseCategory?, LocalDate) -> Unit,
    onRestoreExpense: (Expense) -> Unit,
    onUpdateExpense: (Expense) -> Unit,
    onDeleteExpense: (Expense) -> Unit,
    onNavigateToAnalysis: () -> Unit,
    showTopRightSettingsAction: Boolean,
    showAddExpenseSheet: Boolean,
    onShowAddExpenseSheet: () -> Unit,
    onHideAddExpenseSheet: () -> Unit,
    settingsMessage: String?,
    onSettingsMessageConsumed: () -> Unit,
    modifier: Modifier = Modifier,
    isLoadingData: Boolean = false,
    timelineLockReason: String? = null
) {
    var expenseBeingEdited by remember { mutableStateOf<Expense?>(null) }
    var bucketEditorState by remember { mutableStateOf<HomeBucketEditorState?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val canEditExpenses = !isLoadingData && timelineLockReason == null
    val openBuckets = allBuckets.filterNot { it.isClosed }
    val orderedOpenSummaries = remember(bucketSummaries) {
        bucketSummaries.sortedWith(
            compareBy<BucketSummaryState> { it.bucket.bucketUuid != DEFAULT_SPENDING_BUCKET_UUID }
                .thenBy { it.bucket.sortOrder }
                .thenBy { it.bucket.createdAtEpochMs }
        )
    }
    val initialBucketUuid = remember(orderedOpenSummaries, userSettings.selectedBucketUuid) {
        resolveSelectedOpenBucketUuid(
            selectedBucketUuid = userSettings.selectedBucketUuid,
            openBuckets = orderedOpenSummaries.map { it.bucket }
        )
    }
    val pages = remember(orderedOpenSummaries) { orderedOpenSummaries.map { it.bucket.bucketUuid } }
    val defaultPageIndex = remember(pages, initialBucketUuid) {
        pages.indexOf(initialBucketUuid).takeIf { it >= 0 } ?: 0
    }
    val pagerState = rememberPagerState(initialPage = defaultPageIndex) { pages.size }
    var didInitializePage by rememberSaveable { mutableStateOf(false) }

    HomeScreenEffects(
        canEditExpenses = canEditExpenses,
        onDisableEditing = {
            expenseBeingEdited = null
            onHideAddExpenseSheet()
        }
    )
    SettingsSaveEffect(
        settingsMessage = settingsMessage,
        snackbarHostState = snackbarHostState,
        onSettingsMessageConsumed = onSettingsMessageConsumed
    )

    LaunchedEffect(defaultPageIndex, initialBucketUuid, didInitializePage, pages.size) {
        if (!didInitializePage && pages.isNotEmpty()) {
            pagerState.scrollToPage(defaultPageIndex)
            initialBucketUuid?.let(onSelectBucket)
            didInitializePage = true
        }
    }

    fun openBucketSettings(bucketUuid: String) {
        val bucket = allBuckets.firstOrNull { it.bucketUuid == bucketUuid } ?: return
        val summary = bucketSummaries.firstOrNull { it.bucket.bucketUuid == bucketUuid }
        bucketEditorState = HomeBucketEditorState(
            bucketUuid = bucket.bucketUuid,
            name = bucket.name,
            trackingMode = bucket.trackingMode,
            balanceBehavior = bucket.balanceBehavior,
            amountText = CurrencyFormatter.centsToDecimalString(
                summary?.allocatedThisCycleCents ?: bucket.defaultAllocatedAmountCents
            ),
            isSystemDefault = bucket.bucketUuid == DEFAULT_SPENDING_BUCKET_UUID
        )
    }

    LaunchedEffect(pagerState, pages) {
        snapshotFlow { pagerState.settledPage }
            .map { page -> pages.getOrNull(page) }
            .filter { it != null }
            .map { requireNotNull(it) }
            .distinctUntilChanged()
            .collect { onSelectBucket(it) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (!canEditExpenses) return@FloatingActionButton
                    onShowAddExpenseSheet()
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = "Add expense"
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                timelineLockReason?.let { reason ->
                    TimelineLockBanner(
                        reason = reason,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { pageIndex ->
                    val pageBucketUuid = pages[pageIndex]
                    BucketHomePage(
                        selectedBucketOverview = selectedBucketOverview,
                        spendingForecast = spendingForecast,
                        bucketUuid = pageBucketUuid,
                        pageTitle = orderedOpenSummaries.firstOrNull { it.bucket.bucketUuid == pageBucketUuid }?.bucket?.name
                            ?: "Bucket",
                        pageSummary = orderedOpenSummaries.firstOrNull { it.bucket.bucketUuid == pageBucketUuid },
                        canEditExpenses = canEditExpenses,
                        isLoadingData = isLoadingData,
                        onEditExpense = { expenseBeingEdited = it },
                        onNavigateToAnalysis = if (
                            orderedOpenSummaries.firstOrNull { it.bucket.bucketUuid == pageBucketUuid }
                                ?.bucket
                                ?.trackingMode == BucketTrackingMode.DAILY_TARGET
                        ) {
                            onNavigateToAnalysis
                        } else {
                            null
                        },
                        showTopRightSettingsAction = showTopRightSettingsAction,
                        onNavigateToSettings = if (showTopRightSettingsAction) {
                            { openBucketSettings(pageBucketUuid) }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            BottomPageIndicator(
                pageCount = pages.size,
                currentPage = pagerState.currentPage,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    AddExpenseSheetDialog(
        showAddExpenseSheet = showAddExpenseSheet,
        canEditExpenses = canEditExpenses,
        selectedDate = currentDate,
        bucketOptions = openBuckets,
        selectedBucketOverview = selectedBucketOverview,
        onDismiss = onHideAddExpenseSheet,
        onAddExpense = onAddExpense
    )
    HomeBucketSettingsSheet(
        state = bucketEditorState,
        allBuckets = allBuckets,
        bucketSummaries = bucketSummaries,
        portfolioBudgetCents = userSettings.resolvedPortfolioMonthlyBudgetCents,
        onDismiss = { bucketEditorState = null },
        onSaveSettings = { updatedBucketDraft ->
            onSavePortfolioPlan(
                userSettings.resolvedPortfolioMonthlyBudgetCents,
                buildUpdatedHomeBucketDrafts(
                    allBuckets = allBuckets,
                    bucketSummaries = bucketSummaries,
                    updatedBucketDraft = updatedBucketDraft
                )
            )
            bucketEditorState = null
        }
    )
    EditExpenseSheetDialog(
        editingExpense = expenseBeingEdited,
        canEditExpenses = canEditExpenses,
        allBuckets = allBuckets,
        snackbarHostState = snackbarHostState,
        onDismiss = { expenseBeingEdited = null },
        onUpdateExpense = onUpdateExpense,
        onDeleteExpense = onDeleteExpense,
        onRestoreExpense = onRestoreExpense,
        scope = scope
    )
}

@Composable
private fun BottomPageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 10.dp)
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                shape = MaterialTheme.shapes.extraLarge
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        PagerDots(
            pageCount = pageCount,
            currentPage = currentPage
        )
    }
}

@Composable
private fun HomeScreenEffects(
    canEditExpenses: Boolean,
    onDisableEditing: () -> Unit
) {
    LaunchedEffect(canEditExpenses) {
        if (!canEditExpenses) onDisableEditing()
    }
}

@Composable
internal fun PortfolioOverviewPage(
    portfolioState: PortfolioState,
    bucketSummaries: List<BucketSummaryState>,
    funds: List<Fund>,
    showTopRightSettingsAction: Boolean,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val layoutState = rememberOverviewPageLayoutState(
        defaultCollapsedHeader = false,
        enableHeaderCollapse = true
    )

    CollapsingSummaryLayout(
        layoutState = layoutState,
        config = CollapsingSummaryLayoutConfig(
            modifier = modifier.fillMaxSize(),
            enableHeaderCollapse = true,
            bottomContentPadding = HomeFabSize + HomeFabListClearance + 16.dp,
            headerHorizontalPadding = 0.dp,
            headerTopPadding = 0.dp,
            headerBottomSpacing = 16.dp
        ),
        header = { collapseProgress ->
            PortfolioSummaryCard(
                portfolioState = portfolioState,
                collapseProgress = collapseProgress,
                onNavigateToSettings = if (showTopRightSettingsAction) onNavigateToSettings else null
            )
        }
    ) { listState, contentPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ActiveBucketsSection(bucketSummaries = bucketSummaries)
            }
            item {
                FundsSection(funds = funds)
            }
        }
    }
}

@Composable
private fun PortfolioSummaryCard(
    portfolioState: PortfolioState,
    collapseProgress: Float,
    onNavigateToSettings: (() -> Unit)?
) {
    val cycleDateFormatter = remember { DateTimeFormatter.ofPattern("MMM d") }
    val cycleLabel = remember(portfolioState.cycleStartDate, portfolioState.cycleEndDateExclusive) {
        val cycleEndInclusive = portfolioState.cycleEndDateExclusive.minusDays(1)
        "${portfolioState.cycleStartDate.format(cycleDateFormatter)} - " +
            cycleEndInclusive.format(cycleDateFormatter)
    }

    TopSummaryCard(
        title = "Portfolio",
        amountText = CurrencyFormatter.formatSigned(portfolioState.remainingThisCycleCents),
        subtitleText = "Portfolio remaining this cycle • $cycleLabel",
        collapseProgress = collapseProgress,
        useWarningTint = portfolioState.remainingThisCycleCents < 0L,
        onNavigateToAnalysis = null,
        onNavigateToSettings = onNavigateToSettings
    ) { contentColor, progress ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CollapsingMetricsRow(visibilityProgress = (1f - progress * 1.15f).coerceIn(0f, 1f)) {
                SummaryMetricColumn(
                    label = "Buckets",
                    value = CurrencyFormatter.format(portfolioState.allocatedToBucketsCents),
                    contentColor = contentColor
                )
                SummaryMetricColumn(
                    label = "Funds",
                    value = CurrencyFormatter.format(portfolioState.allocatedToFundsCents),
                    contentColor = contentColor
                )
                SummaryMetricColumn(
                    label = "Spent",
                    value = CurrencyFormatter.format(portfolioState.totalSpentThisCycleCents),
                    contentColor = contentColor
                )
            }
            if (portfolioState.unassignedPlannedBudgetCents > 0L) {
                Text(
                    text = "Includes ${CurrencyFormatter.format(portfolioState.unassignedPlannedBudgetCents)} " +
                        "unassigned plan",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.72f)
                )
            }
        }
    }
}

@Composable
private fun ActiveBucketsSection(bucketSummaries: List<BucketSummaryState>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeading("Active buckets")
        bucketSummaries.forEachIndexed { index, summary ->
            if (index > 0) {
                HorizontalDivider()
            }
            Column(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = summary.bucket.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${CurrencyFormatter.format(summary.allocatedThisCycleCents)} allocated · " +
                        "${CurrencyFormatter.format(summary.spentThisCycleCents)} spent · " +
                        CurrencyFormatter.formatSigned(summary.remainingThisCycleCents),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FundsSection(funds: List<Fund>) {
    val defaultFund = remember(funds) { defaultFund(funds) } ?: return
    val targetProgressText = formatFundTargetProgress(defaultFund)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeading("Funds")
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        )
        {
            Text(
                text = defaultFund.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Balance · ${CurrencyFormatter.format(defaultFund.balanceCents)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            targetProgressText?.let { progressText ->
                Text(
                    text = progressText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

internal fun defaultFund(funds: List<Fund>): Fund? {
    // Future milestones can expand this to show all funds. For now, keep the portfolio UI
    // focused on the guaranteed default destination for closeout surplus.
    return funds.firstOrNull { it.uuid == DEFAULT_FUND_UUID }
}

internal fun formatFundTargetProgress(fund: Fund): String? {
    val targetAmountCents = fund.targetAmountCents?.takeIf { it > 0L } ?: return null
    val progressPercent = ((fund.progressPercent ?: 0f).roundToInt()).coerceIn(0, 100)
    return "${CurrencyFormatter.format(fund.balanceCents)} of " +
        "${CurrencyFormatter.format(targetAmountCents)} target · $progressPercent%"
}

@Composable
internal fun SummaryMetricColumn(
    label: String,
    value: String,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.72f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

@Composable
internal fun CollapsingMetricsRow(
    visibilityProgress: Float,
    content: @Composable RowScope.() -> Unit
) {
    val progress = visibilityProgress.coerceIn(0f, 1f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = progress
            }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val targetHeight = (placeable.height * progress).roundToInt()
                layout(placeable.width, targetHeight) {
                    placeable.placeRelative(0, ((targetHeight - placeable.height) / 2f).roundToInt())
                }
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
        content = content
    )
}

@Composable
internal fun SectionHeading(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun AddExpenseSheetDialog(
    showAddExpenseSheet: Boolean,
    canEditExpenses: Boolean,
    selectedDate: LocalDate,
    bucketOptions: List<BudgetBucket>,
    selectedBucketOverview: SelectedBucketOverview,
    onDismiss: () -> Unit,
    onAddExpense: (String, Long, String, ExpenseCategory?, LocalDate) -> Unit
) {
    if (!showAddExpenseSheet || !canEditExpenses) return

    AddExpenseSheet(
        onDismiss = onDismiss,
        bucketOptions = bucketOptions,
        initialBucketUuid = selectedBucketOverview.bucket.bucketUuid,
        initialBucketName = selectedBucketOverview.bucket.name,
        onSubmitExpense = { bucketUuid, amountCents, description, icon ->
            onAddExpense(bucketUuid, amountCents, description, icon, selectedDate)
        },
        title = addExpenseSheetTitle(selectedDate = selectedDate),
        confirmButtonText = addExpenseSheetConfirmLabel(selectedDate = selectedDate),
        dateLabel = addExpenseSheetDateLabel(selectedDate = selectedDate)
    )
}

@Composable
private fun EditExpenseSheetDialog(
    editingExpense: Expense?,
    canEditExpenses: Boolean,
    allBuckets: List<BudgetBucket>,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit,
    onUpdateExpense: (Expense) -> Unit,
    onDeleteExpense: (Expense) -> Unit,
    onRestoreExpense: (Expense) -> Unit,
    scope: CoroutineScope
) {
    val editableExpense = editingExpense?.takeIf { canEditExpenses } ?: return
    val openBuckets = allBuckets.filterNot { it.isClosed }
    val currentBucketName = allBuckets.firstOrNull { it.bucketUuid == editableExpense.bucketUuid }?.name
        ?: "Unknown bucket"

    AddExpenseSheet(
        onDismiss = onDismiss,
        bucketOptions = openBuckets,
        initialBucketUuid = editableExpense.bucketUuid,
        initialBucketName = currentBucketName,
        onSubmitExpense = { bucketUuid, amountCents, description, icon ->
            onUpdateExpense(
                editableExpense.copy(
                    bucketUuid = bucketUuid,
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
                    message = deletedExpenseMessage(editableExpense),
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

private fun addExpenseSheetTitle(selectedDate: LocalDate): String =
    "Add expense for ${selectedDate.format(DateTimeFormatter.ofPattern("MMM d"))}"

private fun addExpenseSheetConfirmLabel(selectedDate: LocalDate): String =
    "Add to ${selectedDate.format(DateTimeFormatter.ofPattern("MMM d"))}"

private fun addExpenseSheetDateLabel(selectedDate: LocalDate): String =
    "Recorded for ${selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))}"

private fun deletedExpenseMessage(expense: Expense): String =
    "Deleted \"${expense.displayDescription}\""
