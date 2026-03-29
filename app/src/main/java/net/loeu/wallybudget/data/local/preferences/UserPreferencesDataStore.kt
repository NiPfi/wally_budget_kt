package net.loeu.wallybudget.data.local.preferences

import android.content.Context
import androidx.datastore.dataStore

internal val Context.userPreferencesDataStore by dataStore(
    fileName = "user_settings.json",
    serializer = UserPreferencesSerializer,
    produceMigrations = { applicationContext ->
        listOf(LegacyUserPreferencesMigration.fromContext(applicationContext))
    }
)
