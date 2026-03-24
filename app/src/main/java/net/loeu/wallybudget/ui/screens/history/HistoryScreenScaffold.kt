package net.loeu.wallybudget.ui.screens.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import net.loeu.wallybudget.domain.model.ExpenseCycleSection
import net.loeu.wallybudget.ui.components.TimelineLockBanner

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryScreenScaffold(
    historySections: List<ExpenseCycleSection>,
    compactPagerSections: List<ExpenseCycleSection>,
    pagerCurrentPage: Int,
    pagerState: androidx.compose.foundation.pager.PagerState,
    modifier: Modifier,
    embedded: Boolean,
    isCompact: Boolean,
    showInitialSwipeHint: Boolean,
    snackbarHostState: SnackbarHostState,
    timelineLockReason: String?,
    onNavigateToSettings: (() -> Unit)?,
    historyBucketNameByUuid: Map<String, String>
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = if (embedded) WindowInsets() else ScaffoldDefaults.contentWindowInsets,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!embedded && timelineLockReason != null) {
                TimelineLockBanner(
                    reason = timelineLockReason,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            when {
                historySections.isEmpty() -> EmptyHistoryState(
                    modifier = Modifier.weight(1f)
                )
                !embedded && isCompact -> CompactHistoryContent(
                    compactPagerSections = compactPagerSections,
                    pagerCurrentPage = pagerCurrentPage,
                    pagerState = pagerState,
                    showInitialSwipeHint = showInitialSwipeHint,
                    onNavigateToSettings = onNavigateToSettings,
                    historyBucketNameByUuid = historyBucketNameByUuid,
                    modifier = Modifier.weight(1f)
                )
                else -> FullHistoryContent(
                    historySections = historySections,
                    embedded = embedded,
                    historyBucketNameByUuid = historyBucketNameByUuid,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun EmptyHistoryState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No cycle data yet.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactHistoryContent(
    compactPagerSections: List<ExpenseCycleSection>,
    pagerCurrentPage: Int,
    pagerState: androidx.compose.foundation.pager.PagerState,
    showInitialSwipeHint: Boolean,
    onNavigateToSettings: (() -> Unit)?,
    historyBucketNameByUuid: Map<String, String>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CompactHistoryHeader(
            pageCount = compactPagerSections.size,
            currentPage = pagerCurrentPage,
            onNavigateToSettings = onNavigateToSettings
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                CycleLedgerPage(
                    section = compactPagerSections[page],
                    historyBucketNameByUuid = historyBucketNameByUuid,
                    onEditExpense = null,
                    contentPadding = PaddingValues(bottom = 24.dp)
                )
            }

            CyclePagerHint(
                currentPage = pagerCurrentPage,
                pageCount = compactPagerSections.size,
                showSwipeHint = showInitialSwipeHint,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(y = (-24).dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FullHistoryContent(
    historySections: List<ExpenseCycleSection>,
    embedded: Boolean,
    historyBucketNameByUuid: Map<String, String>,
    modifier: Modifier = Modifier
) {
    val sectionsToShow = if (embedded) historySections.take(1) else historySections
    Box(
        modifier = modifier
            .fillMaxWidth()
    ) {
        LazyColumn(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = if (embedded) 560.dp else 760.dp)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = if (embedded) 8.dp else 14.dp,
                bottom = 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            sectionsToShow.forEach { section ->
                if (!embedded) {
                    stickyHeader(key = "cycle-${section.title}") {
                        CycleHeader(section = section)
                    }
                }
                items(
                    items = section.daySections,
                    key = { daySection -> "${section.title}-${daySection.date.toEpochDay()}" }
                ) { daySection ->
                    LedgerDaySection(
                        daySection = daySection,
                        historyBucketNameByUuid = historyBucketNameByUuid,
                        onEditExpense = null
                    )
                }
            }
        }
    }
}
