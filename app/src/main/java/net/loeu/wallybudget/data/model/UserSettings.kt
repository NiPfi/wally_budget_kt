package net.loeu.wallybudget.data.model

/**
 * User settings stored in DataStore
 */
data class UserSettings(
    val monthlyBudget: Double = 0.0,
    val paydayDate: Int = 1, // Day of month (1-31)
    val lastResetTimestamp: Long = 0L,
    val isOnboardingCompleted: Boolean = false
)

