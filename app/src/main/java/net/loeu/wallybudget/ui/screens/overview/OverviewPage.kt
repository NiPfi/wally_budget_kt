package net.loeu.wallybudget.ui.screens.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.loeu.wallybudget.R
import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.ExpenseDaySection
import net.loeu.wallybudget.domain.model.SpendingForecast
import net.loeu.wallybudget.domain.config.ForecastConfig
import net.loeu.wallybudget.ui.calculateAvailableRecoverableOverspendCentsFromForecast
import net.loeu.wallybudget.ui.calculateSafeToSpendNowCents
import net.loeu.wallybudget.util.CurrencyFormatter
import kotlin.math.roundToInt

private class CollapseRangeHolder(var value: Float = 0f)

@Composable
fun OverviewPage(
    budgetState: BudgetState,
    todayExpenses: List<Expense>,
    activeCycleExpenseSections: List<ExpenseDaySection>,
    spendingForecast: SpendingForecast,
    onEditTodayExpense: ((Expense) -> Unit)?,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    onNavigateToSettings: (() -> Unit)? = null,
    showSpendingDetailsSection: Boolean = true,
    showTodayExpensesSection: Boolean = true,
    enableHeaderCollapse: Boolean = true,
    defaultCollapsedHeader: Boolean = false,
    bottomContentPadding: Dp = 24.dp
) {
    val headerHorizontalPadding = 12.dp
    val headerTopPadding = if (defaultCollapsedHeader) 0.dp else 8.dp
    val headerBottomSpacing = 10.dp
    val availableRecoverableOverspendCents = calculateAvailableRecoverableOverspendCentsFromForecast(
        remainingTodayCents = budgetState.remainingTodayCents,
        forecast = spendingForecast
    )
    val safeToSpendTodayCents = calculateSafeToSpendNowCents(
        remainingTodayCents = budgetState.remainingTodayCents,
        availableRecoverableOverspendCents = availableRecoverableOverspendCents
    )
    val useWarningTint = budgetState.remainingTodayCents < 0L ||
        budgetState.remainingCycleCents < 0L ||
        spendingForecast.isProjectedOverBudget
    val showForecastDetails = remember { mutableStateOf(false) }
    val showSafeTodayDetails = remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val defaultCollapseOffsetPx = remember(defaultCollapsedHeader, density) {
        if (defaultCollapsedHeader) {
            with(density) { 64.dp.toPx() }
        } else {
            0f
        }
    }
    var collapseOffsetPx by remember(defaultCollapsedHeader, density) {
        mutableFloatStateOf(defaultCollapseOffsetPx)
    }
    val maxCollapseRangePx = remember { CollapseRangeHolder() }
    val nestedScrollConnection = rememberOverviewNestedScrollConnection(
        listStateFirstVisibleItemIndex = listState.firstVisibleItemIndex,
        listStateFirstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
        enableHeaderCollapse = enableHeaderCollapse,
        collapseOffsetPx = collapseOffsetPx,
        setCollapseOffsetPx = { collapseOffsetPx = it },
        maxCollapsePx = maxCollapseRangePx.value
    )

    SideEffect {
        val normalizedCollapseOffsetPx = collapseOffsetPx.coerceIn(0f, maxCollapseRangePx.value)
        if (normalizedCollapseOffsetPx != collapseOffsetPx) {
            collapseOffsetPx = normalizedCollapseOffsetPx
        }
    }

    OverviewInfoDialogs(
        showForecastDetails = showForecastDetails.value && !isLoading,
        onDismissForecastDetails = { showForecastDetails.value = false },
        showSafeTodayDetails = showSafeTodayDetails.value && !isLoading,
        onDismissSafeTodayDetails = { showSafeTodayDetails.value = false },
        spendingForecast = spendingForecast,
        budgetState = budgetState,
        availableRecoverableOverspendCents = availableRecoverableOverspendCents,
        safeToSpendTodayCents = safeToSpendTodayCents
    )

    OverviewContentLayout(
        budgetState = budgetState,
        todayExpenses = todayExpenses,
        activeCycleExpenseSections = activeCycleExpenseSections,
        spendingForecast = spendingForecast,
        onEditTodayExpense = onEditTodayExpense,
        modifier = modifier,
        isLoading = isLoading,
        onNavigateToSettings = onNavigateToSettings,
        showSpendingDetailsSection = showSpendingDetailsSection,
        showTodayExpensesSection = showTodayExpensesSection,
        enableHeaderCollapse = enableHeaderCollapse,
        bottomContentPadding = bottomContentPadding,
        availableRecoverableOverspendCents = availableRecoverableOverspendCents,
        useWarningTint = useWarningTint,
        density = density,
        headerHorizontalPadding = headerHorizontalPadding,
        headerTopPadding = headerTopPadding,
        headerBottomSpacing = headerBottomSpacing,
        listState = listState,
        collapseOffsetPx = collapseOffsetPx,
        setCollapseOffsetPx = { collapseOffsetPx = it },
        maxCollapseRangePx = maxCollapseRangePx,
        nestedScrollConnection = nestedScrollConnection,
        onShowForecastDetails = { showForecastDetails.value = true },
        onShowSafeTodayDetails = { showSafeTodayDetails.value = true }
    )
}

@Composable
private fun OverviewInfoDialogs(
    showForecastDetails: Boolean,
    onDismissForecastDetails: () -> Unit,
    showSafeTodayDetails: Boolean,
    onDismissSafeTodayDetails: () -> Unit,
    spendingForecast: SpendingForecast,
    budgetState: BudgetState,
    availableRecoverableOverspendCents: Long,
    safeToSpendTodayCents: Long
) {
    if (showForecastDetails) {
        ForecastDetailsDialog(
            spendingForecast = spendingForecast,
            onDismiss = onDismissForecastDetails
        )
    }

    if (showSafeTodayDetails) {
        SafeTodayDetailsDialog(
            budgetState = budgetState,
            availableRecoverableOverspendCents = availableRecoverableOverspendCents,
            safeToSpendTodayCents = safeToSpendTodayCents,
            onDismiss = onDismissSafeTodayDetails
        )
    }
}

@Composable
private fun ForecastDetailsDialog(
    spendingForecast: SpendingForecast,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Forecast analysis") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "This projection combines your recent spending pace with prior " +
                        "cycle behavior to estimate where you may finish by cycle end.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Conservative shows a lower-spend path, Projected is the current " +
                        "best estimate, and High pace is a stress case for how the " +
                        "cycle could finish if spending speeds up.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DetailRow(
                            "Confidence",
                            "${(spendingForecast.confidenceScore * 100).toInt()}% " +
                                "(${spendingForecast.confidenceRating})"
                        )
                        DetailRow(
                            "Average daily pace",
                            CurrencyFormatter.format(spendingForecast.dailyAverageWeightedCents)
                        )
                        DetailRow("Window", "${spendingForecast.usedDataPoints} days")
                        DetailRow("Trend", forecastTrendText(spendingForecast.trendSlopeCents))
                        if (spendingForecast.detectedOutlierCount > 0) {
                            DetailRow(
                                "Filtered anomalies",
                                spendingForecast.detectedOutlierCount.toString()
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        icon = {
            androidx.compose.material3.Icon(
                painter = painterResource(R.drawable.ic_info),
                contentDescription = null
            )
        }
    )
}

@Composable
private fun SafeTodayDetailsDialog(
    budgetState: BudgetState,
    availableRecoverableOverspendCents: Long,
    safeToSpendTodayCents: Long,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Safe today") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "The + amount next to Today left is recoverable overspend: extra " +
                        "spending the forecast suggests you can still absorb and make " +
                        "back over the remaining cycle days.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "It is not free budget. It sits on top of today’s allowance and " +
                        "depends on forecast confidence and days left in the cycle.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DetailRow(
                            "Today left",
                            CurrencyFormatter.formatSigned(budgetState.remainingTodayCents)
                        )
                        DetailRow(
                            "Recoverable overspend",
                            CurrencyFormatter.format(availableRecoverableOverspendCents)
                        )
                        DetailRow(
                            "Safe to spend now",
                            CurrencyFormatter.format(safeToSpendTodayCents)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        icon = {
            androidx.compose.material3.Icon(
                painter = painterResource(R.drawable.ic_info),
                contentDescription = null
            )
        }
    )
}

@Composable
private fun OverviewContentLayout(
    budgetState: BudgetState,
    todayExpenses: List<Expense>,
    activeCycleExpenseSections: List<ExpenseDaySection>,
    spendingForecast: SpendingForecast,
    onEditTodayExpense: ((Expense) -> Unit)?,
    modifier: Modifier,
    isLoading: Boolean,
    onNavigateToSettings: (() -> Unit)?,
    showSpendingDetailsSection: Boolean,
    showTodayExpensesSection: Boolean,
    enableHeaderCollapse: Boolean,
    bottomContentPadding: Dp,
    availableRecoverableOverspendCents: Long,
    useWarningTint: Boolean,
    density: androidx.compose.ui.unit.Density,
    headerHorizontalPadding: Dp,
    headerTopPadding: Dp,
    headerBottomSpacing: Dp,
    listState: androidx.compose.foundation.lazy.LazyListState,
    collapseOffsetPx: Float,
    setCollapseOffsetPx: (Float) -> Unit,
    maxCollapseRangePx: CollapseRangeHolder,
    nestedScrollConnection: NestedScrollConnection,
    onShowForecastDetails: () -> Unit,
    onShowSafeTodayDetails: () -> Unit
) {
    SubcomposeLayout(
        modifier = modifier
            .nestedScroll(nestedScrollConnection)
            .clipToBounds()
    ) { constraints ->
        val horizontalPaddingPx = with(density) { headerHorizontalPadding.roundToPx() }
        val topPaddingPx = with(density) { headerTopPadding.roundToPx() }
        val bottomSpacingPx = with(density) { headerBottomSpacing.roundToPx() }
        val headerConstraints = constraints.copy(
            minWidth = 0,
            minHeight = 0,
            maxWidth = (constraints.maxWidth - (horizontalPaddingPx * 2)).coerceAtLeast(0)
        )

        val expandedHeaderHeightPx = subcompose("expandedHeaderMeasure") {
            SummaryCard(
                budgetState = budgetState,
                recoverableOverspendCents = availableRecoverableOverspendCents,
                collapseProgress = 0f,
                isLoading = isLoading,
                animateCounters = false,
                useWarningTint = useWarningTint,
                onSafeTodayInfoClick = onShowSafeTodayDetails,
                onNavigateToSettings = onNavigateToSettings,
                modifier = Modifier.fillMaxWidth()
            )
        }.maxOfOrNull { it.measure(headerConstraints).height } ?: 0

        val collapsedHeaderHeightPx = subcompose("collapsedHeaderMeasure") {
            SummaryCard(
                budgetState = budgetState,
                recoverableOverspendCents = availableRecoverableOverspendCents,
                collapseProgress = 1f,
                isLoading = isLoading,
                animateCounters = false,
                useWarningTint = useWarningTint,
                onSafeTodayInfoClick = onShowSafeTodayDetails,
                onNavigateToSettings = onNavigateToSettings,
                modifier = Modifier.fillMaxWidth()
            )
        }.maxOfOrNull { it.measure(headerConstraints).height } ?: expandedHeaderHeightPx

        val maxCollapsePx = if (enableHeaderCollapse) {
            (expandedHeaderHeightPx - collapsedHeaderHeightPx).coerceAtLeast(0).toFloat()
        } else {
            0f
        }
        maxCollapseRangePx.value = maxCollapsePx
        val clampedCollapseOffsetPx = collapseOffsetPx.coerceIn(0f, maxCollapsePx)
        if (clampedCollapseOffsetPx != collapseOffsetPx) {
            setCollapseOffsetPx(clampedCollapseOffsetPx)
        }
        val collapseProgress = if (maxCollapsePx == 0f) {
            0f
        } else {
            (clampedCollapseOffsetPx / maxCollapsePx).coerceIn(0f, 1f)
        }

        val contentPlaceables = subcompose("content") {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(
                    top = with(density) {
                        (expandedHeaderHeightPx + topPaddingPx + bottomSpacingPx).toDp()
                    },
                    bottom = bottomContentPadding
                ),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                item {
                    SectionBlock(
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
                        SectionBlock {
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
        }.map { it.measure(constraints) }

        val headerPlaceables = subcompose("currentHeader") {
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
        }.map { it.measure(headerConstraints) }

        layout(constraints.maxWidth, constraints.maxHeight) {
            val contentOffsetY = -clampedCollapseOffsetPx.roundToInt()
            contentPlaceables.forEach { it.placeRelative(0, contentOffsetY) }
            headerPlaceables.forEach { it.placeRelative(horizontalPaddingPx, topPaddingPx) }
        }
    }
}

@Composable
private fun rememberOverviewNestedScrollConnection(
    listStateFirstVisibleItemIndex: Int,
    listStateFirstVisibleItemScrollOffset: Int,
    enableHeaderCollapse: Boolean,
    collapseOffsetPx: Float,
    setCollapseOffsetPx: (Float) -> Unit,
    maxCollapsePx: Float
): NestedScrollConnection {
    val canExpand = listStateFirstVisibleItemIndex == 0 &&
        listStateFirstVisibleItemScrollOffset == 0
    return remember(
        listStateFirstVisibleItemIndex,
        listStateFirstVisibleItemScrollOffset,
        enableHeaderCollapse,
        collapseOffsetPx,
        maxCollapsePx
    ) {
        object : NestedScrollConnection {
            @Suppress("UNUSED_PARAMETER")
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!enableHeaderCollapse) return Offset.Zero
                return consumeHeaderScroll(
                    availableY = available.y,
                    collapseOffsetPx = collapseOffsetPx,
                    setCollapseOffsetPx = setCollapseOffsetPx,
                    maxCollapsePx = maxCollapsePx,
                    canExpand = canExpand
                )
            }

            @Suppress("UNUSED_PARAMETER")
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (!enableHeaderCollapse) return Offset.Zero
                return consumeHeaderScroll(
                    availableY = available.y,
                    collapseOffsetPx = collapseOffsetPx,
                    setCollapseOffsetPx = setCollapseOffsetPx,
                    maxCollapsePx = maxCollapsePx,
                    canExpand = canExpand
                )
            }

            @Suppress("UNUSED_PARAMETER", "SameReturnValue")
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (!enableHeaderCollapse) return Velocity.Zero
                setCollapseOffsetPx(
                    snapHeaderOffset(
                        collapseOffsetPx.coerceIn(0f, maxCollapsePx),
                        maxCollapsePx
                    )
                )
                return Velocity.Zero
            }
        }
    }
}

private fun forecastTrendText(trendSlopeCents: Double): String = when {
    trendSlopeCents > ForecastConfig.TREND_SIGNIFICANCE_THRESHOLD_CENTS -> "Increasing"
    trendSlopeCents < -ForecastConfig.TREND_SIGNIFICANCE_THRESHOLD_CENTS -> "Decreasing"
    else -> "Stable"
}

private fun consumeHeaderScroll(
    availableY: Float,
    collapseOffsetPx: Float,
    setCollapseOffsetPx: (Float) -> Unit,
    maxCollapsePx: Float,
    canExpand: Boolean
): Offset {
    val normalizedCollapseOffsetPx = collapseOffsetPx.coerceIn(0f, maxCollapsePx)
    if (normalizedCollapseOffsetPx != collapseOffsetPx) {
        setCollapseOffsetPx(normalizedCollapseOffsetPx)
    }

    if (availableY < 0f && normalizedCollapseOffsetPx < maxCollapsePx) {
        val newOffset = (normalizedCollapseOffsetPx - availableY).coerceAtMost(maxCollapsePx)
        val consumed = newOffset - normalizedCollapseOffsetPx
        setCollapseOffsetPx(newOffset)
        return Offset(0f, -consumed)
    }

    if (availableY > 0f && canExpand && normalizedCollapseOffsetPx > 0f) {
        val newOffset = (normalizedCollapseOffsetPx - availableY).coerceAtLeast(0f)
        val consumed = normalizedCollapseOffsetPx - newOffset
        setCollapseOffsetPx(newOffset)
        return Offset(0f, consumed)
    }

    return Offset.Zero
}

private fun snapHeaderOffset(collapseOffsetPx: Float, maxCollapsePx: Float): Float {
    if (maxCollapsePx <= 0f) return 0f
    return if (collapseOffsetPx >= maxCollapsePx / 2f) {
        maxCollapsePx
    } else {
        0f
    }
}

@Composable
private fun SectionBlock(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        content = content
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End
        )
    }
}
