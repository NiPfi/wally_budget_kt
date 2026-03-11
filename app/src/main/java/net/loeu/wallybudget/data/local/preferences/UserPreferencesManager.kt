package net.loeu.wallybudget.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import java.time.LocalDate
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

class UserPreferencesManager(
    private val context: Context,
    private val hybridLogicalClockService: HybridLogicalClockService = HybridLogicalClockService()
) : UserSettingsStore {

    private object PreferenceKeys {
        val MONTHLY_BUDGET_CENTS = longPreferencesKey("monthly_budget_cents")
        val PAYDAY_DATE = intPreferencesKey("payday_date")
        val LAST_RESET_TIMESTAMP = longPreferencesKey("last_reset_timestamp")
        val LAST_SEEN_DATE = stringPreferencesKey("last_seen_date")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val PENDING_CYCLE_START_DATE = stringPreferencesKey("pending_cycle_start_date")
        val PENDING_CYCLE_END_DATE_EXCLUSIVE = stringPreferencesKey("pending_cycle_end_date_exclusive")
        val PENDING_CYCLE_DETECTED_AT_TIMESTAMP = longPreferencesKey("pending_cycle_detected_at_timestamp")
        val INSTALL_DEVICE_ID = stringPreferencesKey("install_device_id")
        val SETTINGS_RECORD_UUID = stringPreferencesKey("settings_record_uuid")
        val SETTINGS_UPDATED_AT_EPOCH_MS = longPreferencesKey("settings_updated_at_epoch_ms")
        val SETTINGS_MOD_CLOCK = stringPreferencesKey("settings_mod_clock")
        val SETTINGS_LAST_MODIFIED_BY_INSTALL_ID = stringPreferencesKey("settings_last_modified_by_install_id")
    }

    override val userSettings: Flow<UserSettings> = context.dataStore.data.map { preferences ->
        UserSettings(
            monthlyBudgetCents = preferences[PreferenceKeys.MONTHLY_BUDGET_CENTS] ?: 0L,
            paydayDate = preferences[PreferenceKeys.PAYDAY_DATE] ?: 1,
            lastResetTimestamp = preferences[PreferenceKeys.LAST_RESET_TIMESTAMP] ?: 0L,
            lastSeenDate = preferences[PreferenceKeys.LAST_SEEN_DATE],
            isOnboardingCompleted = preferences[PreferenceKeys.ONBOARDING_COMPLETED] ?: false,
            pendingCycleStartDate = preferences[PreferenceKeys.PENDING_CYCLE_START_DATE],
            pendingCycleEndDateExclusive = preferences[PreferenceKeys.PENDING_CYCLE_END_DATE_EXCLUSIVE],
            pendingCycleDetectedAtTimestamp = preferences[PreferenceKeys.PENDING_CYCLE_DETECTED_AT_TIMESTAMP] ?: 0L,
            installDeviceId = preferences[PreferenceKeys.INSTALL_DEVICE_ID] ?: "",
            settingsRecordUuid = preferences[PreferenceKeys.SETTINGS_RECORD_UUID] ?: "",
            settingsUpdatedAtEpochMs = preferences[PreferenceKeys.SETTINGS_UPDATED_AT_EPOCH_MS] ?: 0L,
            settingsModClock = preferences[PreferenceKeys.SETTINGS_MOD_CLOCK] ?: "",
            settingsLastModifiedByInstallId =
                preferences[PreferenceKeys.SETTINGS_LAST_MODIFIED_BY_INSTALL_ID] ?: ""
        )
    }

    override suspend fun ensureIdentity(): UserSettings {
        val sharedInstallId = getOrCreateInstallId(context)
        context.dataStore.edit { preferences ->
            val installId = preferences[PreferenceKeys.INSTALL_DEVICE_ID]
                ?: sharedInstallId.also {
                    preferences[PreferenceKeys.INSTALL_DEVICE_ID] = it
                }
            if (preferences[PreferenceKeys.SETTINGS_RECORD_UUID] == null) {
                preferences[PreferenceKeys.SETTINGS_RECORD_UUID] = UUID.randomUUID().toString()
            }
            if (preferences[PreferenceKeys.SETTINGS_LAST_MODIFIED_BY_INSTALL_ID] == null) {
                preferences[PreferenceKeys.SETTINGS_LAST_MODIFIED_BY_INSTALL_ID] = installId
            }
            if (preferences[PreferenceKeys.SETTINGS_MOD_CLOCK] == null) {
                preferences[PreferenceKeys.SETTINGS_MOD_CLOCK] = hybridLogicalClockService.format(
                    epochMs = System.currentTimeMillis(),
                    counter = 0,
                    installId = installId
                )
            }
            if (preferences[PreferenceKeys.SETTINGS_UPDATED_AT_EPOCH_MS] == null) {
                preferences[PreferenceKeys.SETTINGS_UPDATED_AT_EPOCH_MS] = System.currentTimeMillis()
            }
        }
        return userSettings.first()
    }

    override suspend fun updateMonthlyBudget(amountCents: Long) {
        context.dataStore.edit { preferences ->
            ensureSettingsIdentity(preferences)
            preferences[PreferenceKeys.MONTHLY_BUDGET_CENTS] = amountCents
            touchSettingsMetadata(preferences)
        }
    }

    override suspend fun updatePaydayDate(day: Int) {
        context.dataStore.edit { preferences ->
            ensureSettingsIdentity(preferences)
            preferences[PreferenceKeys.PAYDAY_DATE] = day
            touchSettingsMetadata(preferences)
        }
    }

    override suspend fun updateLastResetTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            ensureSettingsIdentity(preferences)
            preferences[PreferenceKeys.LAST_RESET_TIMESTAMP] = timestamp
            touchSettingsMetadata(preferences)
        }
    }

    override suspend fun updateLastSeenDate(date: LocalDate) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.LAST_SEEN_DATE] = date.toString()
        }
    }

    override suspend fun completeOnboarding() {
        context.dataStore.edit { preferences ->
            ensureSettingsIdentity(preferences)
            preferences[PreferenceKeys.ONBOARDING_COMPLETED] = true
            touchSettingsMetadata(preferences)
        }
    }

    override suspend fun setPendingCycle(
        cycleStartDate: LocalDate,
        cycleEndDateExclusive: LocalDate,
        detectedAtTimestamp: Long
    ) {
        context.dataStore.edit { preferences ->
            ensureSettingsIdentity(preferences)
            preferences[PreferenceKeys.PENDING_CYCLE_START_DATE] = cycleStartDate.toString()
            preferences[PreferenceKeys.PENDING_CYCLE_END_DATE_EXCLUSIVE] = cycleEndDateExclusive.toString()
            preferences[PreferenceKeys.PENDING_CYCLE_DETECTED_AT_TIMESTAMP] = detectedAtTimestamp
            touchSettingsMetadata(preferences)
        }
    }

    override suspend fun clearPendingCycle() {
        context.dataStore.edit { preferences ->
            ensureSettingsIdentity(preferences)
            preferences.remove(PreferenceKeys.PENDING_CYCLE_START_DATE)
            preferences.remove(PreferenceKeys.PENDING_CYCLE_END_DATE_EXCLUSIVE)
            preferences.remove(PreferenceKeys.PENDING_CYCLE_DETECTED_AT_TIMESTAMP)
            touchSettingsMetadata(preferences)
        }
    }

    private fun ensureSettingsIdentity(preferences: androidx.datastore.preferences.core.MutablePreferences) {
        val installId = preferences[PreferenceKeys.INSTALL_DEVICE_ID]
            ?: getOrCreateInstallId(context).also {
                preferences[PreferenceKeys.INSTALL_DEVICE_ID] = it
            }
        if (preferences[PreferenceKeys.SETTINGS_RECORD_UUID] == null) {
            preferences[PreferenceKeys.SETTINGS_RECORD_UUID] = UUID.randomUUID().toString()
        }
        if (preferences[PreferenceKeys.SETTINGS_LAST_MODIFIED_BY_INSTALL_ID] == null) {
            preferences[PreferenceKeys.SETTINGS_LAST_MODIFIED_BY_INSTALL_ID] = installId
        }
        if (preferences[PreferenceKeys.SETTINGS_MOD_CLOCK] == null) {
            preferences[PreferenceKeys.SETTINGS_MOD_CLOCK] = hybridLogicalClockService.format(
                epochMs = System.currentTimeMillis(),
                counter = 0,
                installId = installId
            )
        }
        if (preferences[PreferenceKeys.SETTINGS_UPDATED_AT_EPOCH_MS] == null) {
            preferences[PreferenceKeys.SETTINGS_UPDATED_AT_EPOCH_MS] = System.currentTimeMillis()
        }
    }

    private fun touchSettingsMetadata(preferences: androidx.datastore.preferences.core.MutablePreferences) {
        val installId = preferences[PreferenceKeys.INSTALL_DEVICE_ID].orEmpty()
        val now = System.currentTimeMillis()
        preferences[PreferenceKeys.SETTINGS_UPDATED_AT_EPOCH_MS] = now
        preferences[PreferenceKeys.SETTINGS_LAST_MODIFIED_BY_INSTALL_ID] = installId
        preferences[PreferenceKeys.SETTINGS_MOD_CLOCK] = hybridLogicalClockService.next(
            previousClock = preferences[PreferenceKeys.SETTINGS_MOD_CLOCK],
            nowEpochMs = now,
            installId = installId
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
