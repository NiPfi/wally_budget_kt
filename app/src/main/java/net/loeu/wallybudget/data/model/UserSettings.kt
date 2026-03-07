package net.loeu.wallybudget.data.model

/**
 * User settings stored in DataStore
 */
data class UserSettings(
    val monthlyBudgetCents: Long = 0L,
    val paydayDate: Int = 1, // Day of month (1-31)
    val forecastSensitivityPercent: Int = 60,
    val lastResetTimestamp: Long = 0L,
    val isOnboardingCompleted: Boolean = false,
    val pendingCycleStartDate: String? = null,
    val pendingCycleEndDateExclusive: String? = null,
    val pendingCycleDetectedAtTimestamp: Long = 0L
)
