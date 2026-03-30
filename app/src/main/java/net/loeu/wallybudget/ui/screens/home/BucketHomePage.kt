@file:Suppress("MaxLineLength", "CyclomaticComplexMethod", "LongMethod")

package net.loeu.wallybudget.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.loeu.wallybudget.domain.model.BucketSummaryState
import net.loeu.wallybudget.domain.model.BucketTrackingMode
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.SelectedBucketOverview
import net.loeu.wallybudget.domain.model.SpendingForecast
import net.loeu.wallybudget.ui.screens.overview.OverviewPage
import net.loeu.wallybudget.ui.screens.overview.PlaceholderShimmerProvider
import java.time.LocalDate

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
                MonthlyTotalBucketPage(
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
                DailyPacingBucketLoadingPage(
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
        MonthlyTotalBucketPage(
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
        DailyPacingBucketPage(
            selectedBucketOverview = selectedBucketOverview,
            canEditExpenses = canEditExpenses,
            onEditExpense = onEditExpense,
            onNavigateToAnalysis = onNavigateToAnalysis,
            showTopRightSettingsAction = showTopRightSettingsAction,
            onNavigateToSettings = onNavigateToSettings,
            modifier = modifier
        )
    }
}
