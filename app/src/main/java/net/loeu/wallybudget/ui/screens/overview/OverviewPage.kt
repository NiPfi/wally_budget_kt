package net.loeu.wallybudget.ui.screens.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.loeu.wallybudget.data.model.BudgetState
import net.loeu.wallybudget.data.model.Expense
import net.loeu.wallybudget.data.model.ExpenseDaySection
import net.loeu.wallybudget.data.model.SpendingForecast
import net.loeu.wallybudget.domain.config.ForecastConfig
import net.loeu.wallybudget.ui.screens.expenses.ExpenseItem
import net.loeu.wallybudget.util.CurrencyFormatter

@Composable
fun OverviewPage(
    budgetState: BudgetState,
    todayExpenses: List<Expense>,
    activeCycleExpenseSections: List<ExpenseDaySection>,
    spendingForecast: SpendingForecast,
    onEditTodayExpense: (Expense) -> Unit,
    onNavigateToSettings: (() -> Unit)? = null,
    showTodayExpensesSection: Boolean = true,
    enableHeaderCollapse: Boolean = true,
    modifier: Modifier = Modifier
) {
    val headerHorizontalPadding = 12.dp
    val headerTopPadding = 8.dp
    val headerBottomSpacing = 10.dp
    val previousExpensesTotal = activeCycleExpenseSections
        .filterNot { it.isToday }
        .sumOf { it.totalSpentCents }
    val adjustedDailyAllowanceCents = budgetState.remainingTodayCents + budgetState.spentTodayCents
    val dailyAdjustmentCents = adjustedDailyAllowanceCents - budgetState.dailyBudgetCents
    val showForecastDetails = remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var collapseOffsetPx by remember { mutableFloatStateOf(0f) }
    val maxCollapseRangePx = remember { object { var value: Float = 0f } }
    val nestedScrollConnection = remember(listState, enableHeaderCollapse) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!enableHeaderCollapse) return Offset.Zero
                return consumeHeaderScroll(
                    availableY = available.y,
                    collapseOffsetPx = collapseOffsetPx,
                    setCollapseOffsetPx = { collapseOffsetPx = it },
                    maxCollapsePx = maxCollapseRangePx.value,
                    canExpand = listState.firstVisibleItemIndex == 0 &&
                        listState.firstVisibleItemScrollOffset == 0
                )
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (!enableHeaderCollapse) return Offset.Zero
                return consumeHeaderScroll(
                    availableY = available.y,
                    collapseOffsetPx = collapseOffsetPx,
                    setCollapseOffsetPx = { collapseOffsetPx = it },
                    maxCollapsePx = maxCollapseRangePx.value,
                    canExpand = listState.firstVisibleItemIndex == 0 &&
                        listState.firstVisibleItemScrollOffset == 0
                )
            }
        }
    }

    if (showForecastDetails.value) {
        AlertDialog(
            onDismissRequest = { showForecastDetails.value = false },
            title = { Text("Forecast analysis") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "This projection analyzes your recent pace together with prior cycle behavior to estimate your end-of-cycle balance.",
                        style = MaterialTheme.typography.bodyMedium
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
                            DetailRow("Confidence", "${(spendingForecast.confidenceScore * 100).toInt()}% (${spendingForecast.confidenceRating})")
                            DetailRow("Average daily pace", CurrencyFormatter.format(spendingForecast.dailyAverageWeightedCents))
                            DetailRow("Window", "${spendingForecast.usedDataPoints} days")
                            val trendVal = spendingForecast.trendSlopeCents
                            val trendText = when {
                                trendVal > ForecastConfig.TREND_SIGNIFICANCE_THRESHOLD_CENTS -> "Increasing"
                                trendVal < -ForecastConfig.TREND_SIGNIFICANCE_THRESHOLD_CENTS -> "Decreasing"
                                else -> "Stable"
                            }
                            DetailRow("Trend", trendText)
                            if (spendingForecast.detectedOutlierCount > 0) {
                                DetailRow("Filtered anomalies", spendingForecast.detectedOutlierCount.toString())
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showForecastDetails.value = false }) {
                    Text("Close")
                }
            },
            icon = {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null
                )
            }
        )
    }

    SubcomposeLayout(modifier = modifier.nestedScroll(nestedScrollConnection)) { constraints ->
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
                collapseProgress = 0f,
                onNavigateToSettings = onNavigateToSettings,
                modifier = Modifier.fillMaxWidth()
            )
        }.maxOfOrNull { it.measure(headerConstraints).height } ?: 0

        val collapsedHeaderHeightPx = subcompose("collapsedHeaderMeasure") {
            SummaryCard(
                budgetState = budgetState,
                collapseProgress = 1f,
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

        val collapseProgress = if (maxCollapsePx == 0f) {
            0f
        } else {
            (collapseOffsetPx.coerceIn(0f, maxCollapsePx) / maxCollapsePx).coerceIn(0f, 1f)
        }
        val currentHeaderHeightPx = (
            expandedHeaderHeightPx +
                ((collapsedHeaderHeightPx - expandedHeaderHeightPx) * collapseProgress)
            ).toInt()

        val contentPlaceables = subcompose("content") {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(
                    top = with(density) {
                        (currentHeaderHeightPx + topPaddingPx + bottomSpacingPx).toDp()
                    },
                    bottom = 24.dp
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
                            onClick = { showForecastDetails.value = true }
                        )
                    }
                }
                item {
                    SectionBlock(
                        modifier = Modifier.testTag("home_spending_today_section")
                    ) {
                        SpendingDetailsCard(
                            budgetState = budgetState,
                            previousExpensesTotal = previousExpensesTotal,
                            dailyAdjustmentCents = dailyAdjustmentCents,
                            adjustedDailyAllowanceCents = adjustedDailyAllowanceCents
                        )
                        if (showTodayExpensesSection) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                            TodayExpensesSection(
                                todayExpenses = todayExpenses,
                                onEditTodayExpense = onEditTodayExpense,
                                modifier = Modifier.testTag("home_today_expenses_section")
                            )
                        }
                    }
                }
            }
        }.map { it.measure(constraints) }

        val headerPlaceables = subcompose("currentHeader") {
            SummaryCard(
                budgetState = budgetState,
                collapseProgress = collapseProgress,
                onNavigateToSettings = onNavigateToSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_summary_section")
            )
        }.map { it.measure(headerConstraints) }

        layout(constraints.maxWidth, constraints.maxHeight) {
            contentPlaceables.forEach { it.placeRelative(0, 0) }
            headerPlaceables.forEach { it.placeRelative(horizontalPaddingPx, topPaddingPx) }
        }
    }
}

private fun consumeHeaderScroll(
    availableY: Float,
    collapseOffsetPx: Float,
    setCollapseOffsetPx: (Float) -> Unit,
    maxCollapsePx: Float,
    canExpand: Boolean
): Offset {
    if (availableY < 0f && collapseOffsetPx < maxCollapsePx) {
        val newOffset = (collapseOffsetPx - availableY).coerceAtMost(maxCollapsePx)
        val consumed = newOffset - collapseOffsetPx
        setCollapseOffsetPx(newOffset)
        return Offset(0f, -consumed)
    }

    if (availableY > 0f && canExpand && collapseOffsetPx > 0f) {
        val newOffset = (collapseOffsetPx - availableY).coerceAtLeast(0f)
        val consumed = collapseOffsetPx - newOffset
        setCollapseOffsetPx(newOffset)
        return Offset(0f, consumed)
    }

    return Offset.Zero
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
private fun TodayExpensesSection(
    todayExpenses: List<Expense>,
    onEditTodayExpense: (Expense) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Today's expenses",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        val totalSpent = todayExpenses.sumOf { it.amountCents }
        Text(
            text = CurrencyFormatter.format(totalSpent),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )

        if (todayExpenses.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No expenses yet for today.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            todayExpenses.take(4).forEachIndexed { index, expense ->
                ExpenseItem(
                    expense = expense,
                    onEdit = { onEditTodayExpense(expense) }
                )
                if (index != todayExpenses.take(4).lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
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
