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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.loeu.wallybudget.domain.model.UserSettings
import java.time.LocalDate

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

private const val FORECAST_SENSITIVITY_MIN = 20
private const val FORECAST_SENSITIVITY_MAX = 90
private const val FORECAST_SENSITIVITY_DEFAULT = 60

class UserPreferencesManager(private val context: Context) : UserSettingsStore {

    private object PreferenceKeys {
        val MONTHLY_BUDGET_CENTS = longPreferencesKey("monthly_budget_cents")
        val PAYDAY_DATE = intPreferencesKey("payday_date")
        val FORECAST_SENSITIVITY_PERCENT = intPreferencesKey("forecast_sensitivity_percent")
        val LAST_RESET_TIMESTAMP = longPreferencesKey("last_reset_timestamp")
        val LAST_SEEN_DATE = stringPreferencesKey("last_seen_date")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val PENDING_CYCLE_START_DATE = stringPreferencesKey("pending_cycle_start_date")
        val PENDING_CYCLE_END_DATE_EXCLUSIVE = stringPreferencesKey("pending_cycle_end_date_exclusive")
        val PENDING_CYCLE_DETECTED_AT_TIMESTAMP = longPreferencesKey("pending_cycle_detected_at_timestamp")
    }

    override val userSettings: Flow<UserSettings> = context.dataStore.data.map { preferences ->
        UserSettings(
            monthlyBudgetCents = preferences[PreferenceKeys.MONTHLY_BUDGET_CENTS] ?: 0L,
            paydayDate = preferences[PreferenceKeys.PAYDAY_DATE] ?: 1,
            forecastSensitivityPercent = (preferences[PreferenceKeys.FORECAST_SENSITIVITY_PERCENT]
                ?: FORECAST_SENSITIVITY_DEFAULT)
                .coerceIn(FORECAST_SENSITIVITY_MIN, FORECAST_SENSITIVITY_MAX),
            lastResetTimestamp = preferences[PreferenceKeys.LAST_RESET_TIMESTAMP] ?: 0L,
            lastSeenDate = preferences[PreferenceKeys.LAST_SEEN_DATE],
            isOnboardingCompleted = preferences[PreferenceKeys.ONBOARDING_COMPLETED] ?: false,
            pendingCycleStartDate = preferences[PreferenceKeys.PENDING_CYCLE_START_DATE],
            pendingCycleEndDateExclusive = preferences[PreferenceKeys.PENDING_CYCLE_END_DATE_EXCLUSIVE],
            pendingCycleDetectedAtTimestamp = preferences[PreferenceKeys.PENDING_CYCLE_DETECTED_AT_TIMESTAMP] ?: 0L
        )
    }

    override suspend fun updateMonthlyBudget(amountCents: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.MONTHLY_BUDGET_CENTS] = amountCents
        }
    }

    override suspend fun updatePaydayDate(day: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.PAYDAY_DATE] = day
        }
    }

    override suspend fun updateLastResetTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.LAST_RESET_TIMESTAMP] = timestamp
        }
    }

    override suspend fun updateLastSeenDate(date: LocalDate) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.LAST_SEEN_DATE] = date.toString()
        }
    }

    override suspend fun completeOnboarding() {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.ONBOARDING_COMPLETED] = true
        }
    }

    override suspend fun setPendingCycle(
        cycleStartDate: LocalDate,
        cycleEndDateExclusive: LocalDate,
        detectedAtTimestamp: Long
    ) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.PENDING_CYCLE_START_DATE] = cycleStartDate.toString()
            preferences[PreferenceKeys.PENDING_CYCLE_END_DATE_EXCLUSIVE] = cycleEndDateExclusive.toString()
            preferences[PreferenceKeys.PENDING_CYCLE_DETECTED_AT_TIMESTAMP] = detectedAtTimestamp
        }
    }

    override suspend fun clearPendingCycle() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferenceKeys.PENDING_CYCLE_START_DATE)
            preferences.remove(PreferenceKeys.PENDING_CYCLE_END_DATE_EXCLUSIVE)
            preferences.remove(PreferenceKeys.PENDING_CYCLE_DETECTED_AT_TIMESTAMP)
        }
    }

}
