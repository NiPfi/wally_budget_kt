package net.loeu.wallybudget.ui.screens.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.ExpenseDaySection
import net.loeu.wallybudget.domain.model.SpendingForecast
import java.time.LocalDate

internal data class OverviewContentState(
    val budgetState: BudgetState,
    val effectiveCurrentDate: LocalDate,
    val todayExpenses: List<Expense>,
    val activeCycleExpenseSections: List<ExpenseDaySection>,
    val spendingForecast: SpendingForecast,
    val onEditTodayExpense: ((Expense) -> Unit)?,
    val isLoading: Boolean,
    val isBodyLoading: Boolean,
    val headerTitle: String?,
    val headerAnalysisAction: (() -> Unit)?,
    val headerSettingsAction: (() -> Unit)?,
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
@Suppress("LongMethod")
internal fun OverviewContentLayout(
    contentState: OverviewContentState,
    layoutState: OverviewPageState,
    config: OverviewContentConfig,
    onShowForecastDetails: () -> Unit,
    onShowSafeTodayDetails: () -> Unit
) {
    CollapsingSummaryLayout(
        layoutState = layoutState,
        config = CollapsingSummaryLayoutConfig(
            modifier = config.modifier,
            enableHeaderCollapse = config.enableHeaderCollapse,
            bottomContentPadding = config.bottomContentPadding,
            headerHorizontalPadding = config.headerHorizontalPadding,
            headerTopPadding = config.headerTopPadding,
            headerBottomSpacing = config.headerBottomSpacing
        ),
        header = { collapseProgress ->
            CurrentSummaryHeader(
                budgetState = contentState.budgetState,
                availableRecoverableOverspendCents = contentState.availableRecoverableOverspendCents,
                collapseProgress = collapseProgress,
                isLoading = contentState.isLoading,
                useWarningTint = contentState.useWarningTint,
                onShowSafeTodayDetails = onShowSafeTodayDetails,
                headerTitle = contentState.headerTitle,
                headerAnalysisAction = contentState.headerAnalysisAction,
                headerSettingsAction = contentState.headerSettingsAction,
                showTestTags = !LocalCollapsingHeaderIsForMeasurement.current,
                onNavigateToSettings = contentState.onNavigateToSettings
            )
        }
    ) { listState, contentPadding ->
        OverviewBodyContent(
            budgetState = contentState.budgetState,
            effectiveCurrentDate = contentState.effectiveCurrentDate,
            todayExpenses = contentState.todayExpenses,
            activeCycleExpenseSections = contentState.activeCycleExpenseSections,
            spendingForecast = contentState.spendingForecast,
            onEditTodayExpense = contentState.onEditTodayExpense,
            listState = listState,
            contentPadding = contentPadding,
            isLoading = contentState.isBodyLoading,
            onShowForecastDetails = onShowForecastDetails,
            showSpendingDetailsSection = config.showSpendingDetailsSection,
            showTodayExpensesSection = config.showTodayExpensesSection
        )
    }
}

@Composable
@Suppress("LongMethod")
private fun CurrentSummaryHeader(
    budgetState: BudgetState,
    availableRecoverableOverspendCents: Long,
    collapseProgress: Float,
    isLoading: Boolean,
    useWarningTint: Boolean,
    onShowSafeTodayDetails: () -> Unit,
    headerTitle: String?,
    headerAnalysisAction: (() -> Unit)?,
    headerSettingsAction: (() -> Unit)?,
    showTestTags: Boolean,
    onNavigateToSettings: (() -> Unit)?
) {
    val summaryModifier = Modifier
        .fillMaxWidth()
        .then(if (showTestTags) Modifier.testTag("home_summary_section") else Modifier)
    if (headerTitle != null) {
        val progress = collapseProgress.coerceIn(0f, 1f)
        val density = LocalDensity.current
        val horizontalPadding = lerp(20.dp, 16.dp, progress)
        val verticalPadding = lerp(18.dp, 8.dp, progress)
        val mergedHeaderTopPadding = 0.dp
        val mergedHeaderBodyOffset = lerp(0.dp, (-6).dp, progress)
        val contentSpacing = lerp(12.dp, 4.dp, progress)
        val iconAlpha = (1f - progress * 1.35f).coerceIn(0f, 1f)
        val secondaryMetricsProgress = 1f - progress
        val amountFontSize = lerp(22.sp, 17.sp, progress)
        val amountLineHeight = lerp(28.sp, 20.sp, progress)
        val safeTodayAlpha = (1f - progress * 1.5f).coerceIn(0f, 1f)
        val rightTopOffsetPx = with(density) { ((1f - progress) * 6.dp.toPx()) }
        val iconOffsetPx = with(density) { (progress * -4.dp.toPx()) }
        val bottomOffsetPx = with(density) { ((1f - secondaryMetricsProgress) * -6.dp.toPx()) }
        val colors = summaryCardColors(useWarningTint)

        MergedSummaryHeaderSurface(
            title = headerTitle,
            summaryColors = colors,
            modifier = summaryModifier,
            headerHorizontalPadding = horizontalPadding,
            headerBottomPadding = 0.dp,
            onNavigateToAnalysis = headerAnalysisAction,
            onNavigateToSettings = headerSettingsAction,
            headerRowTestTag = if (showTestTags) "home_page_header_row" else null,
            titleTestTag = if (showTestTags) "home_page_header_title" else null,
            analysisTestTag = if (showTestTags) "home_page_header_analysis" else null,
            settingsTestTag = if (showTestTags) "home_page_header_settings" else null
        ) {
            Box(modifier = Modifier.offset(y = mergedHeaderBodyOffset)) {
                SummaryCardBody(
                    budgetState = budgetState,
                    recoverableOverspendCents = availableRecoverableOverspendCents,
                    progress = progress,
                    horizontalPadding = horizontalPadding,
                    topPadding = mergedHeaderTopPadding,
                    bottomPadding = verticalPadding,
                    contentSpacing = contentSpacing,
                    amountFontSize = amountFontSize,
                    amountLineHeight = amountLineHeight,
                    contentColor = colors.content,
                    iconAlpha = iconAlpha,
                    iconOffsetPx = iconOffsetPx,
                    rightTopOffsetPx = rightTopOffsetPx,
                    safeTodayAlpha = safeTodayAlpha,
                    bottomOffsetPx = bottomOffsetPx,
                    secondaryMetricsProgress = secondaryMetricsProgress,
                    isLoading = isLoading,
                    animateCounters = true,
                    tagSecondaryMetrics = showTestTags,
                    onSafeTodayInfoClick = onShowSafeTodayDetails,
                    onNavigateToSettings = null
                )
            }
        }
    } else {
        SummaryCard(
            budgetState = budgetState,
            recoverableOverspendCents = availableRecoverableOverspendCents,
            collapseProgress = collapseProgress,
            isLoading = isLoading,
            animateCounters = true,
            useWarningTint = useWarningTint,
            tagSecondaryMetrics = showTestTags,
            onSafeTodayInfoClick = onShowSafeTodayDetails,
            onNavigateToSettings = onNavigateToSettings,
            modifier = summaryModifier
        )
    }
}

@Composable
private fun OverviewBodyContent(
    budgetState: BudgetState,
    effectiveCurrentDate: LocalDate,
    todayExpenses: List<Expense>,
    activeCycleExpenseSections: List<ExpenseDaySection>,
    spendingForecast: SpendingForecast,
    onEditTodayExpense: ((Expense) -> Unit)?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    contentPadding: PaddingValues,
    isLoading: Boolean,
    onShowForecastDetails: () -> Unit,
    showSpendingDetailsSection: Boolean,
    showTodayExpensesSection: Boolean
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            OverviewSectionBlock(
                modifier = Modifier.testTag("home_forecast_section")
            ) {
                ForecastCard(
                    spendingForecast = spendingForecast,
                    budgetState = budgetState,
                    effectiveCurrentDate = effectiveCurrentDate,
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
internal fun OverviewSectionBlock(
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
