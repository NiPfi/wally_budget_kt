package net.loeu.wallybudget.ui.screens.history

import net.loeu.wallybudget.domain.model.CycleBucketSummary
import net.loeu.wallybudget.domain.model.ExpenseCycleSection
import net.loeu.wallybudget.util.CurrencyFormatter
import kotlin.math.abs

internal fun cycleHeaderNetRemainingCents(section: ExpenseCycleSection): Long {
    return section.bucketSummaries.sumRemainingOrNull()
        ?: (section.budgetAmountCents - section.totalSpentCents)
}

internal fun cycleHeaderSummaryText(
    section: ExpenseCycleSection,
    netRemainingCents: Long = cycleHeaderNetRemainingCents(section)
): String {
    val absoluteAmount = CurrencyFormatter.format(abs(netRemainingCents))
    return if (section.isCompletedCycle) {
        if (netRemainingCents >= 0L) {
            "Finished $absoluteAmount under budget"
        } else {
            "Finished $absoluteAmount over budget"
        }
    } else {
        if (netRemainingCents >= 0L) {
            "Net $absoluteAmount available"
        } else {
            "Net $absoluteAmount over budget"
        }
    }
}

private fun List<CycleBucketSummary>.sumRemainingOrNull(): Long? {
    return takeIf { it.isNotEmpty() }?.sumOf { it.remainingCents }
}
