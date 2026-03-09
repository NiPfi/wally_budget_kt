package net.loeu.wallybudget.ui.screens.overview

internal fun calculateAvailableRecoverableOverspendCents(
    remainingTodayCents: Long,
    recoverableOverspendCents: Long
): Long {
    val overspendBeyondTodayAllowanceCents = (-remainingTodayCents).coerceAtLeast(0L)
    return (recoverableOverspendCents - overspendBeyondTodayAllowanceCents).coerceAtLeast(0L)
}

internal fun calculateSafeToSpendNowCents(
    remainingTodayCents: Long,
    availableRecoverableOverspendCents: Long
): Long {
    return remainingTodayCents.coerceAtLeast(0L) + availableRecoverableOverspendCents
}
