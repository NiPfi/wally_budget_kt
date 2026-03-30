package net.loeu.wallybudget.ui.screens.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.SelectedBucketOverview
import net.loeu.wallybudget.ui.CurrencyPlaceholderSamples
import net.loeu.wallybudget.ui.screens.overview.CollapsingSummaryLayout
import net.loeu.wallybudget.ui.screens.overview.CollapsingSummaryLayoutConfig
import net.loeu.wallybudget.ui.screens.overview.OverviewSectionBlock
import net.loeu.wallybudget.ui.screens.overview.rememberOverviewPageLayoutState

@Composable
internal fun DailyPacingBucketPage(
    selectedBucketOverview: SelectedBucketOverview,
    canEditExpenses: Boolean,
    onEditExpense: (Expense) -> Unit,
    onNavigateToAnalysis: (() -> Unit)?,
    showTopRightSettingsAction: Boolean,
    onNavigateToSettings: (() -> Unit)?,
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
            DailyPacingSummaryCard(
                selectedBucketOverview = selectedBucketOverview,
                collapseProgress = collapseProgress,
                onNavigateToAnalysis = onNavigateToAnalysis,
                onNavigateToSettings = if (showTopRightSettingsAction) onNavigateToSettings else null
            )
        }
    ) { listState, contentPadding ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = contentPadding
        ) {
            item {
                OverviewSectionBlock {
                    DailyPacingBucketDetailsSection(selectedBucketOverview = selectedBucketOverview)
                }
            }
            item {
                OverviewSectionBlock {
                    BucketCycleExpensesSection(
                        selectedBucketOverview = selectedBucketOverview,
                        canEditExpenses = canEditExpenses,
                        onEditExpense = onEditExpense
                    )
                }
            }
        }
    }
}

@Composable
internal fun DailyPacingBucketLoadingPage(
    selectedBucketOverview: SelectedBucketOverview,
    pageTitle: String,
    onNavigateToAnalysis: (() -> Unit)?,
    showTopRightSettingsAction: Boolean,
    onNavigateToSettings: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        DailyPacingSummaryCard(
            selectedBucketOverview = selectedBucketOverview,
            collapseProgress = 0f,
            onNavigateToAnalysis = onNavigateToAnalysis,
            onNavigateToSettings = if (showTopRightSettingsAction) onNavigateToSettings else null
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            BucketLoadingSection(
                title = "$pageTitle details",
                rows = listOf(
                    CurrencyPlaceholderSamples.amount(888_800L),
                    CurrencyPlaceholderSamples.amount(88_800L),
                    CurrencyPlaceholderSamples.amount(8_800L)
                )
            )
            BucketLoadingSection(
                title = "Recent activity",
                rows = listOf("Groceries", "Lunch with team", "Coffee")
            )
        }
    }
}
