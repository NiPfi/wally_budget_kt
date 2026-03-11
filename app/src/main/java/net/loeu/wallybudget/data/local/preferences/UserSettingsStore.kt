package net.loeu.wallybudget.data.local.preferences

import kotlinx.coroutines.flow.Flow
import net.loeu.wallybudget.domain.model.UserSettings
import java.time.LocalDate

interface UserSettingsStore {
    val userSettings: Flow<UserSettings>

    suspend fun ensureIdentity(): UserSettings

    suspend fun updateMonthlyBudget(amountCents: Long)

    suspend fun updatePaydayDate(day: Int)

    suspend fun updateLastResetTimestamp(timestamp: Long)

    suspend fun updateLastSeenDate(date: LocalDate)

    suspend fun completeOnboarding()

    suspend fun setPendingCycle(
        cycleStartDate: LocalDate,
        cycleEndDateExclusive: LocalDate,
        detectedAtTimestamp: Long
    )

    suspend fun clearPendingCycle()
}
