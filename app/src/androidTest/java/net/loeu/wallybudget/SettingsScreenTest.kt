package net.loeu.wallybudget

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.loeu.wallybudget.domain.model.BudgetChangeMode
import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.domain.model.UserSettings
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
    fun settings_screen_budget_change_saves_immediately_with_proration() {
        val saveCalls = mutableListOf<Triple<Long, Int, BudgetChangeMode>>()

        setSettingsScreenContent(
            onSaveSettings = { budgetCents, paydayDate, mode ->
                saveCalls += Triple(budgetCents, paydayDate, mode)
            }
        )

        composeRule.onNodeWithTag("settings_budget_input").performTextReplacement("1200.00")
        composeRule.onNodeWithTag("settings_save_button").performClick()

        assertEquals(1, saveCalls.size)
        assertEquals(Triple(120_000L, 25, BudgetChangeMode.PRORATE_CURRENT_CYCLE), saveCalls.single())
    }

    @Test
    fun settings_screen_payday_only_change_saves_immediately() {
        val saveCalls = mutableListOf<Triple<Long, Int, BudgetChangeMode>>()

        setSettingsScreenContent(
            onSaveSettings = { budgetCents, paydayDate, mode ->
                saveCalls += Triple(budgetCents, paydayDate, mode)
            }
        )

        composeRule.onNodeWithTag("settings_payday_input").performTextReplacement("1")
        composeRule.onNodeWithTag("settings_save_button").performClick()

        assertEquals(1, saveCalls.size)
        assertEquals(Triple(100_000L, 1, BudgetChangeMode.PRORATE_CURRENT_CYCLE), saveCalls.single())
    }

    @Test
    fun settings_screen_shows_undo_action_when_available() {
        var undoCalls = 0

        setSettingsScreenContent(
            onSaveSettings = { _, _, _ -> },
            onUndoSettings = { undoCalls += 1 },
            isSettingsUndoAvailable = true,
            settingsUndoExpiresAtExclusive = LocalDate.of(2026, 12, 25)
        )

        composeRule.onNodeWithText("Cycle default available").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_undo_button").performClick()

        assertEquals(1, undoCalls)
    }

    private fun setSettingsScreenContent(
        onSaveSettings: (Long, Int, BudgetChangeMode) -> Unit,
        onUndoSettings: () -> Unit = {},
        isSettingsUndoAvailable: Boolean = false,
        settingsUndoExpiresAtExclusive: LocalDate? = null
    ) {
        composeRule.setContent {
            WallyBudgetTheme {
                SettingsScreen(
                    userSettings = UserSettings(
                        monthlyBudgetCents = 100_000L,
                        paydayDate = 25,
                        isOnboardingCompleted = true
                    ),
                    budgetState = BudgetState(
                        monthlyBudgetCents = 100_000L,
                        totalSpentThisCycleCents = 40_000L,
                        dailyBudgetCents = 3_750L,
                        spentTodayCents = 1_250L,
                        remainingTodayCents = 2_500L,
                        daysRemainingInCycle = 21,
                        cumulativeSavingsCents = 8_000L,
                        paydayDate = 25,
                        cycleStartDate = LocalDate.of(2026, 11, 25)
                    ),
                    currentDate = LocalDate.of(2026, 12, 4),
                    onSaveSettings = onSaveSettings,
                    onUndoSettings = onUndoSettings,
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
