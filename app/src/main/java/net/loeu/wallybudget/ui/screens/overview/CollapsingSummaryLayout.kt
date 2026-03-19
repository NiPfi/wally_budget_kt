@file:Suppress("MatchingDeclarationName")

package net.loeu.wallybudget.ui.screens.overview

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.SubcomposeMeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal data class CollapsingSummaryLayoutConfig(
    val modifier: Modifier = Modifier,
    val enableHeaderCollapse: Boolean = true,
    val bottomContentPadding: Dp = 24.dp,
    val headerHorizontalPadding: Dp = 12.dp,
    val headerTopPadding: Dp = 8.dp,
    val headerBottomSpacing: Dp = 10.dp
)

@Composable
internal fun CollapsingSummaryLayout(
    layoutState: OverviewPageState,
    config: CollapsingSummaryLayoutConfig = CollapsingSummaryLayoutConfig(),
    header: @Composable (collapseProgress: Float) -> Unit,
    body: @Composable (listState: LazyListState, contentPadding: PaddingValues) -> Unit
) {
    SubcomposeLayout(
        modifier = config.modifier
            .nestedScroll(layoutState.nestedScrollConnection)
            .clipToBounds()
    ) { constraints ->
        val headerMetrics = measureCollapsingHeaderMetrics(
            constraints = constraints,
            layoutState = layoutState,
            config = config,
            header = header
        )
        val maxCollapsePx = if (config.enableHeaderCollapse) {
            (headerMetrics.expandedHeaderHeightPx - headerMetrics.collapsedHeaderHeightPx).coerceAtLeast(0).toFloat()
        } else {
            0f
        }
        layoutState.maxCollapseRangePx.value = maxCollapsePx
        val clampedCollapseOffsetPx = layoutState.collapseOffsetPx.value.coerceIn(0f, maxCollapsePx)
        val collapseProgress =
            if (maxCollapsePx == 0f) 0f else (clampedCollapseOffsetPx / maxCollapsePx).coerceIn(0f, 1f)

        val contentPlaceables = subcompose("content") {
            body(
                layoutState.listState,
                PaddingValues(
                    top = with(layoutState.density) { headerMetrics.topContentPaddingPx.toDp() },
                    bottom = config.bottomContentPadding
                )
            )
        }.map { it.measure(constraints) }

        val headerPlaceables = subcompose("currentHeader") {
            header(collapseProgress)
        }.map { it.measure(headerMetrics.headerConstraints) }

        layout(constraints.maxWidth, constraints.maxHeight) {
            placeCollapsingSummaryLayout(
                contentPlaceables = contentPlaceables,
                headerPlaceables = headerPlaceables,
                collapseOffsetPx = clampedCollapseOffsetPx,
                headerMetrics = headerMetrics
            )
        }
    }
}

private data class CollapsingHeaderMetrics(
    val horizontalPaddingPx: Int,
    val topPaddingPx: Int,
    val headerConstraints: Constraints,
    val expandedHeaderHeightPx: Int,
    val collapsedHeaderHeightPx: Int,
    val topContentPaddingPx: Int
)

private fun SubcomposeMeasureScope.measureCollapsingHeaderMetrics(
    constraints: Constraints,
    layoutState: OverviewPageState,
    config: CollapsingSummaryLayoutConfig,
    header: @Composable (collapseProgress: Float) -> Unit
): CollapsingHeaderMetrics {
    val horizontalPaddingPx = with(layoutState.density) { config.headerHorizontalPadding.roundToPx() }
    val topPaddingPx = with(layoutState.density) { config.headerTopPadding.roundToPx() }
    val bottomSpacingPx = with(layoutState.density) { config.headerBottomSpacing.roundToPx() }
    val headerConstraints = constraints.copy(
        minWidth = 0,
        minHeight = 0,
        maxWidth = (constraints.maxWidth - (horizontalPaddingPx * 2)).coerceAtLeast(0)
    )
    val expandedHeaderHeightPx = measureCollapsingHeaderHeight(
        slotId = "expandedHeaderMeasure",
        collapseProgress = 0f,
        header = header,
        constraints = headerConstraints
    )
    val collapsedHeaderHeightPx = measureCollapsingHeaderHeight(
        slotId = "collapsedHeaderMeasure",
        collapseProgress = 1f,
        header = header,
        constraints = headerConstraints,
        fallbackHeight = expandedHeaderHeightPx
    )
    return CollapsingHeaderMetrics(
        horizontalPaddingPx = horizontalPaddingPx,
        topPaddingPx = topPaddingPx,
        headerConstraints = headerConstraints,
        expandedHeaderHeightPx = expandedHeaderHeightPx,
        collapsedHeaderHeightPx = collapsedHeaderHeightPx,
        topContentPaddingPx = expandedHeaderHeightPx + topPaddingPx + bottomSpacingPx
    )
}

private fun SubcomposeMeasureScope.measureCollapsingHeaderHeight(
    slotId: String,
    collapseProgress: Float,
    header: @Composable (collapseProgress: Float) -> Unit,
    constraints: Constraints,
    fallbackHeight: Int = 0
): Int {
    return subcompose(slotId) {
        header(collapseProgress)
    }.maxOfOrNull { it.measure(constraints).height } ?: fallbackHeight
}

private fun Placeable.PlacementScope.placeCollapsingSummaryLayout(
    contentPlaceables: List<Placeable>,
    headerPlaceables: List<Placeable>,
    collapseOffsetPx: Float,
    headerMetrics: CollapsingHeaderMetrics
) {
    val contentOffsetY = -collapseOffsetPx.roundToInt()
    contentPlaceables.forEach { it.placeRelative(0, contentOffsetY) }
    headerPlaceables.forEach {
        it.placeRelative(headerMetrics.horizontalPaddingPx, headerMetrics.topPaddingPx)
    }
}
