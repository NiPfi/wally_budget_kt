package net.loeu.wallybudget.ui.screens.home

import net.loeu.wallybudget.domain.model.BudgetBucket

internal fun compareBucketsClosedLast(): Comparator<BudgetBucket> {
    return compareBy<BudgetBucket> { it.isClosed }
        .thenBy { it.sortOrder }
        .thenBy { it.createdAtEpochMs }
}
