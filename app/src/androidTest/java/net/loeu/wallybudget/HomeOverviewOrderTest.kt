package net.loeu.wallybudget

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.ExpenseDaySection
import net.loeu.wallybudget.domain.model.SpendingForecast
import net.loeu.wallybudget.ui.screens.overview.OverviewPage
import net.loeu.wallybudget.ui.theme.WallyBudgetTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class HomeOverviewOrderTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun overview_sections_follow_summary_forecast_spending_today_order() {
        val today = LocalDate.of(2026, 3, 7)
        val todayExpense = Expense(
            id = 1L,
            amountCents = 1299L,
            description = "Lunch",
            timestamp = Instant.parse("2026-03-07T12:00:00Z").toEpochMilli(),
            expenseDate = today.toString()
        )

        composeRule.setContent {
            WallyBudgetTheme {
                OverviewPage(
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
                    effectiveCurrentDate = today,
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
                    onEditTodayExpense = {},
                    headerTitle = "Groceries",
                    headerSettingsAction = {}
                )
            }
        }

        composeRule.onNodeWithTag("home_summary_section").assertIsDisplayed()
        composeRule.onNodeWithTag("home_forecast_section").assertIsDisplayed()
        composeRule.onNodeWithTag("home_spending_today_section").assertIsDisplayed()
        composeRule.onNodeWithTag("home_today_expenses_section").fetchSemanticsNode()

        val summaryTop = composeRule.onNodeWithTag("home_summary_section").getBoundsInRoot().top
        val forecastTop = composeRule.onNodeWithTag("home_forecast_section").getBoundsInRoot().top
        val spendingTop = composeRule.onNodeWithTag("home_spending_today_section").getBoundsInRoot().top
        val todayTop = composeRule.onNodeWithTag("home_today_expenses_section").getBoundsInRoot().top

        assertTrue(summaryTop < forecastTop)
        assertTrue(forecastTop < spendingTop)
        assertTrue(spendingTop < todayTop)
    }

    @Test
    fun overview_header_title_remains_visible_when_summary_collapses() {
        val today = LocalDate.of(2026, 3, 7)

        composeRule.setContent {
            WallyBudgetTheme {
                OverviewPage(
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
                    effectiveCurrentDate = today,
                    todayExpenses = emptyList(),
                    activeCycleExpenseSections = List(20) { index ->
                        ExpenseDaySection(
                            date = today.minusDays(index.toLong()),
                            expenses = emptyList(),
                            totalSpentCents = 0L,
                            remainingForDayCents = 9_500L,
                            isEditable = true
                        )
                    },
                    spendingForecast = SpendingForecast(),
                    onEditTodayExpense = {},
                    headerTitle = "Groceries",
                    headerSettingsAction = {}
                )
            }
        }

        composeRule.onNodeWithTag("home_page_header_title").assertIsDisplayed()

        composeRule.onRoot().performTouchInput {
            swipeUp()
            swipeUp()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("home_page_header_title").assertIsDisplayed()
    }
}
