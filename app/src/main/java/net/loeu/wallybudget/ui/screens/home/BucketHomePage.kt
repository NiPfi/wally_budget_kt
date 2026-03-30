@file:Suppress("TooManyFunctions", "MaxLineLength", "CyclomaticComplexMethod", "LongMethod")

package net.loeu.wallybudget.ui.screens.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import net.loeu.wallybudget.domain.model.BucketSummaryState
import net.loeu.wallybudget.domain.model.BucketTrackingMode
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.SelectedBucketOverview
import net.loeu.wallybudget.domain.model.SpendingForecast
import net.loeu.wallybudget.domain.model.displayDescription
import net.loeu.wallybudget.ui.CurrencyPlaceholderSamples
import net.loeu.wallybudget.ui.screens.expenses.ExpenseItem
import net.loeu.wallybudget.ui.screens.overview.AnimatedCounter
import net.loeu.wallybudget.ui.screens.overview.CollapsingSummaryLayout
import net.loeu.wallybudget.ui.screens.overview.CollapsingSummaryLayoutConfig
import net.loeu.wallybudget.ui.screens.overview.LoadingExpenseList
import net.loeu.wallybudget.ui.screens.overview.LoadingValuePlaceholder
import net.loeu.wallybudget.ui.screens.overview.LocalCollapsingHeaderIsForMeasurement
import net.loeu.wallybudget.ui.screens.overview.MergedSummaryHeaderSurface
import net.loeu.wallybudget.ui.screens.overview.OverviewPage
import net.loeu.wallybudget.ui.screens.overview.OverviewSectionBlock
import net.loeu.wallybudget.ui.screens.overview.PlaceholderShimmerProvider
import net.loeu.wallybudget.ui.screens.overview.rememberOverviewPageLayoutState
import net.loeu.wallybudget.ui.screens.overview.summaryCardColors
import net.loeu.wallybudget.util.CurrencyFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal val HomeFabSize = 56.dp
internal val HomeFabListClearance = 16.dp

@Composable
internal fun BucketHomePage(
    selectedBucketOverview: SelectedBucketOverview,
    spendingForecast: SpendingForecast?,
    effectiveCurrentDate: LocalDate,
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
                placeholderOverview.bucket.monthScoped &&
                placeholderBudgetState != null
            ) {
                MonthScopedBucketPage(
                    selectedBucketOverview = placeholderOverview,
                    canEditExpenses = false,
                    onEditExpense = {},
                    onNavigateToAnalysis = onNavigateToAnalysis,
                    showTopRightSettingsAction = showTopRightSettingsAction,
                    onNavigateToSettings = onNavigateToSettings,
                    modifier = modifier,
                    isLoading = true
                )
            } else if (
                placeholderOverview != null &&
                placeholderOverview.bucket.trackingMode == BucketTrackingMode.DAILY_TARGET &&
                placeholderBudgetState != null
            ) {
                OverviewPage(
                    modifier = modifier.fillMaxSize(),
                    budgetState = placeholderBudgetState,
                    effectiveCurrentDate = effectiveCurrentDate,
                    todayExpenses = emptyList(),
                    activeCycleExpenseSections = emptyList(),
                    spendingForecast = SpendingForecast(),
                    onEditTodayExpense = null,
                    isLoading = false,
                    isBodyLoading = true,
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

    if (selectedBucketOverview.bucket.monthScoped && selectedBucketOverview.budgetState != null) {
        MonthScopedBucketPage(
            selectedBucketOverview = selectedBucketOverview,
            canEditExpenses = canEditExpenses,
            onEditExpense = onEditExpense,
            onNavigateToAnalysis = onNavigateToAnalysis,
            showTopRightSettingsAction = showTopRightSettingsAction,
            onNavigateToSettings = onNavigateToSettings,
            modifier = modifier
        )
    } else if (selectedBucketOverview.budgetState != null) {
        OverviewPage(
            modifier = Modifier
                .then(modifier)
                .fillMaxSize(),
            budgetState = selectedBucketOverview.budgetState,
            effectiveCurrentDate = effectiveCurrentDate,
            todayExpenses = selectedBucketOverview.todayExpenses,
            activeCycleExpenseSections = selectedBucketOverview.activeCycleExpenseSections,
            spendingForecast = spendingForecast ?: SpendingForecast(),
            onEditTodayExpense = if (canEditExpenses) {
                { expense -> onEditExpense(expense) }
            } else {
                null
            },
            isLoading = isLoadingData,
            isBodyLoading = isLoadingData || spendingForecast == null,
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
                    OverviewSectionBlock {
                        ReserveDetailsSection(selectedBucketOverview = selectedBucketOverview)
                    }
                }
                item {
                    OverviewSectionBlock {
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
                rows = listOf(
                    CurrencyPlaceholderSamples.amount(888_800L),
                    CurrencyPlaceholderSamples.amount(88_800L),
                    CurrencyPlaceholderSamples.amount(8_800L)
                )
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
internal fun TopSummaryCard(
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
    onEditExpense: (Expense) -> Unit,
    isLoading: Boolean = false
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeading("Cycle expenses")
        if (isLoading) {
            LoadingExpenseList()
        } else if (selectedBucketOverview.activeCycleExpenseSections.isEmpty()) {
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
private fun MonthScopedBucketPage(
    selectedBucketOverview: SelectedBucketOverview,
    canEditExpenses: Boolean,
    onEditExpense: (Expense) -> Unit,
    onNavigateToAnalysis: (() -> Unit)?,
    showTopRightSettingsAction: Boolean,
    onNavigateToSettings: (() -> Unit)?,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false
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
            MonthScopedSummaryCard(
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
                OverviewSectionBlock {
                    CycleBudgetProgressSection(selectedBucketOverview = selectedBucketOverview)
                }
            }
            item {
                OverviewSectionBlock {
                    ReserveExpensesSection(
                        selectedBucketOverview = selectedBucketOverview,
                        canEditExpenses = canEditExpenses,
                        onEditExpense = onEditExpense,
                        isLoading = isLoading
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthScopedSummaryCard(
    selectedBucketOverview: SelectedBucketOverview,
    collapseProgress: Float,
    onNavigateToAnalysis: (() -> Unit)?,
    onNavigateToSettings: (() -> Unit)?
) {
    val showTestTags = !LocalCollapsingHeaderIsForMeasurement.current
    val summary = selectedBucketOverview.summary
    val budgetState = selectedBucketOverview.budgetState
    val useWarningTint = summary.remainingThisCycleCents < 0L || summary.overspentCents > 0L

    TopSummaryCard(
        title = selectedBucketOverview.bucket.name,
        amountText = CurrencyFormatter.formatSigned(summary.remainingThisCycleCents),
        subtitleText = "CYCLE LEFT",
        collapseProgress = collapseProgress,
        useWarningTint = useWarningTint,
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
            SummaryMetricColumn(
                label = "Days left",
                value = budgetState?.daysRemainingInCycle?.toString() ?: "—",
                contentColor = contentColor
            )
        }
    }
}

// ── Month-scoped cycle progress data ────────────────────────────────────

private val SEGMENT_COLORS = listOf(
    Color(0xFF5B8FCC), Color(0xFF5AAD8A), Color(0xFFD98B4F), Color(0xFF9470BC),
    Color(0xFF4AADBD), Color(0xFFCD7D6A), Color(0xFF7BAA52), Color(0xFFCC6E93),
)

private data class BarSegment(val startX: Float, val endX: Float)

private fun computeBarSegments(
    expenses: List<Expense>,
    scaleCents: Long,
    barWidth: Float,
    gapPx: Float
): List<BarSegment> {
    if (scaleCents <= 0L || expenses.isEmpty()) return emptyList()
    val totalGapPx = gapPx * (expenses.size - 1).coerceAtLeast(0)
    val usableWidth = (barWidth - totalGapPx).coerceAtLeast(0f)
    var curX = 0f
    return expenses.map { expense ->
        val segWidth = (expense.amountCents.toFloat() / scaleCents * usableWidth).coerceAtLeast(0f)
        val seg = BarSegment(startX = curX, endX = (curX + segWidth).coerceAtMost(barWidth))
        curX = seg.endX + gapPx
        seg
    }
}

private data class CycleBudgetData(
    val allocatedCents: Long,
    val spentCents: Long,
    val remainingCents: Long,
    val overspentCents: Long,
    val spentFraction: Float,
    val isOverBudget: Boolean,
    val expenses: List<Expense>,
)

@Composable
private fun rememberCycleBudgetData(selectedBucketOverview: SelectedBucketOverview): CycleBudgetData? {
    val summary = selectedBucketOverview.summary
    val budgetState = selectedBucketOverview.budgetState ?: return null
    val expenseSections = selectedBucketOverview.activeCycleExpenseSections

    return remember(summary, budgetState, expenseSections) {
        val allocated = summary.allocatedThisCycleCents
        val spent = summary.spentThisCycleCents
        val spentFraction = if (allocated > 0L) {
            (spent.toFloat() / allocated.toFloat()).coerceIn(0f, 1.5f)
        } else {
            0f
        }
        CycleBudgetData(
            allocatedCents = allocated,
            spentCents = spent,
            remainingCents = summary.remainingThisCycleCents,
            overspentCents = summary.overspentCents,
            spentFraction = spentFraction,
            isOverBudget = spent > allocated,
            expenses = expenseSections.flatMap { it.expenses },
        )
    }
}

// ── Budget usage progress section ───────────────────────────────────────

@Composable
private fun CycleBudgetProgressSection(
    selectedBucketOverview: SelectedBucketOverview
) {
    val budgetData = rememberCycleBudgetData(selectedBucketOverview)
    val summary = selectedBucketOverview.summary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Budget usage",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        if (budgetData != null) {
            CycleBudgetProgressBar(
                expenses = budgetData.expenses,
                allocatedCents = budgetData.allocatedCents,
                isOverBudget = budgetData.isOverBudget,
                modifier = Modifier.fillMaxWidth()
            )
            CycleBudgetProgressLabels(
                spentCents = budgetData.spentCents,
                remainingCents = budgetData.remainingCents,
                allocatedCents = budgetData.allocatedCents,
                isOverBudget = budgetData.isOverBudget
            )
        } else {
            CycleBudgetProgressFallback(summary = summary)
        }
    }
}


@Composable
private fun CycleBudgetProgressBar(
    expenses: List<Expense>,
    allocatedCents: Long,
    isOverBudget: Boolean,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val overflowColor = MaterialTheme.colorScheme.error.copy(alpha = 0.30f)
    val tickColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val scaleCents = remember(expenses, allocatedCents) {
        val total = expenses.sumOf { it.amountCents }
        if (total > allocatedCents) total else allocatedCents
    }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(expenses, scaleCents) {
                    detectTapGestures { offset ->
                        val gapPx = 2.dp.toPx()
                        val layout = computeBarSegments(expenses, scaleCents, size.width.toFloat(), gapPx)
                        val hit = layout.indexOfFirst { offset.x in it.startX..it.endX }
                        selectedIndex = if (hit >= 0 && hit != selectedIndex) hit else null
                    }
                }
        ) {
            val gapPx = 2.dp.toPx()
            val budgetX = allocatedCents.toFloat() / scaleCents * size.width
            val layout = computeBarSegments(expenses, scaleCents, size.width, gapPx)
            drawSegmentedBar(layout, trackColor, overflowColor, tickColor, budgetX, selectedIndex, isOverBudget)
        }
        val selected = selectedIndex?.let { expenses.getOrNull(it) }
        val labelColor = selectedIndex?.let { SEGMENT_COLORS[it % SEGMENT_COLORS.size] } ?: Color.Transparent
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (selected != null) Modifier.clickable { selectedIndex = null } else Modifier)
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selected?.displayDescription ?: "",
                style = MaterialTheme.typography.labelMedium,
                color = labelColor,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(
                text = selected?.let { CurrencyFormatter.format(it.amountCents) } ?: "",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = labelColor
            )
        }
    }
}

private fun DrawScope.drawSegmentedBar(
    layout: List<BarSegment>,
    trackColor: Color,
    overflowColor: Color,
    tickColor: Color,
    budgetX: Float,
    selectedIndex: Int?,
    isOverBudget: Boolean
) {
    val barHeight = 14.dp.toPx()
    val barTop = (size.height - barHeight) / 2f
    val cornerR = barHeight / 2f
    val barPath = Path().apply {
        addRoundRect(RoundRect(left = 0f, top = barTop, right = size.width, bottom = barTop + barHeight,
            cornerRadius = CornerRadius(cornerR)))
    }
    clipPath(barPath) {
        drawRect(color = trackColor, topLeft = Offset(0f, barTop), size = Size(size.width, barHeight))
        layout.forEachIndexed { index, seg ->
            val alpha = if (selectedIndex == null || index == selectedIndex) 1f else 0.55f
            val color = SEGMENT_COLORS[index % SEGMENT_COLORS.size].copy(alpha = alpha)
            drawRect(color = color, topLeft = Offset(seg.startX, barTop), size = Size(seg.endX - seg.startX, barHeight))
        }
        if (isOverBudget) {
            drawRect(color = overflowColor, topLeft = Offset(budgetX, barTop),
                size = Size(size.width - budgetX, barHeight))
        }
    }
    // Budget boundary tick — drawn outside clip so it extends above/below the bar
    if (isOverBudget) {
        val tickPad = 6.dp.toPx()
        drawLine(color = tickColor, start = Offset(budgetX, barTop - tickPad),
            end = Offset(budgetX, barTop + barHeight + tickPad),
            strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
    }
}


@Composable
private fun CycleBudgetProgressLabels(
    spentCents: Long,
    remainingCents: Long,
    allocatedCents: Long,
    isOverBudget: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Spent",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AnimatedCounter(
                amountCents = spentCents,
                textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isOverBudget) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                animate = true,
                textAlign = TextAlign.Start
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Remaining",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AnimatedCounter(
                amountCents = remainingCents,
                signed = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isOverBudget) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                animate = true,
                textAlign = TextAlign.Center
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "Budget",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AnimatedCounter(
                amountCents = allocatedCents,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                animate = true,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun CycleBudgetProgressFallback(summary: BucketSummaryState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PlainMetricRow("Allocated", CurrencyFormatter.format(summary.allocatedThisCycleCents))
        PlainMetricRow("Spent", CurrencyFormatter.format(summary.spentThisCycleCents))
        PlainMetricRow("Remaining", CurrencyFormatter.formatSigned(summary.remainingThisCycleCents))
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
