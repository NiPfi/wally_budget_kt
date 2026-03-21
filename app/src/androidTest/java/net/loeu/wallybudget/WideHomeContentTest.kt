package net.loeu.wallybudget

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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

@RunWith(AndroidJUnit4::class)
class WideHomeContentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun overview_page_renders_summary_forecast_and_spending_sections() {
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
        composeRule.onNodeWithTag("home_page_header_row").assertIsDisplayed()
        composeRule.onNodeWithTag("home_forecast_section").assertIsDisplayed()
        composeRule.onNodeWithTag("home_summary_secondary_metrics").assertIsDisplayed()
        composeRule.onNodeWithTag("home_spending_today_section").assertIsDisplayed()
        composeRule.onNodeWithTag("home_today_expenses_section").assertIsDisplayed()
    }
}
