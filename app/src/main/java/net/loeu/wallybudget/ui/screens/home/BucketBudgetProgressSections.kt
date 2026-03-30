package net.loeu.wallybudget.ui.screens.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.loeu.wallybudget.domain.model.BucketSummaryState
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.SelectedBucketOverview
import net.loeu.wallybudget.domain.model.displayDescription
import net.loeu.wallybudget.ui.screens.overview.AnimatedCounter
import net.loeu.wallybudget.util.CurrencyFormatter

private val SEGMENT_COLORS = listOf(
    Color(0xFF5B8FCC),
    Color(0xFF5AAD8A),
    Color(0xFFD98B4F),
    Color(0xFF9470BC),
    Color(0xFF4AADBD),
    Color(0xFFCD7D6A),
    Color(0xFF7BAA52),
    Color(0xFFCC6E93)
)

private data class BarSegment(val startX: Float, val endX: Float)

private fun computeBarSegments(
    expenses: List<Expense>,
    scaleCents: Long,
    barWidth: Float,
    gapPx: Float
): List<BarSegment> {
    if (scaleCents <= 0L || expenses.isEmpty()) return emptyList()
    val totalGapPx = gapPx * (expenses.size - 1).coerceAtLeast(0)
    val usableWidth = (barWidth - totalGapPx).coerceAtLeast(0f)
    var curX = 0f
    return expenses.map { expense ->
        val segWidth = (expense.amountCents.toFloat() / scaleCents * usableWidth).coerceAtLeast(0f)
        val seg = BarSegment(startX = curX, endX = (curX + segWidth).coerceAtMost(barWidth))
        curX = seg.endX + gapPx
        seg
    }
}

private data class CycleBudgetData(
    val allocatedCents: Long,
    val spentCents: Long,
    val remainingCents: Long,
    val overspentCents: Long,
    val spentFraction: Float,
    val isOverBudget: Boolean,
    val expenses: List<Expense>
)

@Composable
private fun rememberCycleBudgetData(selectedBucketOverview: SelectedBucketOverview): CycleBudgetData? {
    val summary = selectedBucketOverview.summary
    val budgetState = selectedBucketOverview.budgetState ?: return null
    val expenseSections = selectedBucketOverview.activeCycleExpenseSections

    return remember(summary, budgetState, expenseSections) {
        val allocated = summary.allocatedThisCycleCents
        val spent = summary.spentThisCycleCents
        val spentFraction = if (allocated > 0L) {
            (spent.toFloat() / allocated.toFloat()).coerceIn(0f, 1.5f)
        } else {
            0f
        }
        CycleBudgetData(
            allocatedCents = allocated,
            spentCents = spent,
            remainingCents = summary.remainingThisCycleCents,
            overspentCents = summary.overspentCents,
            spentFraction = spentFraction,
            isOverBudget = spent > allocated,
            expenses = expenseSections.flatMap { it.expenses }
        )
    }
}

@Composable
internal fun CycleBudgetProgressSection(selectedBucketOverview: SelectedBucketOverview) {
    val budgetData = rememberCycleBudgetData(selectedBucketOverview)
    val summary = selectedBucketOverview.summary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Budget usage",
            style = MaterialTheme.typography.titleLarge
        )
        if (budgetData != null) {
            CycleBudgetProgressBar(
                expenses = budgetData.expenses,
                allocatedCents = budgetData.allocatedCents,
                isOverBudget = budgetData.isOverBudget,
                modifier = Modifier.fillMaxWidth()
            )
            CycleBudgetProgressLabels(
                spentCents = budgetData.spentCents,
                remainingCents = budgetData.remainingCents,
                allocatedCents = budgetData.allocatedCents,
                isOverBudget = budgetData.isOverBudget
            )
        } else {
            CycleBudgetProgressFallback(summary)
        }
    }
}

@Composable
private fun CycleBudgetProgressBar(
    expenses: List<Expense>,
    allocatedCents: Long,
    isOverBudget: Boolean,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val overflowColor = MaterialTheme.colorScheme.error.copy(alpha = 0.30f)
    val tickColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val scaleCents = remember(expenses, allocatedCents) {
        val total = expenses.sumOf { it.amountCents }
        if (total > allocatedCents) total else allocatedCents
    }

    Column(modifier = modifier) {
        CycleBudgetProgressBarCanvas(
            expenses = expenses,
            scaleCents = scaleCents,
            allocatedCents = allocatedCents,
            trackColor = trackColor,
            overflowColor = overflowColor,
            tickColor = tickColor,
            isOverBudget = isOverBudget,
            selectedIndex = selectedIndex,
            onSelectionChange = { selectedIndex = it }
        )
        CycleBudgetProgressSelectionRow(
            expenses = expenses,
            selectedIndex = selectedIndex,
            onClearSelection = { selectedIndex = null }
        )
    }
}

@Composable
private fun CycleBudgetProgressBarCanvas(
    expenses: List<Expense>,
    scaleCents: Long,
    allocatedCents: Long,
    trackColor: Color,
    overflowColor: Color,
    tickColor: Color,
    isOverBudget: Boolean,
    selectedIndex: Int?,
    onSelectionChange: (Int?) -> Unit
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(expenses, scaleCents) {
                detectTapGestures { offset ->
                    val gapPx = 2.dp.toPx()
                    val layout = computeBarSegments(
                        expenses,
                        scaleCents,
                        size.width.toFloat(),
                        gapPx
                    )
                    val hit = layout.indexOfFirst { offset.x in it.startX..it.endX }
                    onSelectionChange(if (hit >= 0 && hit != selectedIndex) hit else null)
                }
            }
    ) {
        val gapPx = 2.dp.toPx()
        val budgetX = allocatedCents.toFloat() / scaleCents * size.width
        val layout = computeBarSegments(expenses, scaleCents, size.width, gapPx)
        drawSegmentedBar(
            layout = layout,
            trackColor = trackColor,
            overflowColor = overflowColor,
            tickColor = tickColor,
            budgetX = budgetX,
            selectedIndex = selectedIndex,
            isOverBudget = isOverBudget
        )
    }
}

@Composable
private fun CycleBudgetProgressSelectionRow(
    expenses: List<Expense>,
    selectedIndex: Int?,
    onClearSelection: () -> Unit
) {
    val selected = selectedIndex?.let { expenses.getOrNull(it) }
    val labelColor = selectedIndex?.let { SEGMENT_COLORS[it % SEGMENT_COLORS.size] } ?: Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (selected != null) Modifier.clickable { onClearSelection() } else Modifier)
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = selected?.displayDescription ?: "",
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            text = selected?.let { CurrencyFormatter.format(it.amountCents) } ?: "",
            style = MaterialTheme.typography.labelMedium,
            color = labelColor
        )
    }
}

private fun DrawScope.drawSegmentedBar(
    layout: List<BarSegment>,
    trackColor: Color,
    overflowColor: Color,
    tickColor: Color,
    budgetX: Float,
    selectedIndex: Int?,
    isOverBudget: Boolean
) {
    val barHeight = 14.dp.toPx()
    val barTop = (size.height - barHeight) / 2f
    val cornerR = barHeight / 2f
    val barPath = Path().apply {
        addRoundRect(
            RoundRect(
                left = 0f,
                top = barTop,
                right = size.width,
                bottom = barTop + barHeight,
                cornerRadius = CornerRadius(cornerR)
            )
        )
    }
    clipPath(barPath) {
        drawRect(
            color = trackColor,
            topLeft = Offset(0f, barTop),
            size = Size(size.width, barHeight)
        )
        layout.forEachIndexed { index, seg ->
            val alpha = if (selectedIndex == null || index == selectedIndex) 1f else 0.55f
            val color = SEGMENT_COLORS[index % SEGMENT_COLORS.size].copy(alpha = alpha)
            drawRect(
                color = color,
                topLeft = Offset(seg.startX, barTop),
                size = Size(seg.endX - seg.startX, barHeight)
            )
        }
        if (isOverBudget) {
            drawRect(
                color = overflowColor,
                topLeft = Offset(budgetX, barTop),
                size = Size(size.width - budgetX, barHeight)
            )
        }
    }
    if (isOverBudget) {
        val tickPad = 6.dp.toPx()
        drawLine(
            color = tickColor,
            start = Offset(budgetX, barTop - tickPad),
            end = Offset(budgetX, barTop + barHeight + tickPad),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun CycleBudgetProgressLabels(
    spentCents: Long,
    remainingCents: Long,
    allocatedCents: Long,
    isOverBudget: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Spent",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AnimatedCounter(
                amountCents = spentCents,
                textStyle = MaterialTheme.typography.titleMedium,
                color = if (isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                animate = true,
                textAlign = TextAlign.Start
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Remaining",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AnimatedCounter(
                amountCents = remainingCents,
                signed = true,
                textStyle = MaterialTheme.typography.titleMedium,
                color = if (isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                animate = true,
                textAlign = TextAlign.Center
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "Budget",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AnimatedCounter(
                amountCents = allocatedCents,
                textStyle = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                animate = true,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun CycleBudgetProgressFallback(summary: BucketSummaryState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PlainMetricRow("Allocated", CurrencyFormatter.format(summary.allocatedThisCycleCents))
        PlainMetricRow("Spent", CurrencyFormatter.format(summary.spentThisCycleCents))
        PlainMetricRow("Remaining", CurrencyFormatter.formatSigned(summary.remainingThisCycleCents))
    }
}
