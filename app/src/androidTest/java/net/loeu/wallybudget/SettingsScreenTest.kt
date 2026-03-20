package net.loeu.wallybudget

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.BucketBalanceBehavior
import net.loeu.wallybudget.domain.model.BucketSummaryState
import net.loeu.wallybudget.domain.model.BucketTrackingMode
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.usecase.BucketDraft
import net.loeu.wallybudget.ui.screens.settings.SettingsScreen
import net.loeu.wallybudget.ui.theme.WallyBudgetTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun settings_screen_portfolio_plan_save_updates_budget_only() {
        val saveCalls = mutableListOf<Pair<Long, List<BucketDraft>>>()

        setSettingsScreenContent(
            onSavePortfolioPlan = { budgetCents, buckets ->
                saveCalls += budgetCents to buckets
            }
        )

        composeRule.onNodeWithTag("settings_budget_input").performTextReplacement("1200.00")
        composeRule.onNodeWithTag("settings_plan_save_button").performClick()

        assertEquals(1, saveCalls.size)
        assertEquals(120_000L, saveCalls.single().first)
    }

    @Test
    fun settings_screen_payday_only_change_saves_separately() {
        val saveCalls = mutableListOf<Int>()

        setSettingsScreenContent(
            onSavePayday = { paydayDate ->
                saveCalls += paydayDate
            }
        )

        composeRule.onNodeWithTag("settings_payday_input").performTextReplacement("1")
        composeRule.onNodeWithTag("settings_payday_save_button").performClick()

        assertEquals(1, saveCalls.size)
        assertEquals(1, saveCalls.single())
    }

    @Test
    fun settings_screen_shows_payday_undo_action_when_available() {
        var undoCalls = 0

        setSettingsScreenContent(
            onSavePortfolioPlan = { _, _ -> },
            onUndoPaydayChange = { undoCalls += 1 },
            isSettingsUndoAvailable = true,
            settingsUndoExpiresAtExclusive = LocalDate.of(2026, 12, 25)
        )

        composeRule.onNodeWithText("Payday change undo").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_undo_button").performClick()

        assertEquals(1, undoCalls)
    }

    private fun setSettingsScreenContent(
        onSavePortfolioPlan: (Long, List<BucketDraft>) -> Unit = { _, _ -> },
        onSavePayday: (Int) -> Unit = {},
        onUndoPaydayChange: () -> Unit = {},
        isSettingsUndoAvailable: Boolean = false,
        settingsUndoExpiresAtExclusive: LocalDate? = null
    ) {
        val allBuckets = listOf(
            BudgetBucket(
                bucketUuid = "bucket-1",
                name = "Groceries",
                trackingMode = BucketTrackingMode.CYCLE_RESERVE,
                balanceBehavior = BucketBalanceBehavior.RETURN_TO_PORTFOLIO,
                defaultAllocatedAmountCents = 100_000L,
                sortOrder = 0,
                isPrimary = true,
                originInstallId = "test-install",
                lastModifiedByInstallId = "test-install",
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L,
                modClock = "clock-1"
            )
        )
        val bucketSummaries = listOf(
            BucketSummaryState(
                bucket = allBuckets.single(),
                allocatedThisCycleCents = 100_000L,
                spentThisCycleCents = 40_000L,
                remainingThisCycleCents = 60_000L,
                overspentCents = 0L,
                earmarkedBalanceCents = 0L
            )
        )
        composeRule.setContent {
            WallyBudgetTheme {
                SettingsScreen(
                    userSettings = UserSettings(
                        monthlyBudgetCents = 100_000L,
                        paydayDate = 25,
                        isOnboardingCompleted = true
                    ),
                    allBuckets = allBuckets,
                    bucketSummaries = bucketSummaries,
                    currentDate = LocalDate.of(2026, 12, 4),
                    onSavePortfolioPlan = onSavePortfolioPlan,
                    onSavePayday = onSavePayday,
                    onUndoPaydayChange = onUndoPaydayChange,
                    isSettingsUndoAvailable = isSettingsUndoAvailable,
                    settingsUndoExpiresAtExclusive = settingsUndoExpiresAtExclusive,
                    onSettingsMessageConsumed = {},
                    onRequestExportSnapshot = {},
                    settingsMessage = null,
                    snapshotMessage = null,
                    snapshotErrorMessage = null,
                    isSnapshotBusy = false,
                    onNavigateBack = {}
                )
            }
        }
    }
}
