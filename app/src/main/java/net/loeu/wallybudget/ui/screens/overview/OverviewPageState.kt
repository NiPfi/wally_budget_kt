package net.loeu.wallybudget.ui.screens.overview

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

internal data class OverviewPageLayoutState(
    val showForecastDetails: MutableState<Boolean>,
    val showSafeTodayDetails: MutableState<Boolean>,
    val listState: LazyListState,
    val density: Density,
    val collapseOffsetPx: MutableState<Float>,
    val maxCollapseRangePx: CollapseRangeHolder,
    val nestedScrollConnection: NestedScrollConnection
)

@Composable
internal fun rememberOverviewPageLayoutState(
    defaultCollapsedHeader: Boolean,
    enableHeaderCollapse: Boolean
): OverviewPageLayoutState {
    val showForecastDetails = remember { mutableStateOf(false) }
    val showSafeTodayDetails = remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val defaultCollapseOffsetPx = remember(defaultCollapsedHeader, density) {
        if (defaultCollapsedHeader) with(density) { 64.dp.toPx() } else 0f
    }
    val collapseOffsetPx = remember(defaultCollapsedHeader, density) {
        mutableFloatStateOf(defaultCollapseOffsetPx)
    }
    val maxCollapseRangePx = remember { CollapseRangeHolder() }
    val nestedScrollConnection = rememberOverviewNestedScrollConnection(
        listStateFirstVisibleItemIndex = listState.firstVisibleItemIndex,
        listStateFirstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
        enableHeaderCollapse = enableHeaderCollapse,
        collapseOffsetPx = collapseOffsetPx.floatValue,
        setCollapseOffsetPx = { collapseOffsetPx.floatValue = it },
        maxCollapsePx = maxCollapseRangePx.value
    )

    SideEffect {
        collapseOffsetPx.floatValue = collapseOffsetPx.floatValue.coerceIn(0f, maxCollapseRangePx.value)
    }

    return OverviewPageLayoutState(
        showForecastDetails = showForecastDetails,
        showSafeTodayDetails = showSafeTodayDetails,
        listState = listState,
        density = density,
        collapseOffsetPx = collapseOffsetPx,
        maxCollapseRangePx = maxCollapseRangePx,
        nestedScrollConnection = nestedScrollConnection
    )
}
