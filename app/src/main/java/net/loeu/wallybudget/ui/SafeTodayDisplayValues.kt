package net.loeu.wallybudget.ui

import net.loeu.wallybudget.data.model.SpendingForecast

internal fun calculateAvailableRecoverableOverspendCentsFromForecast(
    remainingTodayCents: Long,
    forecast: SpendingForecast
): Long = calculateAvailableRecoverableOverspendCents(
    remainingTodayCents = remainingTodayCents,
    recoverableOverspendCents = forecast.recoverableOverspendCents
)

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
