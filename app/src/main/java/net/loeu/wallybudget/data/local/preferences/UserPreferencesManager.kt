package net.loeu.wallybudget.data.local.preferences

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.loeu.wallybudget.domain.model.PendingPaydayUndo
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import java.time.LocalDate
import java.util.UUID

@Suppress("TooManyFunctions")
class UserPreferencesManager(
    private val context: Context,
    private val hybridLogicalClockService: HybridLogicalClockService = HybridLogicalClockService()
) : UserSettingsStore {
    private val dataStore = context.applicationContext.userPreferencesDataStore

    override val userSettings: Flow<UserSettings> = dataStore.data.map(UserPreferencesState::toDomainUserSettings)

    override val pendingPaydayUndo: Flow<PendingPaydayUndo?> = dataStore.data.map { preferences ->
        preferences.pendingPaydayUndo?.toDomain()
    }

    override suspend fun ensureIdentity(): UserSettings {
        return dataStore.updateData { currentState ->
            ensureSettingsIdentity(currentState)
        }.toDomainUserSettings()
    }

    override suspend fun updateMonthlyBudget(amountCents: Long) {
        updateSettingsState { settings ->
            settings.copy(monthlyBudgetCents = amountCents)
        }
    }

    override suspend fun updatePortfolioMonthlyBudget(amountCents: Long?) {
        updateSettingsState { settings ->
            settings.copy(portfolioMonthlyBudgetCents = amountCents)
        }
    }

    override suspend fun updateCycleSettings(monthlyBudgetCents: Long, paydayDate: Int) {
        updateSettingsState { settings ->
            settings.copy(
                monthlyBudgetCents = monthlyBudgetCents,
                paydayDate = paydayDate
            )
        }
    }

    override suspend fun updatePaydayDate(day: Int) {
        updateSettingsState { settings ->
            settings.copy(paydayDate = day)
        }
    }

    override suspend fun updateSelectedBucket(selectedBucketUuid: String?) {
        updateSettingsState { settings ->
            settings.copy(selectedBucketUuid = selectedBucketUuid)
        }
    }

    override suspend fun updateLastResetTimestamp(timestamp: Long) {
        updateSettingsState { settings ->
            settings.copy(lastResetTimestamp = timestamp)
        }
    }

    override suspend fun updateLastSeenDate(date: LocalDate) {
        dataStore.updateData { currentState ->
            currentState.copy(
                settings = currentState.settings.copy(lastSeenDate = date.toString())
            )
        }
    }

    override suspend fun completeOnboarding() {
        updateSettingsState { settings ->
            settings.copy(isOnboardingCompleted = true)
        }
    }

    override suspend fun setPendingCycle(
        cycleStartDate: LocalDate,
        cycleEndDateExclusive: LocalDate,
        detectedAtTimestamp: Long
    ) {
        updateSettingsState { settings ->
            settings.copy(
                pendingCycleStartDate = cycleStartDate.toString(),
                pendingCycleEndDateExclusive = cycleEndDateExclusive.toString(),
                pendingCycleDetectedAtTimestamp = detectedAtTimestamp
            )
        }
    }

    override suspend fun clearPendingCycle() {
        updateSettingsState { settings ->
            settings.copy(
                pendingCycleStartDate = null,
                pendingCycleEndDateExclusive = null,
                pendingCycleDetectedAtTimestamp = 0L
            )
        }
    }

    override suspend fun savePendingPaydayUndo(pendingPaydayUndo: PendingPaydayUndo) {
        dataStore.updateData { currentState ->
            currentState.copy(pendingPaydayUndo = pendingPaydayUndo.toState())
        }
    }

    override suspend fun clearPendingPaydayUndo() {
        dataStore.updateData { currentState ->
            currentState.copy(pendingPaydayUndo = null)
        }
    }

    override suspend fun restoreFromSnapshot(settings: UserSettings, onboardingCompleted: Boolean) {
        dataStore.updateData { currentState ->
            val currentInstallId = currentState.settings.installDeviceId.takeIf { it.isNotBlank() }
            val restoredInstallId = currentInstallId
                ?: settings.installDeviceId.takeIf { it.isNotBlank() }
                ?: UUID.randomUUID().toString()
            currentState.copy(
                settings = settings.toStoredState().copy(
                    isOnboardingCompleted = onboardingCompleted,
                    installDeviceId = restoredInstallId,
                    settingsRecordUuid = settings.settingsRecordUuid
                        .takeIf { it.isNotBlank() }
                        ?: UUID.randomUUID().toString(),
                    settingsUpdatedAtEpochMs = settings.settingsUpdatedAtEpochMs,
                    settingsModClock = settings.settingsModClock.ifBlank {
                        hybridLogicalClockService.format(
                            epochMs = settings.settingsUpdatedAtEpochMs.coerceAtLeast(System.currentTimeMillis()),
                            counter = 0,
                            installId = restoredInstallId
                        )
                    },
                    settingsLastModifiedByInstallId = settings.settingsLastModifiedByInstallId.ifBlank {
                        restoredInstallId
                    }
                ),
                pendingPaydayUndo = null
            )
        }
    }

    private suspend fun updateSettingsState(
        transform: (StoredUserSettingsState) -> StoredUserSettingsState
    ) {
        dataStore.updateData { currentState ->
            val stateWithIdentity = ensureSettingsIdentity(currentState)
            stateWithIdentity.copy(
                settings = touchSettingsMetadata(
                    transform(stateWithIdentity.settings)
                )
            )
        }
    }

    private fun ensureSettingsIdentity(currentState: UserPreferencesState): UserPreferencesState {
        val settings = currentState.settings
        val installId = settings.installDeviceId.takeIf { it.isNotBlank() } ?: getOrCreateInstallId(context)
        val now = System.currentTimeMillis()
        return currentState.copy(
            settings = settings.copy(
                installDeviceId = installId,
                settingsRecordUuid = settings.settingsRecordUuid.ifBlank { UUID.randomUUID().toString() },
                settingsLastModifiedByInstallId = settings.settingsLastModifiedByInstallId.ifBlank { installId },
                settingsModClock = settings.settingsModClock.ifBlank {
                    hybridLogicalClockService.format(
                        epochMs = now,
                        counter = 0,
                        installId = installId
                    )
                },
                settingsUpdatedAtEpochMs = settings.settingsUpdatedAtEpochMs.takeIf { it != 0L } ?: now
            )
        )
    }

    private fun touchSettingsMetadata(settings: StoredUserSettingsState): StoredUserSettingsState {
        val installId = settings.installDeviceId
        val now = System.currentTimeMillis()
        return settings.copy(
            settingsUpdatedAtEpochMs = now,
            settingsLastModifiedByInstallId = installId,
            settingsModClock = hybridLogicalClockService.next(
                previousClock = settings.settingsModClock,
                nowEpochMs = now,
                installId = installId
            )
        )
    }

    companion object {
        private const val INSTALL_ID_PREFS = "install_identity"
        private const val INSTALL_ID_KEY = "install_device_id"

        fun getOrCreateInstallId(context: Context): String {
            val preferences = context.getSharedPreferences(INSTALL_ID_PREFS, Context.MODE_PRIVATE)
            return preferences.getString(INSTALL_ID_KEY, null)
                ?: UUID.randomUUID().toString().also { installId ->
                    preferences.edit().putString(INSTALL_ID_KEY, installId).apply()
                }
        }
    }
}
