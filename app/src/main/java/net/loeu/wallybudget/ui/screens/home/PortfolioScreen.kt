package net.loeu.wallybudget.ui.screens.home

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.loeu.wallybudget.R
import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.BucketSummaryState
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import net.loeu.wallybudget.domain.model.Fund
import net.loeu.wallybudget.domain.model.PortfolioState
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.usecase.BucketDraft
import net.loeu.wallybudget.util.CurrencyFormatter

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
    var editingBucketState by remember { mutableStateOf<HomeBucketEditorState?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val orderedOpenSummaries = remember(bucketSummaries) {
        bucketSummaries.filterNot { it.bucket.isClosed }
    }

    fun openBucketEditor(bucketUuid: String) {
        val bucket = allBuckets.firstOrNull { it.bucketUuid == bucketUuid }?.takeIf { !it.isClosed } ?: return
        val summary = bucketSummaries.firstOrNull { it.bucket.bucketUuid == bucketUuid }
        editingBucketState = HomeBucketEditorState(
            bucketUuid = bucket.bucketUuid,
            name = bucket.name,
            amountText = CurrencyFormatter.centsToDecimalString(
                summary?.allocatedThisCycleCents ?: bucket.defaultAllocatedAmountCents
            ),
            isSystemDefault = bucket.bucketUuid == DEFAULT_SPENDING_BUCKET_UUID
        )
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
        PortfolioScreenContent(
            portfolioState = portfolioState,
            bucketSummaries = orderedOpenSummaries,
            funds = funds,
            interactionsEnabled = interactionsEnabled,
            onEditBucket = ::openBucketEditor,
            showTopRightSettingsAction = showTopRightSettingsAction,
            onNavigateToSettings = onNavigateToSettings,
            modifier = Modifier.padding(paddingValues)
        )
    }

    PortfolioBucketSheets(
        showAddBucketDialog = showAddBucketDialog,
        onDismissAddBucket = { showAddBucketDialog = false },
        editingBucketState = editingBucketState,
        onDismissEditBucket = { editingBucketState = null },
        portfolioBudgetCents = userSettings.resolvedPortfolioMonthlyBudgetCents,
        allBuckets = allBuckets,
        bucketSummaries = bucketSummaries,
        snackbarHostState = snackbarHostState,
        onSavePortfolioPlan = onSavePortfolioPlan,
        scope = scope
    )
}

@Composable
private fun PortfolioScreenContent(
    portfolioState: PortfolioState,
    bucketSummaries: List<BucketSummaryState>,
    funds: List<Fund>,
    interactionsEnabled: Boolean,
    onEditBucket: (String) -> Unit,
    showTopRightSettingsAction: Boolean,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    PortfolioOverviewPage(
        portfolioState = portfolioState,
        bucketSummaries = bucketSummaries,
        funds = funds,
        bucketInteractionsEnabled = interactionsEnabled,
        onEditBucket = { bucketUuid ->
            if (interactionsEnabled) {
                onEditBucket(bucketUuid)
            }
        },
        showTopRightSettingsAction = showTopRightSettingsAction,
        onNavigateToSettings = onNavigateToSettings,
        modifier = modifier
    )
}

@Composable
private fun PortfolioBucketSheets(
    showAddBucketDialog: Boolean,
    onDismissAddBucket: () -> Unit,
    editingBucketState: HomeBucketEditorState?,
    onDismissEditBucket: () -> Unit,
    portfolioBudgetCents: Long,
    allBuckets: List<BudgetBucket>,
    bucketSummaries: List<BucketSummaryState>,
    snackbarHostState: SnackbarHostState,
    onSavePortfolioPlan: (Long, List<BucketDraft>) -> Unit,
    scope: CoroutineScope
) {
    AddBucketSheet(
        showSheet = showAddBucketDialog,
        portfolioBudgetCents = portfolioBudgetCents,
        existingBuckets = allBuckets,
        bucketSummaries = bucketSummaries,
        onDismiss = onDismissAddBucket,
        onCreateBucket = { newBucketDraft ->
            onSavePortfolioPlan(
                portfolioBudgetCents,
                buildHomeBucketDrafts(
                    allBuckets = allBuckets,
                    bucketSummaries = bucketSummaries,
                    newBucketDraft = newBucketDraft
                )
            )
            onDismissAddBucket()
        }
    )
    BucketEditorSheet(
        state = editingBucketState,
        allBuckets = allBuckets,
        bucketSummaries = bucketSummaries,
        portfolioBudgetCents = portfolioBudgetCents,
        onRequestDismiss = onDismissEditBucket,
        onSubmit = { updatedBucketDraft ->
            when (val result = buildFreshUpdatedBucketDrafts(
                allBuckets = allBuckets,
                bucketSummaries = bucketSummaries,
                updatedBucketDraft = updatedBucketDraft
            )) {
                is BucketDraftBuildResult.Success -> {
                    onSavePortfolioPlan(portfolioBudgetCents, result.drafts)
                    onDismissEditBucket()
                }
                BucketDraftBuildResult.BucketChanged -> {
                    onDismissEditBucket()
                    scope.launch {
                        snackbarHostState.showSnackbar(BUCKET_CHANGED_SNACKBAR_MESSAGE)
                    }
                }
            }
        }
    )
}
