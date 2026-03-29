package net.loeu.wallybudget.data.local.preferences

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files

class UserPreferencesStoreMigrationTest {

    private val legacyDecoder = LegacyPaydayUndoJsonDecoder()

    @Test
    fun serializer_roundTripsTypedState() = runBlocking {
        val encoded = ByteArrayOutputStream()

        UserPreferencesSerializer.writeTo(sampleState(), encoded)
        val decoded = UserPreferencesSerializer.readFrom(
            ByteArrayInputStream(encoded.toByteArray())
        )

        assertEquals(sampleState(), decoded)
    }

    @Test
    fun legacyDecoder_readsLegacyPayloadWithDelegateFields() {
        val decoded = legacyDecoder.decodeOrNull(LEGACY_PENDING_UNDO_JSON)

        assertNotNull(decoded)
        assertEquals("2026-12-23", decoded?.expiresAtExclusive)
        assertEquals(100_000L, decoded?.previousSettings?.monthlyBudgetCents)
        assertEquals(23, decoded?.previousSettings?.paydayDate)
        assertEquals(1, decoded?.policiesToRestore?.size)
        assertEquals(1, decoded?.policiesToDeactivate?.size)
        assertEquals(1, decoded?.adjustmentsToDeactivate?.size)
    }

    @Test
    fun legacyDecoder_returnsNullWhenRequiredFieldsAreMissing() {
        val decoded = legacyDecoder.decodeOrNull(
            """
            {
              "previousSettings": {},
              "policiesToRestore": [
                {
                  "policyUuid": null
                }
              ],
              "expiresAtExclusive": "2026-12-23"
            }
            """.trimIndent()
        )

        assertNull(decoded)
    }

    @Test
    fun migration_movesLegacyPreferencesIntoTypedStore_andDeletesLegacyFiles() = runBlocking {
        val tempDir = Files.createTempDirectory("user-preferences-migration").toFile()
        val legacyFile = tempDir.resolve("user_settings.preferences_pb")
        val legacyBackup = tempDir.resolve("user_settings.preferences_pb.bak")
        val targetFile = tempDir.resolve("user_settings.json")
        legacyBackup.writeText("legacy backup")

        val legacyStore = PreferenceDataStoreFactory.create(
            produceFile = { legacyFile }
        )
        legacyStore.edit { preferences ->
            preferences[MONTHLY_BUDGET_CENTS] = 120_000L
            preferences[PORTFOLIO_MONTHLY_BUDGET_CENTS] = 140_000L
            preferences[PAYDAY_DATE] = 23
            preferences[LAST_RESET_TIMESTAMP] = 1_795_561_200_000L
            preferences[LAST_SEEN_DATE] = "2026-12-10"
            preferences[ONBOARDING_COMPLETED] = true
            preferences[PENDING_CYCLE_START_DATE] = "2026-12-23"
            preferences[PENDING_CYCLE_END_DATE_EXCLUSIVE] = "2027-01-23"
            preferences[PENDING_CYCLE_DETECTED_AT_TIMESTAMP] = 1_796_876_106_059L
            preferences[SELECTED_BUCKET_UUID] = "bucket-1"
            preferences[INSTALL_DEVICE_ID] = "device-1"
            preferences[SETTINGS_RECORD_UUID] = "settings-1"
            preferences[SETTINGS_UPDATED_AT_EPOCH_MS] = 1_796_876_106_059L
            preferences[SETTINGS_MOD_CLOCK] = "1796945922618-0001-device-1"
            preferences[SETTINGS_LAST_MODIFIED_BY_INSTALL_ID] = "device-1"
            preferences[PENDING_SETTINGS_UNDO_JSON] = LEGACY_PENDING_UNDO_JSON
        }

        val typedStore = DataStoreFactory.create(
            serializer = UserPreferencesSerializer,
            migrations = listOf(
                LegacyUserPreferencesMigration.createForTest(
                    legacyDataStore = legacyStore,
                    legacyPreferencesFile = legacyFile
                )
            ),
            produceFile = { targetFile }
        )

        val migrated = typedStore.data.first()

        assertEquals(120_000L, migrated.settings.monthlyBudgetCents)
        assertEquals(140_000L, migrated.settings.portfolioMonthlyBudgetCents)
        assertEquals(23, migrated.settings.paydayDate)
        assertEquals("bucket-1", migrated.settings.selectedBucketUuid)
        assertEquals("device-1", migrated.settings.installDeviceId)
        assertNotNull(migrated.pendingPaydayUndo)
        assertEquals("2026-12-23", migrated.pendingPaydayUndo?.expiresAtExclusive)
        assertFalse(legacyFile.exists())
        assertFalse(legacyBackup.exists())
    }

    @Test
    fun migration_discardsInvalidLegacyUndoJson() = runBlocking {
        val tempDir = Files.createTempDirectory("user-preferences-invalid-undo").toFile()
        val legacyFile = tempDir.resolve("user_settings.preferences_pb")
        val targetFile = tempDir.resolve("user_settings.json")

        val legacyStore = PreferenceDataStoreFactory.create(
            produceFile = { legacyFile }
        )
        legacyStore.edit { preferences ->
            preferences[MONTHLY_BUDGET_CENTS] = 90_000L
            preferences[PENDING_SETTINGS_UNDO_JSON] = """{"previousSettings": "bad"}"""
        }

        val typedStore = DataStoreFactory.create(
            serializer = UserPreferencesSerializer,
            migrations = listOf(
                LegacyUserPreferencesMigration.createForTest(
                    legacyDataStore = legacyStore,
                    legacyPreferencesFile = legacyFile
                )
            ),
            produceFile = { targetFile }
        )

        val migrated = typedStore.data.first()

        assertEquals(90_000L, migrated.settings.monthlyBudgetCents)
        assertNull(migrated.pendingPaydayUndo)
        assertTrue(targetFile.exists())
    }

    private fun sampleState(): UserPreferencesState {
        return UserPreferencesState(
            settings = StoredUserSettingsState(
                monthlyBudgetCents = 100_000L,
                portfolioMonthlyBudgetCents = 150_000L,
                paydayDate = 25,
                lastResetTimestamp = 1_795_561_200_000L,
                lastSeenDate = "2026-12-10",
                isOnboardingCompleted = true,
                pendingCycleStartDate = "2026-12-23",
                pendingCycleEndDateExclusive = "2027-01-23",
                pendingCycleDetectedAtTimestamp = 1_796_876_106_059L,
                selectedBucketUuid = "bucket-1",
                installDeviceId = "device-1",
                settingsRecordUuid = "settings-1",
                settingsUpdatedAtEpochMs = 1_796_876_106_059L,
                settingsModClock = "1796945922618-0001-device-1",
                settingsLastModifiedByInstallId = "device-1"
            ),
            pendingPaydayUndo = legacyDecoder.decodeOrNull(LEGACY_PENDING_UNDO_JSON)
        )
    }

    private companion object {
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

        val LEGACY_PENDING_UNDO_JSON = """
            {
              "adjustmentsToDeactivate": [
                {
                  "adjustmentUuid": "a9531427-7f57-40d1-a836-a339bd4267fc",
                  "createdAtEpochMs": 1796876110183,
                  "cycleStartDate": "2026-11-25",
                  "effectiveDate": "2026-12-10",
                  "lastModifiedByInstallId": "device-1",
                  "modClock": "1796876110183-0000-device-1",
                  "newMonthlyBudgetCents": 100002,
                  "originInstallId": "device-1",
                  "parsedCycleStart${'$'}delegate": { "_value": {}, "initializer": {} },
                  "parsedEffectiveDate${'$'}delegate": { "_value": {}, "initializer": {} },
                  "previousMonthlyBudgetCents": 100000,
                  "updatedAtEpochMs": 1796876110183
                }
              ],
              "adjustmentsToRestore": [],
              "expiresAtExclusive": "2026-12-23",
              "parsedExpiryDate${'$'}delegate": { "_value": {}, "initializer": {} },
              "policiesToDeactivate": [
                {
                  "budgetAmountCents": 100002,
                  "createdAtEpochMs": 1796857200000,
                  "cycleEndDateExclusive": "2027-01-23",
                  "cycleStartDate": "2026-12-23",
                  "lastModifiedByInstallId": "device-1",
                  "modClock": "1796857200000-0000-device-1",
                  "originInstallId": "device-1",
                  "parsedCycleEnd${'$'}delegate": { "_value": {}, "initializer": {} },
                  "parsedCycleStart${'$'}delegate": { "_value": {}, "initializer": {} },
                  "paydayDayOfMonth": 23,
                  "policyUuid": "24a96e06-d8f9-4bf9-b164-040125f2a282",
                  "updatedAtEpochMs": 1796857200000
                }
              ],
              "policiesToRestore": [
                {
                  "budgetAmountCents": 100000,
                  "createdAtEpochMs": 1796857200000,
                  "cycleEndDateExclusive": "2027-01-23",
                  "cycleStartDate": "2026-12-23",
                  "lastModifiedByInstallId": "device-1",
                  "modClock": "1796857200000-0000-device-1",
                  "originInstallId": "device-1",
                  "parsedCycleEnd${'$'}delegate": { "_value": {} },
                  "parsedCycleStart${'$'}delegate": { "_value": {} },
                  "paydayDayOfMonth": 23,
                  "policyUuid": "3537784a-a713-4c2f-92de-f65ea18a3316",
                  "updatedAtEpochMs": 1796857200000
                }
              ],
              "previousSettings": {
                "installDeviceId": "device-1",
                "isOnboardingCompleted": true,
                "lastResetTimestamp": 1795561200000,
                "lastSeenDate": "2026-12-10",
                "monthlyBudgetCents": 100000,
                "paydayDate": 23,
                "pendingCycleDetectedAtTimestamp": 0,
                "settingsLastModifiedByInstallId": "device-1",
                "settingsModClock": "1796945922618-0001-device-1",
                "settingsRecordUuid": "fb2e340a-1965-4c9c-bad5-be8d977183cc",
                "settingsUpdatedAtEpochMs": 1796876106059
              }
            }
        """.trimIndent()
    }
}
