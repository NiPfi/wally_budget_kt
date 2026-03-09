package net.loeu.wallybudget

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.loeu.wallybudget.data.model.BudgetState
import net.loeu.wallybudget.data.model.Expense
import net.loeu.wallybudget.data.model.ExpenseDaySection
import net.loeu.wallybudget.data.model.SpendingForecast
import net.loeu.wallybudget.ui.screens.home.WideHomeContent
import net.loeu.wallybudget.ui.theme.WallyBudgetTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class WideHomeContentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun wide_home_content_places_spending_details_in_right_pane() {
        val today = LocalDate.of(2026, 3, 7)
        val todayExpense = Expense(
            id = 1L,
            amountCents = 1_299L,
            description = "Lunch",
            timestamp = Instant.parse("2026-03-07T12:00:00Z").toEpochMilli(),
            expenseDate = today.toString()
        )

        composeRule.setContent {
            WallyBudgetTheme {
                WideHomeContent(
                    budgetState = BudgetState(
                        monthlyBudgetCents = 250_000L,
                        totalSpentThisCycleCents = 83_000L,
                        dailyBudgetCents = 9_500L,
                        spentTodayCents = 1_299L,
                        remainingTodayCents = 8_201L,
                        daysRemainingInCycle = 18,
                        cumulativeSavingsCents = 12_000L,
                        paydayDate = 1,
                        cycleStartDate = today.minusDays(12)
                    ),
                    todayExpenses = listOf(todayExpense),
                    activeCycleExpenseSections = listOf(
                        ExpenseDaySection(
                            date = today,
                            expenses = listOf(todayExpense),
                            totalSpentCents = todayExpense.amountCents,
                            remainingForDayCents = 8_201L,
                            isToday = true,
                            isEditable = true
                        ),
                        ExpenseDaySection(
                            date = today.minusDays(1),
                            expenses = emptyList(),
                            totalSpentCents = 0L,
                            remainingForDayCents = 9_500L,
                            isEditable = true
                        )
                    ),
                    spendingForecast = SpendingForecast(),
                    isLoading = false,
                    onEditTodayExpense = {},
                    onNavigateToSettings = null,
                    preferCompactSummary = false,
                    overviewBottomContentPadding = 24.dp,
                    detailsBottomContentPadding = 72.dp
                )
            }
        }

        composeRule.onNodeWithTag("home_landscape_left_pane").assertIsDisplayed()
        composeRule.onNodeWithTag("home_landscape_right_pane").assertIsDisplayed()
        composeRule.onNodeWithTag("home_summary_section").assertIsDisplayed()
        composeRule.onNodeWithTag("home_forecast_section").assertIsDisplayed()
        composeRule.onNodeWithTag("home_summary_secondary_metrics").assertIsDisplayed()
        composeRule.onNodeWithTag("home_spending_today_section").assertIsDisplayed()
        composeRule.onNodeWithTag("home_today_expenses_section").assertIsDisplayed()

        val leftPaneBounds = composeRule.onNodeWithTag("home_landscape_left_pane").getBoundsInRoot()
        val rightPaneBounds = composeRule.onNodeWithTag("home_landscape_right_pane").getBoundsInRoot()
        val summaryBounds = composeRule.onNodeWithTag("home_summary_section").getBoundsInRoot()
        val spendingBounds = composeRule.onNodeWithTag("home_spending_today_section").getBoundsInRoot()

        assertTrue(leftPaneBounds.left < rightPaneBounds.left)
        assertTrue(summaryBounds.left < spendingBounds.left)
    }
}
