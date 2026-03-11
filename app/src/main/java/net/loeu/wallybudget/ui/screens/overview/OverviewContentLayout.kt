package net.loeu.wallybudget.ui.screens.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.SubcomposeMeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.ExpenseDaySection
import net.loeu.wallybudget.domain.model.SpendingForecast
import kotlin.math.roundToInt

internal data class OverviewContentState(
    val budgetState: BudgetState,
    val todayExpenses: List<Expense>,
    val activeCycleExpenseSections: List<ExpenseDaySection>,
    val spendingForecast: SpendingForecast,
    val onEditTodayExpense: ((Expense) -> Unit)?,
    val isLoading: Boolean,
    val onNavigateToSettings: (() -> Unit)?,
    val availableRecoverableOverspendCents: Long,
    val useWarningTint: Boolean
)

internal data class OverviewContentConfig(
    val modifier: Modifier = Modifier,
    val showSpendingDetailsSection: Boolean,
    val showTodayExpensesSection: Boolean,
    val enableHeaderCollapse: Boolean,
    val bottomContentPadding: Dp,
    val density: Density,
    val headerHorizontalPadding: Dp,
    val headerTopPadding: Dp,
    val headerBottomSpacing: Dp
)

@Composable
internal fun OverviewContentLayout(
    contentState: OverviewContentState,
    layoutState: OverviewPageState,
    config: OverviewContentConfig,
    onShowForecastDetails: () -> Unit,
    onShowSafeTodayDetails: () -> Unit
) {
    SubcomposeLayout(
        modifier = config.modifier
            .nestedScroll(layoutState.nestedScrollConnection)
            .clipToBounds()
    ) { constraints ->
        val headerMetrics = measureHeaderMetrics(
            constraints = constraints,
            density = config.density,
            headerHorizontalPadding = config.headerHorizontalPadding,
            headerTopPadding = config.headerTopPadding,
            headerBottomSpacing = config.headerBottomSpacing,
            budgetState = contentState.budgetState,
            availableRecoverableOverspendCents = contentState.availableRecoverableOverspendCents,
            isLoading = contentState.isLoading,
            useWarningTint = contentState.useWarningTint,
            onShowSafeTodayDetails = onShowSafeTodayDetails,
            onNavigateToSettings = contentState.onNavigateToSettings
        )
        val maxCollapsePx = if (config.enableHeaderCollapse) {
            (headerMetrics.expandedHeaderHeightPx - headerMetrics.collapsedHeaderHeightPx).coerceAtLeast(0).toFloat()
        } else 0f
        layoutState.maxCollapseRangePx.value = maxCollapsePx
        val clampedCollapseOffsetPx = layoutState.collapseOffsetPx.value.coerceIn(0f, maxCollapsePx)
        val collapseProgress =
            if (maxCollapsePx == 0f) 0f else (clampedCollapseOffsetPx / maxCollapsePx).coerceIn(0f, 1f)
        val contentPlaceables = measureOverviewBodyPlaceables(
            constraints = constraints,
            budgetState = contentState.budgetState,
            todayExpenses = contentState.todayExpenses,
            activeCycleExpenseSections = contentState.activeCycleExpenseSections,
            spendingForecast = contentState.spendingForecast,
            onEditTodayExpense = contentState.onEditTodayExpense,
            listState = layoutState.listState,
            density = config.density,
            topContentPaddingPx = headerMetrics.topContentPaddingPx,
            bottomContentPadding = config.bottomContentPadding,
            isLoading = contentState.isLoading,
            onShowForecastDetails = onShowForecastDetails,
            showSpendingDetailsSection = config.showSpendingDetailsSection,
            showTodayExpensesSection = config.showTodayExpensesSection
        )
        val headerPlaceables = measureOverviewHeaderPlaceables(
            headerConstraints = headerMetrics.headerConstraints,
            budgetState = contentState.budgetState,
            availableRecoverableOverspendCents = contentState.availableRecoverableOverspendCents,
            collapseProgress = collapseProgress,
            isLoading = contentState.isLoading,
            useWarningTint = contentState.useWarningTint,
            onShowSafeTodayDetails = onShowSafeTodayDetails,
            onNavigateToSettings = contentState.onNavigateToSettings
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeOverviewContent(contentPlaceables, headerPlaceables, clampedCollapseOffsetPx, headerMetrics)
        }
    }
}

private fun Placeable.PlacementScope.placeOverviewContent(
    contentPlaceables: List<Placeable>,
    headerPlaceables: List<Placeable>,
    clampedCollapseOffsetPx: Float,
    headerMetrics: OverviewHeaderMetrics
) {
    val contentOffsetY = -clampedCollapseOffsetPx.roundToInt()
    contentPlaceables.forEach { it.placeRelative(0, contentOffsetY) }
    headerPlaceables.forEach {
        it.placeRelative(headerMetrics.horizontalPaddingPx, headerMetrics.topPaddingPx)
    }
}

private fun SubcomposeMeasureScope.measureOverviewBodyPlaceables(
    constraints: Constraints,
    budgetState: BudgetState,
    todayExpenses: List<Expense>,
    activeCycleExpenseSections: List<ExpenseDaySection>,
    spendingForecast: SpendingForecast,
    onEditTodayExpense: ((Expense) -> Unit)?,
    listState: LazyListState,
    density: Density,
    topContentPaddingPx: Int,
    bottomContentPadding: Dp,
    isLoading: Boolean,
    onShowForecastDetails: () -> Unit,
    showSpendingDetailsSection: Boolean,
    showTodayExpensesSection: Boolean
) = subcompose("content") {
    OverviewBodyContent(
        budgetState = budgetState,
        todayExpenses = todayExpenses,
        activeCycleExpenseSections = activeCycleExpenseSections,
        spendingForecast = spendingForecast,
        onEditTodayExpense = onEditTodayExpense,
        listState = listState,
        density = density,
        topContentPaddingPx = topContentPaddingPx,
        bottomContentPadding = bottomContentPadding,
        isLoading = isLoading,
        onShowForecastDetails = onShowForecastDetails,
        showSpendingDetailsSection = showSpendingDetailsSection,
        showTodayExpensesSection = showTodayExpensesSection
    )
}.map { it.measure(constraints) }

private fun SubcomposeMeasureScope.measureOverviewHeaderPlaceables(
    headerConstraints: Constraints,
    budgetState: BudgetState,
    availableRecoverableOverspendCents: Long,
    collapseProgress: Float,
    isLoading: Boolean,
    useWarningTint: Boolean,
    onShowSafeTodayDetails: () -> Unit,
    onNavigateToSettings: (() -> Unit)?
) = subcompose("currentHeader") {
    CurrentSummaryHeader(
        budgetState = budgetState,
        availableRecoverableOverspendCents = availableRecoverableOverspendCents,
        collapseProgress = collapseProgress,
        isLoading = isLoading,
        useWarningTint = useWarningTint,
        onShowSafeTodayDetails = onShowSafeTodayDetails,
        onNavigateToSettings = onNavigateToSettings
    )
}.map { it.measure(headerConstraints) }

private data class OverviewHeaderMetrics(
    val horizontalPaddingPx: Int,
    val topPaddingPx: Int,
    val headerConstraints: Constraints,
    val expandedHeaderHeightPx: Int,
    val collapsedHeaderHeightPx: Int,
    val topContentPaddingPx: Int
)

private fun SubcomposeMeasureScope.measureHeaderMetrics(
    constraints: Constraints,
    density: Density,
    headerHorizontalPadding: Dp,
    headerTopPadding: Dp,
    headerBottomSpacing: Dp,
    budgetState: BudgetState,
    availableRecoverableOverspendCents: Long,
    isLoading: Boolean,
    useWarningTint: Boolean,
    onShowSafeTodayDetails: () -> Unit,
    onNavigateToSettings: (() -> Unit)?
): OverviewHeaderMetrics {
    val horizontalPaddingPx = with(density) { headerHorizontalPadding.roundToPx() }
    val topPaddingPx = with(density) { headerTopPadding.roundToPx() }
    val bottomSpacingPx = with(density) { headerBottomSpacing.roundToPx() }
    val headerConstraints = constraints.copy(
        minWidth = 0,
        minHeight = 0,
        maxWidth = (constraints.maxWidth - (horizontalPaddingPx * 2)).coerceAtLeast(0)
    )
    val expandedHeaderHeightPx = measureSummaryCardHeight(
        slotId = "expandedHeaderMeasure",
        collapseProgress = 0f,
        budgetState = budgetState,
        recoverableOverspendCents = availableRecoverableOverspendCents,
        isLoading = isLoading,
        useWarningTint = useWarningTint,
        onSafeTodayInfoClick = onShowSafeTodayDetails,
        onNavigateToSettings = onNavigateToSettings,
        constraints = headerConstraints
    )
    val collapsedHeaderHeightPx = measureSummaryCardHeight(
        slotId = "collapsedHeaderMeasure",
        collapseProgress = 1f,
        budgetState = budgetState,
        recoverableOverspendCents = availableRecoverableOverspendCents,
        isLoading = isLoading,
        useWarningTint = useWarningTint,
        onSafeTodayInfoClick = onShowSafeTodayDetails,
        onNavigateToSettings = onNavigateToSettings,
        constraints = headerConstraints,
        fallbackHeight = expandedHeaderHeightPx
    )
    return OverviewHeaderMetrics(
        horizontalPaddingPx = horizontalPaddingPx,
        topPaddingPx = topPaddingPx,
        headerConstraints = headerConstraints,
        expandedHeaderHeightPx = expandedHeaderHeightPx,
        collapsedHeaderHeightPx = collapsedHeaderHeightPx,
        topContentPaddingPx = expandedHeaderHeightPx + topPaddingPx + bottomSpacingPx
    )
}

@Composable
private fun CurrentSummaryHeader(
    budgetState: BudgetState,
    availableRecoverableOverspendCents: Long,
    collapseProgress: Float,
    isLoading: Boolean,
    useWarningTint: Boolean,
    onShowSafeTodayDetails: () -> Unit,
    onNavigateToSettings: (() -> Unit)?
) {
    SummaryCard(
        budgetState = budgetState,
        recoverableOverspendCents = availableRecoverableOverspendCents,
        collapseProgress = collapseProgress,
        isLoading = isLoading,
        animateCounters = true,
        useWarningTint = useWarningTint,
        tagSecondaryMetrics = true,
        onSafeTodayInfoClick = onShowSafeTodayDetails,
        onNavigateToSettings = onNavigateToSettings,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_summary_section")
    )
}

@Composable
private fun OverviewBodyContent(
    budgetState: BudgetState,
    todayExpenses: List<Expense>,
    activeCycleExpenseSections: List<ExpenseDaySection>,
    spendingForecast: SpendingForecast,
    onEditTodayExpense: ((Expense) -> Unit)?,
    listState: LazyListState,
    density: Density,
    topContentPaddingPx: Int,
    bottomContentPadding: Dp,
    isLoading: Boolean,
    onShowForecastDetails: () -> Unit,
    showSpendingDetailsSection: Boolean,
    showTodayExpensesSection: Boolean
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(
            top = with(density) { topContentPaddingPx.toDp() },
            bottom = bottomContentPadding
        ),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            OverviewSectionBlock(
                modifier = Modifier.testTag("home_forecast_section")
            ) {
                ForecastCard(
                    spendingForecast = spendingForecast,
                    budgetState = budgetState,
                    isLoading = isLoading,
                    onClick = onShowForecastDetails
                )
            }
        }
        if (showSpendingDetailsSection) {
            item {
                OverviewSectionBlock {
                    SpendingTodayPane(
                        budgetState = budgetState,
                        todayExpenses = todayExpenses,
                        activeCycleExpenseSections = activeCycleExpenseSections,
                        isLoading = isLoading,
                        onEditTodayExpense = onEditTodayExpense,
                        showTodayExpensesSection = showTodayExpensesSection
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewSectionBlock(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        content = content
    )
}

private fun SubcomposeMeasureScope.measureSummaryCardHeight(
    slotId: String,
    collapseProgress: Float,
    budgetState: BudgetState,
    recoverableOverspendCents: Long,
    isLoading: Boolean,
    useWarningTint: Boolean,
    onSafeTodayInfoClick: () -> Unit,
    onNavigateToSettings: (() -> Unit)?,
    constraints: Constraints,
    fallbackHeight: Int = 0
): Int {
    return subcompose(slotId) {
        SummaryCard(
            budgetState = budgetState,
            recoverableOverspendCents = recoverableOverspendCents,
            collapseProgress = collapseProgress,
            isLoading = isLoading,
            animateCounters = false,
            useWarningTint = useWarningTint,
            onSafeTodayInfoClick = onSafeTodayInfoClick,
            onNavigateToSettings = onNavigateToSettings,
            modifier = Modifier.fillMaxWidth()
        )
    }.maxOfOrNull { it.measure(constraints).height } ?: fallbackHeight
}
