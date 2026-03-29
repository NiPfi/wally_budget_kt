package net.loeu.wallybudget.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.first
import java.io.File

internal class LegacyUserPreferencesMigration private constructor(
    private val legacyDataStore: DataStore<Preferences>,
    private val legacyPreferencesFile: File,
    private val legacyPaydayUndoJsonDecoder: LegacyPaydayUndoJsonDecoder
) : DataMigration<UserPreferencesState> {

    override suspend fun shouldMigrate(currentData: UserPreferencesState): Boolean {
        if (currentData != UserPreferencesState()) {
            return false
        }
        return legacyDataStore.data.first().asMap().isNotEmpty()
    }

    override suspend fun migrate(currentData: UserPreferencesState): UserPreferencesState {
        val preferences = legacyDataStore.data.first()
        if (preferences.asMap().isEmpty()) {
            return currentData
        }

        return UserPreferencesState(
            settings = StoredUserSettingsState(
                monthlyBudgetCents = preferences[PreferenceKeys.MONTHLY_BUDGET_CENTS] ?: 0L,
                portfolioMonthlyBudgetCents = preferences[PreferenceKeys.PORTFOLIO_MONTHLY_BUDGET_CENTS],
                paydayDate = preferences[PreferenceKeys.PAYDAY_DATE] ?: 1,
                lastResetTimestamp = preferences[PreferenceKeys.LAST_RESET_TIMESTAMP] ?: 0L,
                lastSeenDate = preferences[PreferenceKeys.LAST_SEEN_DATE],
                isOnboardingCompleted = preferences[PreferenceKeys.ONBOARDING_COMPLETED] ?: false,
                pendingCycleStartDate = preferences[PreferenceKeys.PENDING_CYCLE_START_DATE],
                pendingCycleEndDateExclusive = preferences[PreferenceKeys.PENDING_CYCLE_END_DATE_EXCLUSIVE],
                pendingCycleDetectedAtTimestamp = preferences[PreferenceKeys.PENDING_CYCLE_DETECTED_AT_TIMESTAMP]
                    ?: 0L,
                selectedBucketUuid = preferences[PreferenceKeys.SELECTED_BUCKET_UUID],
                installDeviceId = preferences[PreferenceKeys.INSTALL_DEVICE_ID] ?: "",
                settingsRecordUuid = preferences[PreferenceKeys.SETTINGS_RECORD_UUID] ?: "",
                settingsUpdatedAtEpochMs = preferences[PreferenceKeys.SETTINGS_UPDATED_AT_EPOCH_MS] ?: 0L,
                settingsModClock = preferences[PreferenceKeys.SETTINGS_MOD_CLOCK] ?: "",
                settingsLastModifiedByInstallId =
                    preferences[PreferenceKeys.SETTINGS_LAST_MODIFIED_BY_INSTALL_ID] ?: ""
            ),
            pendingPaydayUndo = preferences[PreferenceKeys.PENDING_SETTINGS_UNDO_JSON]
                ?.let(legacyPaydayUndoJsonDecoder::decodeOrNull)
        )
    }

    override suspend fun cleanUp() {
        legacyPreferencesFile.delete()
        legacyPreferencesFile.parentFile?.resolve("${legacyPreferencesFile.name}.bak")?.delete()
    }

    internal companion object {
        private const val LEGACY_DATASTORE_NAME = "user_settings"

        fun fromContext(
            context: Context,
            legacyPaydayUndoJsonDecoder: LegacyPaydayUndoJsonDecoder = LegacyPaydayUndoJsonDecoder()
        ): LegacyUserPreferencesMigration {
            val legacyPreferencesFile = context.preferencesDataStoreFile(LEGACY_DATASTORE_NAME)
            return LegacyUserPreferencesMigration(
                legacyDataStore = PreferenceDataStoreFactory.create(
                    produceFile = { legacyPreferencesFile }
                ),
                legacyPreferencesFile = legacyPreferencesFile,
                legacyPaydayUndoJsonDecoder = legacyPaydayUndoJsonDecoder
            )
        }

        fun createForTest(
            legacyDataStore: DataStore<Preferences>,
            legacyPreferencesFile: File,
            legacyPaydayUndoJsonDecoder: LegacyPaydayUndoJsonDecoder = LegacyPaydayUndoJsonDecoder()
        ): LegacyUserPreferencesMigration {
            return LegacyUserPreferencesMigration(
                legacyDataStore = legacyDataStore,
                legacyPreferencesFile = legacyPreferencesFile,
                legacyPaydayUndoJsonDecoder = legacyPaydayUndoJsonDecoder
            )
        }
    }

    private object PreferenceKeys {
        val MONTHLY_BUDGET_CENTS = longPreferencesKey("monthly_budget_cents")
        val PORTFOLIO_MONTHLY_BUDGET_CENTS = longPreferencesKey("portfolio_monthly_budget_cents")
        val PAYDAY_DATE = intPreferencesKey("payday_date")
        val LAST_RESET_TIMESTAMP = longPreferencesKey("last_reset_timestamp")
        val LAST_SEEN_DATE = stringPreferencesKey("last_seen_date")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val PENDING_CYCLE_START_DATE = stringPreferencesKey("pending_cycle_start_date")
        val PENDING_CYCLE_END_DATE_EXCLUSIVE = stringPreferencesKey("pending_cycle_end_date_exclusive")
        val PENDING_CYCLE_DETECTED_AT_TIMESTAMP = longPreferencesKey("pending_cycle_detected_at_timestamp")
        val SELECTED_BUCKET_UUID = stringPreferencesKey("selected_bucket_uuid")
        val INSTALL_DEVICE_ID = stringPreferencesKey("install_device_id")
        val SETTINGS_RECORD_UUID = stringPreferencesKey("settings_record_uuid")
        val SETTINGS_UPDATED_AT_EPOCH_MS = longPreferencesKey("settings_updated_at_epoch_ms")
        val SETTINGS_MOD_CLOCK = stringPreferencesKey("settings_mod_clock")
        val SETTINGS_LAST_MODIFIED_BY_INSTALL_ID = stringPreferencesKey("settings_last_modified_by_install_id")
        val PENDING_SETTINGS_UNDO_JSON = stringPreferencesKey("pending_settings_undo_json")
    }
}
