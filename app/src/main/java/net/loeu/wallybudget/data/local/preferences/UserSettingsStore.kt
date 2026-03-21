package net.loeu.wallybudget.data.local.preferences

import kotlinx.coroutines.flow.Flow
import net.loeu.wallybudget.domain.model.PendingPaydayUndo
import net.loeu.wallybudget.domain.model.UserSettings
import java.time.LocalDate

@Suppress("TooManyFunctions")
interface UserSettingsStore {
    val userSettings: Flow<UserSettings>
    val pendingPaydayUndo: Flow<PendingPaydayUndo?>

    suspend fun ensureIdentity(): UserSettings

    suspend fun updateCycleSettings(monthlyBudgetCents: Long, paydayDate: Int)

    suspend fun updatePortfolioMonthlyBudget(amountCents: Long?)

    suspend fun updateMonthlyBudget(amountCents: Long)

    suspend fun updatePaydayDate(day: Int)

    suspend fun updateSelectedBucket(selectedBucketUuid: String?)

    suspend fun updateLeftoverReceiverBucket(leftoverReceiverBucketUuid: String?)

    suspend fun updateLastResetTimestamp(timestamp: Long)

    suspend fun updateLastSeenDate(date: LocalDate)

    suspend fun completeOnboarding()

    suspend fun setPendingCycle(
        cycleStartDate: LocalDate,
        cycleEndDateExclusive: LocalDate,
        detectedAtTimestamp: Long
    )

    suspend fun clearPendingCycle()

    suspend fun savePendingPaydayUndo(pendingPaydayUndo: PendingPaydayUndo)

    suspend fun clearPendingPaydayUndo()

    suspend fun restoreFromSnapshot(settings: UserSettings, onboardingCompleted: Boolean)
}
