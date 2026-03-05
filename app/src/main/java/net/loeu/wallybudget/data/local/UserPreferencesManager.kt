package net.loeu.wallybudget.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.loeu.wallybudget.data.model.UserSettings

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

private const val FORECAST_SENSITIVITY_MIN = 20
private const val FORECAST_SENSITIVITY_MAX = 90
private const val FORECAST_SENSITIVITY_DEFAULT = 60

class UserPreferencesManager(private val context: Context) {

    private object PreferenceKeys {
        val MONTHLY_BUDGET_CENTS = longPreferencesKey("monthly_budget_cents")
        val PAYDAY_DATE = intPreferencesKey("payday_date")
        val FORECAST_SENSITIVITY_PERCENT = intPreferencesKey("forecast_sensitivity_percent")
        val LAST_RESET_TIMESTAMP = longPreferencesKey("last_reset_timestamp")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    val userSettings: Flow<UserSettings> = context.dataStore.data.map { preferences ->
        UserSettings(
            monthlyBudgetCents = preferences[PreferenceKeys.MONTHLY_BUDGET_CENTS] ?: 0L,
            paydayDate = preferences[PreferenceKeys.PAYDAY_DATE] ?: 1,
            forecastSensitivityPercent = (preferences[PreferenceKeys.FORECAST_SENSITIVITY_PERCENT]
                ?: FORECAST_SENSITIVITY_DEFAULT)
                .coerceIn(FORECAST_SENSITIVITY_MIN, FORECAST_SENSITIVITY_MAX),
            lastResetTimestamp = preferences[PreferenceKeys.LAST_RESET_TIMESTAMP] ?: 0L,
            isOnboardingCompleted = preferences[PreferenceKeys.ONBOARDING_COMPLETED] ?: false
        )
    }

    suspend fun updateMonthlyBudget(amountCents: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.MONTHLY_BUDGET_CENTS] = amountCents
        }
    }

    suspend fun updatePaydayDate(day: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.PAYDAY_DATE] = day
        }
    }

    suspend fun updateForecastSensitivityPercent(percent: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.FORECAST_SENSITIVITY_PERCENT] =
                percent.coerceIn(FORECAST_SENSITIVITY_MIN, FORECAST_SENSITIVITY_MAX)
        }
    }

    suspend fun updateLastResetTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.LAST_RESET_TIMESTAMP] = timestamp
        }
    }

    suspend fun completeOnboarding() {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.ONBOARDING_COMPLETED] = true
        }
    }

}

