package net.loeu.wallybudget.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.loeu.wallybudget.data.model.UserSettings

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

class UserPreferencesManager(private val context: Context) {

    private object PreferenceKeys {
        val MONTHLY_BUDGET = doublePreferencesKey("monthly_budget")
        val PAYDAY_DATE = intPreferencesKey("payday_date")
        val LAST_RESET_TIMESTAMP = longPreferencesKey("last_reset_timestamp")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    val userSettings: Flow<UserSettings> = context.dataStore.data.map { preferences ->
        UserSettings(
            monthlyBudget = preferences[PreferenceKeys.MONTHLY_BUDGET] ?: 0.0,
            paydayDate = preferences[PreferenceKeys.PAYDAY_DATE] ?: 1,
            lastResetTimestamp = preferences[PreferenceKeys.LAST_RESET_TIMESTAMP] ?: 0L,
            isOnboardingCompleted = preferences[PreferenceKeys.ONBOARDING_COMPLETED] ?: false
        )
    }

    suspend fun updateMonthlyBudget(amount: Double) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.MONTHLY_BUDGET] = amount
        }
    }

    suspend fun updatePaydayDate(day: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.PAYDAY_DATE] = day
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

    suspend fun updateSettings(settings: UserSettings) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.MONTHLY_BUDGET] = settings.monthlyBudget
            preferences[PreferenceKeys.PAYDAY_DATE] = settings.paydayDate
            preferences[PreferenceKeys.LAST_RESET_TIMESTAMP] = settings.lastResetTimestamp
            preferences[PreferenceKeys.ONBOARDING_COMPLETED] = settings.isOnboardingCompleted
        }
    }
}

