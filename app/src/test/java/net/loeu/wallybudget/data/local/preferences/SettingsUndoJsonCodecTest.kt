package net.loeu.wallybudget.data.local.preferences

import net.loeu.wallybudget.domain.model.BudgetAdjustment
import net.loeu.wallybudget.domain.model.BudgetPolicy
import net.loeu.wallybudget.domain.model.PendingSettingsUndo
import net.loeu.wallybudget.domain.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class SettingsUndoJsonCodecTest {

    private val codec = SettingsUndoJsonCodec()

    @Test
    fun encode_omitsLazyDelegateFields() {
        val encoded = codec.encode(samplePendingUndo())

        assertFalse(encoded.contains("parsedExpiryDate\$delegate"))
        assertFalse(encoded.contains("parsedCycleStart\$delegate"))
        assertFalse(encoded.contains("parsedCycleEnd\$delegate"))
        assertFalse(encoded.contains("parsedEffectiveDate\$delegate"))
    }

    @Test
    fun decodeOrNull_readsLegacyPayloadWithDelegateFields() {
        val decoded = codec.decodeOrNull(LEGACY_PENDING_UNDO_JSON)

        assertNotNull(decoded)
        assertEquals("2026-12-23", decoded?.expiresAtExclusive)
        assertEquals(100_000L, decoded?.previousSettings?.monthlyBudgetCents)
        assertEquals(23, decoded?.previousSettings?.paydayDate)
        assertEquals(1, decoded?.policiesToRestore?.size)
        assertEquals(1, decoded?.policiesToDeactivate?.size)
        assertEquals(1, decoded?.adjustmentsToDeactivate?.size)
    }

    private fun samplePendingUndo(): PendingSettingsUndo {
        return PendingSettingsUndo(
            previousSettings = UserSettings(
                monthlyBudgetCents = 100_000L,
                paydayDate = 25,
                isOnboardingCompleted = true,
                installDeviceId = "device-1",
                settingsRecordUuid = "settings-1",
                settingsUpdatedAtEpochMs = 1_796_876_106_059L,
                settingsModClock = "1796945922618-0001-device-1",
                settingsLastModifiedByInstallId = "device-1"
            ),
            policiesToRestore = listOf(
                BudgetPolicy(
                    policyUuid = "policy-restore",
                    cycleStartDate = "2026-12-23",
                    cycleEndDateExclusive = "2027-01-23",
                    budgetAmountCents = 100_000L,
                    paydayDayOfMonth = 23,
                    originInstallId = "device-1",
                    lastModifiedByInstallId = "device-1",
                    createdAtEpochMs = 1_796_857_200_000L,
                    updatedAtEpochMs = 1_796_857_200_000L,
                    modClock = "1796857200000-0000-device-1"
                )
            ),
            policiesToDeactivate = listOf(
                BudgetPolicy(
                    policyUuid = "policy-deactivate",
                    cycleStartDate = "2026-12-23",
                    cycleEndDateExclusive = "2027-01-23",
                    budgetAmountCents = 100_002L,
                    paydayDayOfMonth = 23,
                    originInstallId = "device-1",
                    lastModifiedByInstallId = "device-1",
                    createdAtEpochMs = 1_796_857_200_000L,
                    updatedAtEpochMs = 1_796_857_200_000L,
                    modClock = "1796857200000-0000-device-1"
                )
            ),
            adjustmentsToRestore = emptyList(),
            adjustmentsToDeactivate = listOf(
                BudgetAdjustment(
                    adjustmentUuid = "adjustment-1",
                    cycleStartDate = "2026-11-25",
                    effectiveDate = "2026-12-10",
                    previousMonthlyBudgetCents = 100_000L,
                    newMonthlyBudgetCents = 100_002L,
                    originInstallId = "device-1",
                    lastModifiedByInstallId = "device-1",
                    createdAtEpochMs = 1_796_876_110_183L,
                    updatedAtEpochMs = 1_796_876_110_183L,
                    modClock = "1796876110183-0000-device-1"
                )
            ),
            expiresAtExclusive = "2026-12-23"
        )
    }

    private companion object {
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
