package net.loeu.wallybudget.ui.screens.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.SelectedBucketOverview
import net.loeu.wallybudget.ui.screens.overview.CollapsingSummaryLayout
import net.loeu.wallybudget.ui.screens.overview.CollapsingSummaryLayoutConfig
import net.loeu.wallybudget.ui.screens.overview.OverviewSectionBlock
import net.loeu.wallybudget.ui.screens.overview.rememberOverviewPageLayoutState

@Composable
internal fun MonthlyTotalBucketPage(
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
            MonthlyTotalSummaryCard(
                selectedBucketOverview = selectedBucketOverview,
                collapseProgress = collapseProgress,
                onNavigateToAnalysis = onNavigateToAnalysis,
                onNavigateToSettings = if (showTopRightSettingsAction) onNavigateToSettings else null
            )
        }
    ) { listState, contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = contentPadding
        ) {
            item {
                OverviewSectionBlock {
                    CycleBudgetProgressSection(selectedBucketOverview = selectedBucketOverview)
                }
            }
            item {
                OverviewSectionBlock {
                    BucketCycleExpensesSection(
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
