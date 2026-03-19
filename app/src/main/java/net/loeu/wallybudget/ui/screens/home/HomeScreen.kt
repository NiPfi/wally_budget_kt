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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Switch
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
import net.loeu.wallybudget.domain.model.BudgetChangeMode
import net.loeu.wallybudget.domain.model.BucketBalanceBehavior
import net.loeu.wallybudget.domain.model.BucketSummaryState
import net.loeu.wallybudget.domain.model.BucketTrackingMode
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.ExpenseCategory
import net.loeu.wallybudget.domain.model.PortfolioState
import net.loeu.wallybudget.domain.model.SelectedBucketOverview
import net.loeu.wallybudget.domain.model.SpendingForecast
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.model.displayDescription
import net.loeu.wallybudget.domain.model.recordedDate
import net.loeu.wallybudget.domain.usecase.BucketDraft
import net.loeu.wallybudget.ui.components.PagerDots
import net.loeu.wallybudget.ui.components.TimelineLockBanner
import net.loeu.wallybudget.ui.screens.expenses.ExpenseItem
import net.loeu.wallybudget.ui.screens.overview.CollapsingSummaryLayout
import net.loeu.wallybudget.ui.screens.overview.CollapsingSummaryLayoutConfig
import net.loeu.wallybudget.ui.screens.overview.LocalCollapsingHeaderIsForMeasurement
import net.loeu.wallybudget.ui.screens.overview.MergedSummaryHeaderSurface
import net.loeu.wallybudget.ui.screens.overview.OverviewPage
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
private sealed interface HomePage {
    data object Portfolio : HomePage
    data class Bucket(val bucketUuid: String) : HomePage
}

private data class HomeBucketEditorState(
    val bucketUuid: String,
    val name: String,
    val trackingMode: BucketTrackingMode,
    val balanceBehavior: BucketBalanceBehavior,
    val amountText: String,
    val isPrimary: Boolean
)

@Composable
fun HomeScreen(
    portfolioState: PortfolioState,
    bucketSummaries: List<BucketSummaryState>,
    selectedBucketOverview: SelectedBucketOverview,
    allBuckets: List<BudgetBucket>,
    userSettings: UserSettings,
    currentDate: LocalDate,
    spendingForecast: SpendingForecast?,
    onSelectBucket: (String) -> Unit,
    onSaveSettings: (Long, Int, List<BucketDraft>, BudgetChangeMode) -> Unit,
    onAddExpense: (String, Long, String, ExpenseCategory?, LocalDate) -> Unit,
    onRestoreExpense: (Expense) -> Unit,
    onUpdateExpense: (Expense) -> Unit,
    onDeleteExpense: (Expense) -> Unit,
    onNavigateToSettings: () -> Unit,
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
    var showAddBucketDialog by rememberSaveable { mutableStateOf(false) }
    var bucketEditorState by remember { mutableStateOf<HomeBucketEditorState?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val canEditExpenses = !isLoadingData && timelineLockReason == null
    val openBuckets = allBuckets.filterNot { it.isClosed }
    val orderedOpenSummaries = remember(bucketSummaries) {
        bucketSummaries.sortedWith(compareBy<BucketSummaryState> { it.bucket.sortOrder }.thenBy { it.bucket.createdAtEpochMs })
    }
    val primaryBucketUuid = remember(openBuckets, userSettings.primaryBucketUuid) {
        openBuckets.firstOrNull { it.bucketUuid == userSettings.primaryBucketUuid }?.bucketUuid
            ?: openBuckets.firstOrNull { it.isPrimary }?.bucketUuid
            ?: openBuckets.firstOrNull()?.bucketUuid
    }
    val pages = remember(orderedOpenSummaries) {
        buildList {
            add(HomePage.Portfolio)
            orderedOpenSummaries.forEach { add(HomePage.Bucket(it.bucket.bucketUuid)) }
        }
    }
    val defaultPageIndex = remember(pages, primaryBucketUuid) {
        pages.indexOfFirst { page ->
            page is HomePage.Bucket && page.bucketUuid == primaryBucketUuid
        }.takeIf { it >= 0 } ?: 0
    }
    val pagerState = rememberPagerState(initialPage = defaultPageIndex) { pages.size }
    var didInitializePage by rememberSaveable { mutableStateOf(false) }
    var pendingCreatedBucketUuid by rememberSaveable { mutableStateOf<String?>(null) }

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

    LaunchedEffect(defaultPageIndex, primaryBucketUuid, didInitializePage, pages.size) {
        if (!didInitializePage && pages.isNotEmpty()) {
            pagerState.scrollToPage(defaultPageIndex)
            primaryBucketUuid?.let(onSelectBucket)
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
            isPrimary = bucket.isPrimary
        )
    }

    LaunchedEffect(pagerState, pages) {
        snapshotFlow { pagerState.settledPage }
            .map { page -> pages.getOrNull(page) }
            .filter { it is HomePage.Bucket }
            .map { (it as HomePage.Bucket).bucketUuid }
            .distinctUntilChanged()
            .collect { onSelectBucket(it) }
    }

    LaunchedEffect(openBuckets, pendingCreatedBucketUuid, pages) {
        val targetBucketUuid = pendingCreatedBucketUuid ?: return@LaunchedEffect
        val targetPage = pages.indexOfFirst { page ->
            page is HomePage.Bucket && page.bucketUuid == targetBucketUuid
        }
        if (targetPage >= 0) {
            pagerState.animateScrollToPage(targetPage)
            onSelectBucket(targetBucketUuid)
            pendingCreatedBucketUuid = null
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (!canEditExpenses) return@FloatingActionButton
                    when (pages.getOrNull(pagerState.currentPage)) {
                        HomePage.Portfolio -> showAddBucketDialog = true
                        is HomePage.Bucket -> onShowAddExpenseSheet()
                        null -> Unit
                    }
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = if (pages.getOrNull(pagerState.currentPage) == HomePage.Portfolio) {
                        "Add bucket"
                    } else {
                        "Add expense"
                    }
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
                    when (val page = pages[pageIndex]) {
                        HomePage.Portfolio -> PortfolioOverviewPage(
                            portfolioState = portfolioState,
                            bucketSummaries = orderedOpenSummaries,
                            showTopRightSettingsAction = showTopRightSettingsAction,
                            onNavigateToSettings = onNavigateToSettings,
                            modifier = Modifier.fillMaxSize()
                        )

                        is HomePage.Bucket -> BucketHomePage(
                            selectedBucketOverview = selectedBucketOverview,
                            spendingForecast = spendingForecast,
                            bucketUuid = page.bucketUuid,
                            pageTitle = orderedOpenSummaries.firstOrNull { it.bucket.bucketUuid == page.bucketUuid }?.bucket?.name
                                ?: "Bucket",
                            pageSummary = orderedOpenSummaries.firstOrNull { it.bucket.bucketUuid == page.bucketUuid },
                            canEditExpenses = canEditExpenses,
                            isLoadingData = isLoadingData,
                            onEditExpense = { expenseBeingEdited = it },
                            showTopRightSettingsAction = showTopRightSettingsAction,
                            onNavigateToSettings = if (showTopRightSettingsAction) {
                                { openBucketSettings(page.bucketUuid) }
                            } else {
                                null
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
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
    AddBucketSheet(
        showSheet = showAddBucketDialog,
        portfolioBudgetCents = userSettings.resolvedPortfolioMonthlyBudgetCents,
        allocatedToBucketsCents = portfolioState.allocatedToBucketsCents,
        existingBuckets = allBuckets,
        bucketSummaries = bucketSummaries,
        onDismiss = { showAddBucketDialog = false },
        onCreateBucket = { newBucketDraft ->
            onSaveSettings(
                userSettings.resolvedPortfolioMonthlyBudgetCents,
                userSettings.paydayDate,
                buildHomeBucketDrafts(
                    allBuckets = allBuckets,
                    bucketSummaries = bucketSummaries,
                    newBucketDraft = newBucketDraft
                ),
                BudgetChangeMode.PRORATE_CURRENT_CYCLE
            )
            pendingCreatedBucketUuid = newBucketDraft.bucketUuid
            showAddBucketDialog = false
        }
    )
    HomeBucketSettingsSheet(
        state = bucketEditorState,
        allBuckets = allBuckets,
        bucketSummaries = bucketSummaries,
        portfolioBudgetCents = userSettings.resolvedPortfolioMonthlyBudgetCents,
        onDismiss = { bucketEditorState = null },
        onSaveSettings = { updatedBucketDraft ->
            onSaveSettings(
                userSettings.resolvedPortfolioMonthlyBudgetCents,
                userSettings.paydayDate,
                buildUpdatedHomeBucketDrafts(
                    allBuckets = allBuckets,
                    bucketSummaries = bucketSummaries,
                    updatedBucketDraft = updatedBucketDraft
                ),
                BudgetChangeMode.PRORATE_CURRENT_CYCLE
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
private fun PortfolioOverviewPage(
    portfolioState: PortfolioState,
    bucketSummaries: List<BucketSummaryState>,
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
            headerHorizontalPadding = 0.dp,
            headerTopPadding = 0.dp,
            bottomContentPadding = HomeFabSize + HomeFabListClearance + 16.dp
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
            modifier = Modifier.fillMaxWidth(),
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                OverviewLikeSection {
                    PortfolioDetailsSection(portfolioState = portfolioState)
                }
            }
            item {
                OverviewLikeSection {
                    ActiveBucketsSection(bucketSummaries = bucketSummaries)
                }
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
    val showTestTags = !LocalCollapsingHeaderIsForMeasurement.current
    val colors = summaryCardColors(useWarningTint = portfolioState.remainingThisCycleCents < 0L)
    val progress = collapseProgress.coerceIn(0f, 1f)
    val horizontalPadding = lerp(20.dp, 16.dp, progress)
    val verticalPadding = lerp(18.dp, 10.dp, progress)
    val contentSpacing = lerp(12.dp, 6.dp, progress)
    val amountFontSize = lerp(34.sp, 24.sp, progress)

    MergedSummaryHeaderSurface(
        title = "Portfolio",
        summaryColors = colors,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (showTestTags) Modifier else Modifier),
        onNavigateToSettings = onNavigateToSettings,
        headerRowTestTag = if (showTestTags) "home_page_header_row" else null,
        titleTestTag = if (showTestTags) "home_page_header_title" else null,
        settingsTestTag = if (showTestTags) "home_page_header_settings" else null
    ) {
        Column(
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalArrangement = Arrangement.spacedBy(contentSpacing)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = CurrencyFormatter.format(portfolioState.remainingThisCycleCents),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = amountFontSize,
                        fontWeight = FontWeight.Black
                    ),
                    fontWeight = FontWeight.Black,
                    color = colors.content
                )
            }
            CollapsingMetricsRow(visibilityProgress = (1f - progress * 1.15f).coerceIn(0f, 1f)) {
                SummaryMetricColumn(
                    label = "Portfolio total",
                    value = CurrencyFormatter.format(portfolioState.portfolioTotalBudgetCents),
                    contentColor = colors.content
                )
                SummaryMetricColumn(
                    label = "Allocated",
                    value = CurrencyFormatter.format(portfolioState.allocatedToBucketsCents),
                    contentColor = colors.content
                )
                SummaryMetricColumn(
                    label = "Unassigned",
                    value = CurrencyFormatter.format(portfolioState.unassignedPlannedBudgetCents),
                    contentColor = colors.content
                )
            }
        }
    }
}
@Composable
private fun PortfolioDetailsSection(
    portfolioState: PortfolioState
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeading("Portfolio details")
        PlainMetricRow("Portfolio total", CurrencyFormatter.format(portfolioState.portfolioTotalBudgetCents))
        PlainMetricRow("Allocated to buckets", CurrencyFormatter.format(portfolioState.allocatedToBucketsCents))
        PlainMetricRow("Unassigned planned", CurrencyFormatter.format(portfolioState.unassignedPlannedBudgetCents))
        PlainMetricRow("Net reserve", CurrencyFormatter.formatSigned(portfolioState.netReserveCents))
        if (portfolioState.earmarkedReserveCents > 0L) {
            PlainMetricRow("Earmarked reserve", CurrencyFormatter.format(portfolioState.earmarkedReserveCents))
            PlainMetricRow("Unassigned reserve", CurrencyFormatter.formatSigned(portfolioState.unassignedReserveCents))
        }
    }
}

@Composable
private fun ActiveBucketsSection(
    bucketSummaries: List<BucketSummaryState>
) {
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
    showTopRightSettingsAction: Boolean,
    onNavigateToSettings: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    if (selectedBucketOverview.bucket.bucketUuid != bucketUuid) {
        Column(modifier = modifier.fillMaxSize()) {
            pageSummary?.let { summary ->
                ReserveSummaryCard(
                    selectedBucketOverview = SelectedBucketOverview(
                        bucket = summary.bucket,
                        summary = summary,
                        budgetState = summary.budgetState,
                        todayExpenses = emptyList(),
                        activeCycleExpenseSections = emptyList(),
                        spendingForecast = null
                    ),
                    collapseProgress = 0f,
                    onNavigateToSettings = if (showTopRightSettingsAction) onNavigateToSettings else null
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.TopStart
            ) {
                Text(
                    text = "Loading $pageTitle…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun ReserveSummaryCard(
    selectedBucketOverview: SelectedBucketOverview,
    collapseProgress: Float,
    onNavigateToSettings: (() -> Unit)?
) {
    val showTestTags = !LocalCollapsingHeaderIsForMeasurement.current
    val summary = selectedBucketOverview.summary
    val colors = summaryCardColors(
        useWarningTint = summary.remainingThisCycleCents < 0L || summary.overspentCents > 0L
    )
    val progress = collapseProgress.coerceIn(0f, 1f)
    val horizontalPadding = lerp(20.dp, 16.dp, progress)
    val verticalPadding = lerp(18.dp, 10.dp, progress)
    val contentSpacing = lerp(12.dp, 6.dp, progress)
    val amountFontSize = lerp(34.sp, 24.sp, progress)
    MergedSummaryHeaderSurface(
        title = selectedBucketOverview.bucket.name,
        summaryColors = colors,
        modifier = Modifier.fillMaxWidth(),
        onNavigateToSettings = onNavigateToSettings,
        headerRowTestTag = if (showTestTags) "home_page_header_row" else null,
        titleTestTag = if (showTestTags) "home_page_header_title" else null,
        settingsTestTag = if (showTestTags) "home_page_header_settings" else null
    ) {
        Column(
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalArrangement = Arrangement.spacedBy(contentSpacing)
        ) {
            Text(
                text = CurrencyFormatter.formatSigned(summary.remainingThisCycleCents),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = amountFontSize,
                    fontWeight = FontWeight.Black
                ),
                fontWeight = FontWeight.Black,
                color = colors.content
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                CollapsingMetricsRow(visibilityProgress = (1f - progress * 1.15f).coerceIn(0f, 1f)) {
                    SummaryMetricColumn(
                        label = "Allocated",
                        value = CurrencyFormatter.format(summary.allocatedThisCycleCents),
                        contentColor = colors.content
                    )
                    SummaryMetricColumn(
                        label = "Spent",
                        value = CurrencyFormatter.format(summary.spentThisCycleCents),
                        contentColor = colors.content
                    )
                    if (summary.earmarkedBalanceCents > 0L) {
                        SummaryMetricColumn(
                            label = "Earmarked",
                            value = CurrencyFormatter.format(summary.earmarkedBalanceCents),
                            contentColor = colors.content
                        )
                    }
                }
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
private fun AddBucketSheet(
    showSheet: Boolean,
    portfolioBudgetCents: Long,
    allocatedToBucketsCents: Long,
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
            allocatedToBucketsCents = allocatedToBucketsCents,
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
    var isPrimary by remember(editor) { mutableStateOf(editor.isPrimary) }
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
                    amountText = it
                    errorMessage = null
                },
                label = { Text("Cycle allocation") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Primary bucket", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = isPrimary,
                    onCheckedChange = {
                        isPrimary = it
                        errorMessage = null
                    }
                )
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
                        val otherAllocatedCents = buildExistingHomeBucketDrafts(
                            allBuckets = allBuckets,
                            bucketSummaries = bucketSummaries
                        )
                            .filterNot { it.bucketUuid == editor.bucketUuid || it.closeRequested }
                            .sumOf { it.defaultAllocatedAmountCents }
                        when {
                            trimmedName.isBlank() -> errorMessage = "Enter a bucket name."
                            allBuckets.any {
                                it.bucketUuid != editor.bucketUuid &&
                                    !it.isClosed &&
                                    it.name.trim().lowercase() == normalizedName
                            } -> errorMessage = "Bucket names must be unique."
                            amountCents == null || amountCents < 0L -> errorMessage = "Enter a valid allocation."
                            otherAllocatedCents + amountCents > portfolioBudgetCents ->
                                errorMessage = "Allocation exceeds the portfolio total."
                            else -> {
                                onSaveSettings(
                                    BucketDraft(
                                        bucketUuid = editor.bucketUuid,
                                        name = trimmedName,
                                        trackingMode = trackingMode,
                                        balanceBehavior = balanceBehavior,
                                        defaultAllocatedAmountCents = amountCents,
                                        sortOrder = allBuckets.firstOrNull { it.bucketUuid == editor.bucketUuid }?.sortOrder ?: 0,
                                        isPrimary = isPrimary,
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
private fun AddBucketForm(
    portfolioBudgetCents: Long,
    allocatedToBucketsCents: Long,
    existingBuckets: List<BudgetBucket>,
    bucketSummaries: List<BucketSummaryState>,
    onDismiss: () -> Unit,
    onCreateBucket: (BucketDraft) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("0.00") }
    var trackingMode by rememberSaveable { mutableStateOf(BucketTrackingMode.DAILY_TARGET) }
    var balanceBehavior by rememberSaveable { mutableStateOf(BucketBalanceBehavior.RETURN_TO_PORTFOLIO) }
    var makePrimary by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val remainingUnassignedCents = portfolioBudgetCents - allocatedToBucketsCents

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
                    text = "Unassigned planned: ${CurrencyFormatter.formatSigned(remainingUnassignedCents)}",
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.padding(end = 16.dp)) {
                        Text("Make primary", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "Primary opens first on Overview.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = makePrimary,
                        onCheckedChange = { makePrimary = it }
                    )
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
                                allocatedToBucketsCents + allocationCents > portfolioBudgetCents ->
                                    errorMessage = "Allocation exceeds the unassigned planned budget."
                                else -> {
                                    onCreateBucket(
                                        BucketDraft(
                                            bucketUuid = UUID.randomUUID().toString(),
                                            name = trimmedName,
                                            trackingMode = trackingMode,
                                            balanceBehavior = balanceBehavior,
                                            defaultAllocatedAmountCents = allocationCents,
                                            sortOrder = (existingBuckets.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                                            isPrimary = makePrimary || existingBuckets.none { !it.isClosed && it.isPrimary },
                                            closeRequested = false
                                        )
                                    )
                                    name = ""
                                    amountText = "0.00"
                                    trackingMode = BucketTrackingMode.DAILY_TARGET
                                    balanceBehavior = BucketBalanceBehavior.RETURN_TO_PORTFOLIO
                                    makePrimary = false
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

private fun buildExistingHomeBucketDrafts(
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
                isPrimary = bucket.isPrimary,
                closeRequested = bucket.isClosed
            )
        }
}

private fun buildHomeBucketDrafts(
    allBuckets: List<BudgetBucket>,
    bucketSummaries: List<BucketSummaryState>,
    newBucketDraft: BucketDraft
): List<BucketDraft> {
    val existingDrafts = buildExistingHomeBucketDrafts(
        allBuckets = allBuckets,
        bucketSummaries = bucketSummaries
    ).map { draft ->
        if (newBucketDraft.isPrimary) draft.copy(isPrimary = false) else draft
    }
    return existingDrafts + newBucketDraft
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
        when {
            draft.bucketUuid == updatedBucketDraft.bucketUuid -> updatedBucketDraft
            updatedBucketDraft.isPrimary -> draft.copy(isPrimary = false)
            else -> draft
        }
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
