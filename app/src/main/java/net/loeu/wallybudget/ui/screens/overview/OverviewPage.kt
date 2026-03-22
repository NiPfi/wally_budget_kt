package net.loeu.wallybudget.ui.screens.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
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

@Composable
fun OverviewPage(
    budgetState: BudgetState,
    todayExpenses: List<Expense>,
    activeCycleExpenseSections: List<ExpenseDaySection>,
    spendingForecast: SpendingForecast,
    onEditTodayExpense: ((Expense) -> Unit)?,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    headerTitle: String? = null,
    headerAnalysisAction: (() -> Unit)? = null,
    headerSettingsAction: (() -> Unit)? = null,
    onNavigateToSettings: (() -> Unit)? = null,
    showSpendingDetailsSection: Boolean = true,
    showTodayExpensesSection: Boolean = true,
    enableHeaderCollapse: Boolean = true,
    defaultCollapsedHeader: Boolean = false,
    bottomContentPadding: Dp = 24.dp
) {
    val headerHorizontalPadding = 0.dp
    val headerTopPadding = 0.dp
    val headerBottomSpacing = 10.dp
    val availableRecoverableOverspendCents = calculateAvailableRecoverableOverspendCentsFromForecast(
        remainingTodayCents = budgetState.remainingTodayCents,
        forecast = spendingForecast
    )
    val safeToSpendTodayCents = calculateSafeToSpendNowCents(
        remainingTodayCents = budgetState.remainingTodayCents,
        availableRecoverableOverspendCents = availableRecoverableOverspendCents
    )
    val useWarningTint =
        budgetState.remainingTodayCents < 0L ||
            budgetState.remainingCycleCents < 0L ||
            spendingForecast.isProjectedOverBudget
    val layoutState = rememberOverviewPageLayoutState(defaultCollapsedHeader, enableHeaderCollapse)

    OverviewInfoDialogs(
        showForecastDetails = layoutState.showForecastDetails.value && !isLoading,
        onDismissForecastDetails = { layoutState.showForecastDetails.value = false },
        showSafeTodayDetails = layoutState.showSafeTodayDetails.value && !isLoading,
        onDismissSafeTodayDetails = { layoutState.showSafeTodayDetails.value = false },
        spendingForecast = spendingForecast,
        budgetState = budgetState,
        availableRecoverableOverspendCents = availableRecoverableOverspendCents,
        safeToSpendTodayCents = safeToSpendTodayCents
    )

    OverviewContentLayout(
        contentState = OverviewContentState(
            budgetState = budgetState,
            todayExpenses = todayExpenses,
            activeCycleExpenseSections = activeCycleExpenseSections,
            spendingForecast = spendingForecast,
            onEditTodayExpense = onEditTodayExpense,
            isLoading = isLoading,
            headerTitle = headerTitle,
            headerAnalysisAction = headerAnalysisAction,
            headerSettingsAction = headerSettingsAction,
            onNavigateToSettings = onNavigateToSettings,
            availableRecoverableOverspendCents = availableRecoverableOverspendCents,
            useWarningTint = useWarningTint
        ),
        layoutState = layoutState,
        config = OverviewContentConfig(
            modifier = modifier,
            showSpendingDetailsSection = showSpendingDetailsSection,
            showTodayExpensesSection = showTodayExpensesSection,
            enableHeaderCollapse = enableHeaderCollapse,
            bottomContentPadding = bottomContentPadding,
            density = layoutState.density,
            headerHorizontalPadding = headerHorizontalPadding,
            headerTopPadding = headerTopPadding,
            headerBottomSpacing = headerBottomSpacing
        ),
        onShowForecastDetails = { layoutState.showForecastDetails.value = true },
        onShowSafeTodayDetails = { layoutState.showSafeTodayDetails.value = true }
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
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        icon = { InfoDialogIcon() }
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
internal fun rememberOverviewNestedScrollConnection(
    listState: LazyListState,
    enableHeaderCollapse: Boolean,
    collapseOffsetPx: () -> Float,
    setCollapseOffsetPx: (Float) -> Unit,
    maxCollapsePx: () -> Float
): NestedScrollConnection {
    return remember(listState, enableHeaderCollapse) {
        overviewNestedScrollConnection(
            enableHeaderCollapse = enableHeaderCollapse,
            canExpand = {
                listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0
            },
            collapseOffsetPx = collapseOffsetPx,
            setCollapseOffsetPx = setCollapseOffsetPx,
            maxCollapsePx = maxCollapsePx
        )
    }
}

internal fun overviewNestedScrollConnection(
    enableHeaderCollapse: Boolean,
    canExpand: () -> Boolean,
    collapseOffsetPx: () -> Float,
    setCollapseOffsetPx: (Float) -> Unit,
    maxCollapsePx: () -> Float
): NestedScrollConnection {
    return object : NestedScrollConnection {
        @Suppress("UNUSED_PARAMETER")
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (!enableHeaderCollapse) return Offset.Zero
            return consumeHeaderScroll(
                availableY = available.y,
                collapseOffsetPx = collapseOffsetPx(),
                setCollapseOffsetPx = setCollapseOffsetPx,
                maxCollapsePx = maxCollapsePx(),
                canExpand = canExpand()
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
                collapseOffsetPx = collapseOffsetPx(),
                setCollapseOffsetPx = setCollapseOffsetPx,
                maxCollapsePx = maxCollapsePx(),
                canExpand = canExpand()
            )
        }

        @Suppress("UNUSED_PARAMETER", "SameReturnValue")
        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (!enableHeaderCollapse) return Velocity.Zero
            val latestMaxCollapsePx = maxCollapsePx()
            setCollapseOffsetPx(
                snapHeaderOffset(
                    collapseOffsetPx().coerceIn(0f, latestMaxCollapsePx),
                    latestMaxCollapsePx
                )
            )
            return Velocity.Zero
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

    return when {
        availableY < 0f && normalizedCollapseOffsetPx < maxCollapsePx -> {
            val newOffset = (normalizedCollapseOffsetPx - availableY).coerceAtMost(maxCollapsePx)
            val consumed = newOffset - normalizedCollapseOffsetPx
            setCollapseOffsetPx(newOffset)
            Offset(0f, -consumed)
        }
        availableY > 0f && canExpand && normalizedCollapseOffsetPx > 0f -> {
            val newOffset = (normalizedCollapseOffsetPx - availableY).coerceAtLeast(0f)
            val consumed = normalizedCollapseOffsetPx - newOffset
            setCollapseOffsetPx(newOffset)
            Offset(0f, consumed)
        }
        else -> Offset.Zero
    }
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

@Composable
private fun InfoDialogIcon() {
    androidx.compose.material3.Icon(
        painter = painterResource(R.drawable.ic_info),
        contentDescription = null
    )
}
