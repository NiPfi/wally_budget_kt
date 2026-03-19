package net.loeu.wallybudget.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsFormValidationTest {

    @Test
    fun validateSettingsForm_rejectsInvalidBudget() {
        val result = validateSettingsForm(
            budgetText = "0",
            paydayText = "5"
        )

        assertFalse(result.isValid)
        assertFalse(result.isBudgetValid)
        assertTrue(result.isPaydayValid)
    }

    @Test
    fun validateSettingsForm_rejectsInvalidPayday() {
        val result = validateSettingsForm(
            budgetText = "1200",
            paydayText = ""
        )

        assertFalse(result.isValid)
        assertTrue(result.isBudgetValid)
        assertFalse(result.isPaydayValid)
    }

    @Test
    fun validateSettingsForm_acceptsValidBudgetAndPayday() {
        val result = validateSettingsForm(
            budgetText = "1200",
            paydayText = "5"
        )

        assertTrue(result.isValid)
        assertEquals(120_000L, result.budgetCents)
        assertEquals(5, result.payday)
    }

    @Test
    fun shouldSyncSettingsDrafts_isFalseWhenUserHasUnsavedBucketChanges() {
        val currentDrafts = listOf(
            EditableBucketUi(
                bucketUuid = "bucket-1",
                name = "Test",
                trackingMode = net.loeu.wallybudget.domain.model.BucketTrackingMode.DAILY_TARGET,
                balanceBehavior = net.loeu.wallybudget.domain.model.BucketBalanceBehavior.RETURN_TO_PORTFOLIO,
                amountText = "15.0",
                sortOrder = 0,
                isPrimary = true,
                closeRequested = false,
                existingClosed = false
            )
        )
        val externalDrafts = listOf(currentDrafts.single().copy(amountText = "0.0"))

        val shouldSync = shouldSyncSettingsDrafts(
            currentBudgetText = "4500.0",
            currentPaydayText = "25",
            currentBucketDrafts = currentDrafts,
            externalBudgetText = "4500.0",
            externalPaydayText = "25",
            externalBucketDrafts = externalDrafts,
            isEditorOpen = false
        )

        assertFalse(shouldSync)
    }

    @Test
    fun shouldSyncSettingsDrafts_isTrueWhenDraftsAlreadyMatchExternalState() {
        val externalDrafts = listOf(
            EditableBucketUi(
                bucketUuid = "bucket-1",
                name = "Test",
                trackingMode = net.loeu.wallybudget.domain.model.BucketTrackingMode.DAILY_TARGET,
                balanceBehavior = net.loeu.wallybudget.domain.model.BucketBalanceBehavior.RETURN_TO_PORTFOLIO,
                amountText = "15.0",
                sortOrder = 0,
                isPrimary = true,
                closeRequested = false,
                existingClosed = false
            )
        )

        val shouldSync = shouldSyncSettingsDrafts(
            currentBudgetText = "4500.0",
            currentPaydayText = "25",
            currentBucketDrafts = externalDrafts,
            externalBudgetText = "4500.0",
            externalPaydayText = "25",
            externalBucketDrafts = externalDrafts,
            isEditorOpen = false
        )

        assertTrue(shouldSync)
    }
}
