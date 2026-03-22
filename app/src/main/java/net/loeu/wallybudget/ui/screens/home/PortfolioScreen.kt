package net.loeu.wallybudget.ui.screens.home

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import net.loeu.wallybudget.R
import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.BucketSummaryState
import net.loeu.wallybudget.domain.model.Fund
import net.loeu.wallybudget.domain.model.PortfolioState
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.usecase.BucketDraft

@Composable
fun PortfolioScreen(
    portfolioState: PortfolioState,
    bucketSummaries: List<BucketSummaryState>,
    funds: List<Fund>,
    allBuckets: List<BudgetBucket>,
    userSettings: UserSettings,
    onSavePortfolioPlan: (Long, List<BucketDraft>) -> Unit,
    onNavigateToSettings: () -> Unit,
    showTopRightSettingsAction: Boolean,
    modifier: Modifier = Modifier,
    interactionsEnabled: Boolean = true
) {
    var showAddBucketDialog by rememberSaveable { mutableStateOf(false) }
    val orderedOpenSummaries = remember(bucketSummaries) {
        bucketSummaries.filterNot { it.bucket.isClosed }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (!interactionsEnabled) return@ExtendedFloatingActionButton
                    showAddBucketDialog = true
                },
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = null
                    )
                },
                text = { Text("Add bucket") }
            )
        }
    ) { paddingValues ->
        PortfolioOverviewPage(
            portfolioState = portfolioState,
            bucketSummaries = orderedOpenSummaries,
            funds = funds,
            showTopRightSettingsAction = showTopRightSettingsAction,
            onNavigateToSettings = onNavigateToSettings,
            modifier = Modifier
                .then(modifier)
                .padding(paddingValues)
        )
    }

    AddBucketSheet(
        showSheet = showAddBucketDialog,
        portfolioBudgetCents = userSettings.resolvedPortfolioMonthlyBudgetCents,
        existingBuckets = allBuckets,
        bucketSummaries = bucketSummaries,
        onDismiss = { showAddBucketDialog = false },
        onCreateBucket = { newBucketDraft ->
            onSavePortfolioPlan(
                userSettings.resolvedPortfolioMonthlyBudgetCents,
                buildHomeBucketDrafts(
                    allBuckets = allBuckets,
                    bucketSummaries = bucketSummaries,
                    newBucketDraft = newBucketDraft
                )
            )
            showAddBucketDialog = false
        }
    )
}
