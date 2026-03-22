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

private val HomeFabSize = 56.dp
private val HomeFabListClearance = 16.dp

private data class HomeBucketEditorState(
    val bucketUuid: String,
    val name: String,
    val trackingMode: BucketTrackingMode,
    val balanceBehavior: BucketBalanceBehavior,
    val amountText: String,
    val isSystemDefault: Boolean
)

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
private fun SummaryMetricColumn(
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
private fun CollapsingMetricsRow(
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
private fun SectionHeading(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun BucketHomePage(
    selectedBucketOverview: SelectedBucketOverview,
    spendingForecast: SpendingForecast?,
    bucketUuid: String,
    pageTitle: String,
    pageSummary: BucketSummaryState?,
    canEditExpenses: Boolean,
    isLoadingData: Boolean,
    onEditExpense: (Expense) -> Unit,
    onNavigateToAnalysis: (() -> Unit)?,
    showTopRightSettingsAction: Boolean,
    onNavigateToSettings: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    if (selectedBucketOverview.bucket.bucketUuid != bucketUuid) {
        val placeholderOverview = pageSummary?.let { summary ->
            SelectedBucketOverview(
                bucket = summary.bucket,
                summary = summary,
                budgetState = summary.budgetState,
                todayExpenses = emptyList(),
                activeCycleExpenseSections = emptyList(),
                spendingForecast = null
            )
        }
        val placeholderBudgetState = placeholderOverview?.budgetState
            ?: placeholderOverview?.summary?.budgetState
            ?: selectedBucketOverview.budgetState
            ?: selectedBucketOverview.summary.budgetState

        PlaceholderShimmerProvider {
            if (
                placeholderOverview != null &&
                placeholderOverview.bucket.trackingMode == BucketTrackingMode.DAILY_TARGET &&
                placeholderBudgetState != null
            ) {
                OverviewPage(
                    modifier = modifier.fillMaxSize(),
                    budgetState = placeholderBudgetState,
                    todayExpenses = emptyList(),
                    activeCycleExpenseSections = emptyList(),
                    spendingForecast = SpendingForecast(),
                    onEditTodayExpense = null,
                    isLoading = true,
                    headerTitle = pageTitle,
                    headerAnalysisAction = onNavigateToAnalysis,
                    headerSettingsAction = if (showTopRightSettingsAction) onNavigateToSettings else null,
                    onNavigateToSettings = null,
                    enableHeaderCollapse = true,
                    defaultCollapsedHeader = false,
                    bottomContentPadding = HomeFabSize + HomeFabListClearance + 16.dp
                )
            } else {
                ReserveBucketLoadingPage(
                    selectedBucketOverview = placeholderOverview ?: selectedBucketOverview,
                    pageTitle = pageTitle,
                    onNavigateToAnalysis = onNavigateToAnalysis,
                    showTopRightSettingsAction = showTopRightSettingsAction,
                    onNavigateToSettings = onNavigateToSettings,
                    modifier = modifier
                )
            }
        }
        return
    }

    if (selectedBucketOverview.budgetState != null && spendingForecast != null) {
        OverviewPage(
            modifier = Modifier
                .then(modifier)
                .fillMaxSize(),
            budgetState = selectedBucketOverview.budgetState,
            todayExpenses = selectedBucketOverview.todayExpenses,
            activeCycleExpenseSections = selectedBucketOverview.activeCycleExpenseSections,
            spendingForecast = spendingForecast,
            onEditTodayExpense = if (canEditExpenses) {
                { expense -> onEditExpense(expense) }
            } else {
                null
            },
            isLoading = isLoadingData,
            headerTitle = pageTitle,
            headerAnalysisAction = onNavigateToAnalysis,
            headerSettingsAction = if (showTopRightSettingsAction) onNavigateToSettings else null,
            onNavigateToSettings = null,
            enableHeaderCollapse = true,
            defaultCollapsedHeader = false,
            bottomContentPadding = HomeFabSize + HomeFabListClearance + 16.dp
        )
    } else {
        val layoutState = rememberOverviewPageLayoutState(
            defaultCollapsedHeader = false,
            enableHeaderCollapse = true
        )
        CollapsingSummaryLayout(
            layoutState = layoutState,
            config = CollapsingSummaryLayoutConfig(
                modifier = modifier.fillMaxSize(),
                headerHorizontalPadding = 0.dp,
                headerTopPadding = 0.dp,
                bottomContentPadding = HomeFabSize + HomeFabListClearance + 16.dp
            ),
            header = { collapseProgress ->
                ReserveSummaryCard(
                    selectedBucketOverview = selectedBucketOverview,
                    collapseProgress = collapseProgress,
                    onNavigateToAnalysis = onNavigateToAnalysis,
                    onNavigateToSettings = if (showTopRightSettingsAction) onNavigateToSettings else null
                )
            }
        ) { listState, contentPadding ->
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                state = listState,
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                item {
                    OverviewLikeSection {
                        ReserveDetailsSection(selectedBucketOverview = selectedBucketOverview)
                    }
                }
                item {
                    OverviewLikeSection {
                        ReserveExpensesSection(
                            selectedBucketOverview = selectedBucketOverview,
                            canEditExpenses = canEditExpenses,
                            onEditExpense = onEditExpense
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReserveBucketLoadingPage(
    selectedBucketOverview: SelectedBucketOverview,
    pageTitle: String,
    onNavigateToAnalysis: (() -> Unit)?,
    showTopRightSettingsAction: Boolean,
    onNavigateToSettings: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        ReserveSummaryCard(
            selectedBucketOverview = selectedBucketOverview,
            collapseProgress = 0f,
            onNavigateToAnalysis = onNavigateToAnalysis,
            onNavigateToSettings = if (showTopRightSettingsAction) onNavigateToSettings else null
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            ReserveLoadingSection(
                title = "$pageTitle details",
                rows = listOf("$8,888", "$888", "$88")
            )
            ReserveLoadingSection(
                title = "Recent activity",
                rows = listOf("Groceries", "Lunch with team", "Coffee")
            )
        }
    }
}

@Composable
private fun ReserveLoadingSection(
    title: String,
    rows: List<String>
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        LoadingValuePlaceholder(
            sampleText = title,
            textStyle = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Start
        )
        rows.forEachIndexed { index, sampleText ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LoadingValuePlaceholder(
                    sampleText = sampleText,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Start,
                    fillWidth = index == rows.lastIndex
                )
                if (index != rows.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun ReserveSummaryCard(
    selectedBucketOverview: SelectedBucketOverview,
    collapseProgress: Float,
    onNavigateToAnalysis: (() -> Unit)?,
    onNavigateToSettings: (() -> Unit)?
) {
    val showTestTags = !LocalCollapsingHeaderIsForMeasurement.current
    val summary = selectedBucketOverview.summary

    TopSummaryCard(
        title = selectedBucketOverview.bucket.name,
        amountText = CurrencyFormatter.formatSigned(summary.remainingThisCycleCents),
        subtitleText = null,
        collapseProgress = collapseProgress,
        useWarningTint = summary.remainingThisCycleCents < 0L || summary.overspentCents > 0L,
        onNavigateToAnalysis = onNavigateToAnalysis,
        onNavigateToSettings = onNavigateToSettings,
        headerRowTestTag = if (showTestTags) "home_page_header_row" else null,
        titleTestTag = if (showTestTags) "home_page_header_title" else null,
        analysisTestTag = if (showTestTags) "home_page_header_analysis" else null,
        settingsTestTag = if (showTestTags) "home_page_header_settings" else null
    ) { contentColor, progress ->
        CollapsingMetricsRow(visibilityProgress = (1f - progress * 1.15f).coerceIn(0f, 1f)) {
            SummaryMetricColumn(
                label = "Allocated",
                value = CurrencyFormatter.format(summary.allocatedThisCycleCents),
                contentColor = contentColor
            )
            SummaryMetricColumn(
                label = "Spent",
                value = CurrencyFormatter.format(summary.spentThisCycleCents),
                contentColor = contentColor
            )
            if (summary.earmarkedBalanceCents > 0L) {
                SummaryMetricColumn(
                    label = "Earmarked",
                    value = CurrencyFormatter.format(summary.earmarkedBalanceCents),
                    contentColor = contentColor
                )
            }
        }
    }
}

@Composable
private fun TopSummaryCard(
    title: String,
    amountText: String,
    subtitleText: String?,
    collapseProgress: Float,
    useWarningTint: Boolean,
    onNavigateToAnalysis: (() -> Unit)?,
    onNavigateToSettings: (() -> Unit)?,
    headerRowTestTag: String? = null,
    titleTestTag: String? = null,
    analysisTestTag: String? = null,
    settingsTestTag: String? = null,
    metrics: @Composable RowScope.(contentColor: androidx.compose.ui.graphics.Color, progress: Float) -> Unit
) {
    val colors = summaryCardColors(useWarningTint = useWarningTint)
    val progress = collapseProgress.coerceIn(0f, 1f)
    val horizontalPadding = lerp(20.dp, 16.dp, progress)
    val verticalPadding = lerp(18.dp, 10.dp, progress)
    val mergedHeaderBodyOffset = lerp(0.dp, (-6).dp, progress)
    val contentSpacing = lerp(12.dp, 6.dp, progress)
    val amountFontSize = lerp(34.sp, 24.sp, progress)

    MergedSummaryHeaderSurface(
        title = title,
        summaryColors = colors,
        modifier = Modifier.fillMaxWidth(),
        headerHorizontalPadding = horizontalPadding,
        headerBottomPadding = 0.dp,
        onNavigateToAnalysis = onNavigateToAnalysis,
        onNavigateToSettings = onNavigateToSettings,
        headerRowTestTag = headerRowTestTag,
        titleTestTag = titleTestTag,
        analysisTestTag = analysisTestTag,
        settingsTestTag = settingsTestTag
    ) {
        Column(
            modifier = Modifier
                .padding(
                    start = horizontalPadding,
                    top = 0.dp,
                    end = horizontalPadding,
                    bottom = verticalPadding
                )
                .offset(y = mergedHeaderBodyOffset),
            verticalArrangement = Arrangement.spacedBy(contentSpacing)
        ) {
            Text(
                text = amountText,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = amountFontSize,
                    fontWeight = FontWeight.Black
                ),
                fontWeight = FontWeight.Black,
                color = colors.content
            )
            if (subtitleText != null) {
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.content.copy(alpha = 0.72f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                metrics(colors.content, progress)
            }
        }
    }
}

@Composable
private fun ReserveDetailsSection(
    selectedBucketOverview: SelectedBucketOverview
) {
    val summary = selectedBucketOverview.summary
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        SectionHeading("Reserve details")
        PlainMetricRow("Allocated", CurrencyFormatter.format(summary.allocatedThisCycleCents))
        PlainMetricRow("Spent", CurrencyFormatter.format(summary.spentThisCycleCents))
        PlainMetricRow("Remaining", CurrencyFormatter.formatSigned(summary.remainingThisCycleCents))
        if (summary.overspentCents > 0L) {
            PlainMetricRow("Overspent", CurrencyFormatter.format(summary.overspentCents))
        }
        if (summary.earmarkedBalanceCents > 0L) {
            PlainMetricRow("Earmarked balance", CurrencyFormatter.format(summary.earmarkedBalanceCents))
        }
    }
}

@Composable
private fun ReserveExpensesSection(
    selectedBucketOverview: SelectedBucketOverview,
    canEditExpenses: Boolean,
    onEditExpense: (Expense) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeading("Cycle expenses")
        if (selectedBucketOverview.activeCycleExpenseSections.isEmpty()) {
            Text(
                text = "No expenses recorded in this bucket this cycle.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            selectedBucketOverview.activeCycleExpenseSections.forEach { daySection ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = daySection.date.format(DateTimeFormatter.ofPattern("EEE, MMM d")),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    daySection.expenses.forEachIndexed { index, expense ->
                        ExpenseItem(
                            expense = expense,
                            showDivider = index != daySection.expenses.lastIndex,
                            onEdit = if (canEditExpenses) {
                                { onEditExpense(expense) }
                            } else {
                                null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewLikeSection(
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        content()
    }
}

@Composable
private fun PlainMetricRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddBucketSheet(
    showSheet: Boolean,
    portfolioBudgetCents: Long,
    existingBuckets: List<BudgetBucket>,
    bucketSummaries: List<BucketSummaryState>,
    onDismiss: () -> Unit,
    onCreateBucket: (BucketDraft) -> Unit,
) {
    if (!showSheet) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        AddBucketForm(
            portfolioBudgetCents = portfolioBudgetCents,
            existingBuckets = existingBuckets,
            bucketSummaries = bucketSummaries,
            onDismiss = onDismiss,
            onCreateBucket = onCreateBucket
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeBucketSettingsSheet(
    state: HomeBucketEditorState?,
    allBuckets: List<BudgetBucket>,
    bucketSummaries: List<BucketSummaryState>,
    portfolioBudgetCents: Long,
    onDismiss: () -> Unit,
    onSaveSettings: (BucketDraft) -> Unit
) {
    val editor = state ?: return
    var name by remember(editor) { mutableStateOf(editor.name) }
    var amountText by remember(editor) { mutableStateOf(editor.amountText) }
    var trackingMode by remember(editor) { mutableStateOf(editor.trackingMode) }
    var balanceBehavior by remember(editor) { mutableStateOf(editor.balanceBehavior) }
    var errorMessage by remember(editor) { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Bucket settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    errorMessage = null
                },
                label = { Text("Bucket name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = amountText,
                onValueChange = {
                    if (!editor.isSystemDefault) {
                        amountText = it
                    }
                    errorMessage = null
                },
                label = { Text(if (editor.isSystemDefault) "Computed remainder" else "Cycle allocation") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                enabled = !editor.isSystemDefault,
                modifier = Modifier.fillMaxWidth()
            )
            if (editor.isSystemDefault) {
                Text(
                    text = "This system bucket always absorbs the leftover portfolio budget after your named buckets.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Tracking mode",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BucketTrackingMode.entries.forEach { mode ->
                        FilterChip(
                            selected = trackingMode == mode,
                            onClick = {
                                trackingMode = mode
                                errorMessage = null
                            },
                            label = { Text(mode.displayLabel()) }
                        )
                    }
                }
                Text(
                    text = "Balance behavior",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BucketBalanceBehavior.entries.forEach { behavior ->
                        FilterChip(
                            selected = balanceBehavior == behavior,
                            onClick = {
                                balanceBehavior = behavior
                                errorMessage = null
                            },
                            label = { Text(behavior.displayLabel()) }
                        )
                    }
                }
            }
            errorMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val trimmedName = name.trim()
                        val normalizedName = trimmedName.lowercase()
                        val amountCents = CurrencyFormatter.parseAmountToCents(amountText)
                        val otherAllocatedCents = sumOtherNamedBucketAllocationsForValidation(
                            allBuckets = allBuckets,
                            bucketSummaries = bucketSummaries,
                            editedBucketUuid = editor.bucketUuid
                        )
                        when {
                            trimmedName.isBlank() -> errorMessage = "Enter a bucket name."
                            allBuckets.any {
                                it.bucketUuid != editor.bucketUuid &&
                                    !it.isClosed &&
                                    it.name.trim().lowercase() == normalizedName
                            } -> errorMessage = "Bucket names must be unique."
                            !editor.isSystemDefault && (amountCents == null || amountCents < 0L) ->
                                errorMessage = "Enter a valid allocation."
                            !editor.isSystemDefault && otherAllocatedCents + requireNotNull(amountCents) > portfolioBudgetCents ->
                                errorMessage = "Allocation exceeds the portfolio total."
                            else -> {
                                onSaveSettings(
                                    BucketDraft(
                                        bucketUuid = editor.bucketUuid,
                                        name = trimmedName,
                                        trackingMode = trackingMode,
                                        balanceBehavior = balanceBehavior,
                                        defaultAllocatedAmountCents = amountCents ?: 0L,
                                        sortOrder = allBuckets.firstOrNull { it.bucketUuid == editor.bucketUuid }?.sortOrder ?: 0,
                                        closeRequested = false
                                    )
                                )
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
internal fun AddBucketForm(
    portfolioBudgetCents: Long,
    existingBuckets: List<BudgetBucket>,
    bucketSummaries: List<BucketSummaryState>,
    onDismiss: () -> Unit,
    onCreateBucket: (BucketDraft) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("0.00") }
    var trackingMode by rememberSaveable { mutableStateOf(BucketTrackingMode.DAILY_TARGET) }
    var balanceBehavior by rememberSaveable { mutableStateOf(BucketBalanceBehavior.RETURN_TO_PORTFOLIO) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val allocatedToNamedBucketsCents = bucketSummaries
        .filterNot { it.bucket.bucketUuid == DEFAULT_SPENDING_BUCKET_UUID || it.bucket.isClosed }
        .sumOf { it.allocatedThisCycleCents }
    val computedDefaultBucketCents = (portfolioBudgetCents - allocatedToNamedBucketsCents).coerceAtLeast(0L)

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Add bucket",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Create a bucket here, then fine-tune it in Settings if needed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Default bucket remainder: ${CurrencyFormatter.format(computedDefaultBucketCents)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text("Bucket name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        errorMessage = null
                    },
                    label = { Text("Cycle allocation") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    text = "Tracking mode",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BucketTrackingMode.entries.forEach { mode ->
                        FilterChip(
                            selected = trackingMode == mode,
                            onClick = { trackingMode = mode },
                            label = { Text(mode.displayLabel()) }
                        )
                    }
                }
                Text(
                    text = "Balance behavior",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BucketBalanceBehavior.entries.forEach { behavior ->
                        FilterChip(
                            selected = balanceBehavior == behavior,
                            onClick = { balanceBehavior = behavior },
                            label = { Text(behavior.displayLabel()) }
                        )
                    }
                }
                errorMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val allocationCents = CurrencyFormatter.parseAmountToCents(amountText)
                            val trimmedName = name.trim()
                            val normalizedName = trimmedName.lowercase()
                            when {
                                trimmedName.isBlank() -> errorMessage = "Enter a bucket name."
                                existingBuckets.any { it.name.trim().lowercase() == normalizedName && !it.isClosed } ->
                                    errorMessage = "Bucket names must be unique."
                                allocationCents == null || allocationCents < 0L ->
                                    errorMessage = "Enter a valid allocation."
                                allocatedToNamedBucketsCents + allocationCents > portfolioBudgetCents ->
                                    errorMessage = "Allocation exceeds the portfolio total."
                                else -> {
                                    onCreateBucket(
                                        BucketDraft(
                                            bucketUuid = UUID.randomUUID().toString(),
                                            name = trimmedName,
                                            trackingMode = trackingMode,
                                            balanceBehavior = balanceBehavior,
                                            defaultAllocatedAmountCents = allocationCents,
                                            sortOrder = (existingBuckets.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                                            closeRequested = false
                                        )
                                    )
                                    name = ""
                                    amountText = "0.00"
                                    trackingMode = BucketTrackingMode.DAILY_TARGET
                                    balanceBehavior = BucketBalanceBehavior.RETURN_TO_PORTFOLIO
                                    errorMessage = null
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Create bucket")
                    }
                }
            }
        }
        if (bucketSummaries.isNotEmpty()) {
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Current allocations",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    bucketSummaries.forEachIndexed { index, summary ->
                        if (index > 0) {
                            HorizontalDivider()
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.padding(end = 16.dp)) {
                                Text(
                                    text = summary.bucket.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = summary.bucket.trackingMode.displayLabel(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = CurrencyFormatter.format(summary.allocatedThisCycleCents),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
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

internal fun buildExistingHomeBucketDrafts(
    allBuckets: List<BudgetBucket>,
    bucketSummaries: List<BucketSummaryState>
): List<BucketDraft> {
    val summaryByBucketUuid = bucketSummaries.associateBy { it.bucket.bucketUuid }
    return allBuckets
        .sortedWith(compareBy<BudgetBucket> { it.sortOrder }.thenBy { it.createdAtEpochMs })
        .map { bucket ->
            val effectiveAllocation = summaryByBucketUuid[bucket.bucketUuid]?.allocatedThisCycleCents
                ?: bucket.defaultAllocatedAmountCents
            BucketDraft(
                bucketUuid = bucket.bucketUuid,
                name = bucket.name,
                trackingMode = bucket.trackingMode,
                balanceBehavior = bucket.balanceBehavior,
                defaultAllocatedAmountCents = effectiveAllocation,
                sortOrder = bucket.sortOrder,
                closeRequested = bucket.isClosed
            )
        }
}

internal fun sumOtherNamedBucketAllocationsForValidation(
    allBuckets: List<BudgetBucket>,
    bucketSummaries: List<BucketSummaryState>,
    editedBucketUuid: String
): Long {
    return buildExistingHomeBucketDrafts(
        allBuckets = allBuckets,
        bucketSummaries = bucketSummaries
    )
        .filterNot { draft ->
            draft.bucketUuid == editedBucketUuid ||
                draft.bucketUuid == DEFAULT_SPENDING_BUCKET_UUID ||
                draft.closeRequested
        }
        .sumOf { it.defaultAllocatedAmountCents }
}

internal fun buildHomeBucketDrafts(
    allBuckets: List<BudgetBucket>,
    bucketSummaries: List<BucketSummaryState>,
    newBucketDraft: BucketDraft
): List<BucketDraft> {
    return buildExistingHomeBucketDrafts(
        allBuckets = allBuckets,
        bucketSummaries = bucketSummaries
    ) + newBucketDraft
}

private fun buildUpdatedHomeBucketDrafts(
    allBuckets: List<BudgetBucket>,
    bucketSummaries: List<BucketSummaryState>,
    updatedBucketDraft: BucketDraft
): List<BucketDraft> {
    return buildExistingHomeBucketDrafts(
        allBuckets = allBuckets,
        bucketSummaries = bucketSummaries
    ).map { draft ->
        if (draft.bucketUuid == updatedBucketDraft.bucketUuid) updatedBucketDraft else draft
    }
}

private fun BucketTrackingMode.displayLabel(): String = when (this) {
    BucketTrackingMode.DAILY_TARGET -> "Daily target"
    BucketTrackingMode.CYCLE_RESERVE -> "Cycle reserve"
}

private fun BucketBalanceBehavior.displayLabel(): String = when (this) {
    BucketBalanceBehavior.RETURN_TO_PORTFOLIO -> "Return to portfolio"
    BucketBalanceBehavior.RETAIN_IN_BUCKET -> "Retain in bucket"
}
