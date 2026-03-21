package net.loeu.wallybudget

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.BucketBalanceBehavior
import net.loeu.wallybudget.domain.model.BucketSummaryState
import net.loeu.wallybudget.domain.model.BucketTrackingMode
import net.loeu.wallybudget.domain.model.SelectedBucketOverview
import net.loeu.wallybudget.domain.model.SpendingForecast
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.ui.screens.home.HomeScreen
import net.loeu.wallybudget.ui.theme.WallyBudgetTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class HomeScreenHeaderIntegrationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun home_screen_merged_header_moves_with_page_and_keeps_contextual_actions() {
        composeRule.setContent {
            val bucketSummaries = remember { listOf(testBucketSummary("bucket-1", "Groceries"), testBucketSummary("bucket-2", "Travel")) }
            var selectedBucketUuid by remember { mutableStateOf("bucket-1") }
            var analysisOpened by remember { mutableStateOf(false) }

            WallyBudgetTheme {
                HomeScreen(
                    bucketSummaries = bucketSummaries,
                    selectedBucketOverview = selectedBucketOverview(
                        bucketSummaries = bucketSummaries,
                        bucketUuid = selectedBucketUuid
                    ),
                    allBuckets = bucketSummaries.map { it.bucket },
                    userSettings = UserSettings(
                        monthlyBudgetCents = 250_000L,
                        portfolioMonthlyBudgetCents = 250_000L,
                        paydayDate = 1,
                        selectedBucketUuid = selectedBucketUuid
                    ),
                    currentDate = LocalDate.of(2026, 3, 7),
                    spendingForecast = null,
                    onSelectBucket = { selectedBucketUuid = it },
                    onSavePortfolioPlan = { _, _ -> },
                    onAddExpense = { _, _, _, _, _ -> },
                    onRestoreExpense = {},
                    onUpdateExpense = {},
                    onDeleteExpense = {},
                    onNavigateToAnalysis = { analysisOpened = true },
                    showTopRightSettingsAction = true,
                    showAddExpenseSheet = false,
                    onShowAddExpenseSheet = {},
                    onHideAddExpenseSheet = {},
                    settingsMessage = null,
                    onSettingsMessageConsumed = {}
                )
                if (analysisOpened) {
                    androidx.compose.material3.Text("Analysis opened")
                }
            }
        }

        composeRule.onNodeWithTag("home_page_header_title").assertIsDisplayed()
        composeRule.onNodeWithTag("home_page_header_analysis").assertIsDisplayed()
        composeRule.onNodeWithTag("home_page_header_settings").assertIsDisplayed()

        composeRule.onRoot().performTouchInput {
            swipeLeft()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("home_page_header_analysis").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Analysis opened").assertIsDisplayed()

        composeRule.onNodeWithTag("home_page_header_settings").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Bucket settings").assertIsDisplayed()
    }

    @Test
    fun home_screen_bucket_switch_loading_uses_placeholders_instead_of_loading_text() {
        composeRule.setContent {
            val bucketSummaries = remember {
                listOf(
                    testBucketSummary("bucket-1", "Groceries", trackingMode = BucketTrackingMode.DAILY_TARGET),
                    testBucketSummary("bucket-2", "Travel", trackingMode = BucketTrackingMode.DAILY_TARGET)
                )
            }

            WallyBudgetTheme {
                HomeScreen(
                    bucketSummaries = bucketSummaries,
                    selectedBucketOverview = selectedBucketOverview(
                        bucketSummaries = bucketSummaries,
                        bucketUuid = "bucket-1"
                    ),
                    allBuckets = bucketSummaries.map { it.bucket },
                    userSettings = UserSettings(
                        monthlyBudgetCents = 250_000L,
                        portfolioMonthlyBudgetCents = 250_000L,
                        paydayDate = 1,
                        selectedBucketUuid = "bucket-1"
                    ),
                    currentDate = LocalDate.of(2026, 3, 7),
                    spendingForecast = SpendingForecast(),
                    onSelectBucket = {},
                    onSavePortfolioPlan = { _, _ -> },
                    onAddExpense = { _, _, _, _, _ -> },
                    onRestoreExpense = {},
                    onUpdateExpense = {},
                    onDeleteExpense = {},
                    onNavigateToAnalysis = {},
                    showTopRightSettingsAction = true,
                    showAddExpenseSheet = false,
                    onShowAddExpenseSheet = {},
                    onHideAddExpenseSheet = {},
                    settingsMessage = null,
                    onSettingsMessageConsumed = {}
                )
            }
        }

        composeRule.onRoot().performTouchInput {
            swipeLeft()
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("Loading Travel…").assertCountEquals(0)
        composeRule.onNodeWithTag("home_today_expenses_section").assertIsDisplayed()
    }

    private fun selectedBucketOverview(
        bucketSummaries: List<BucketSummaryState>,
        bucketUuid: String
    ): SelectedBucketOverview {
        val summary = bucketSummaries.first { it.bucket.bucketUuid == bucketUuid }
        return SelectedBucketOverview(
            bucket = summary.bucket,
            summary = summary,
            budgetState = null,
            todayExpenses = emptyList(),
            activeCycleExpenseSections = emptyList(),
            spendingForecast = null
        )
    }

    private fun testBucketSummary(
        bucketUuid: String,
        name: String,
        trackingMode: BucketTrackingMode = BucketTrackingMode.CYCLE_RESERVE
    ) = BucketSummaryState(
        bucket = BudgetBucket(
            bucketUuid = bucketUuid,
            name = name,
            trackingMode = trackingMode,
            balanceBehavior = BucketBalanceBehavior.RETURN_TO_PORTFOLIO,
            defaultAllocatedAmountCents = 50_000L,
            sortOrder = if (bucketUuid == "bucket-1") 1 else 2,
            originInstallId = "test-install",
            lastModifiedByInstallId = "test-install",
            createdAtEpochMs = if (bucketUuid == "bucket-1") 1L else 2L,
            updatedAtEpochMs = if (bucketUuid == "bucket-1") 1L else 2L,
            modClock = "clock-$bucketUuid"
        ),
        allocatedThisCycleCents = 50_000L,
        spentThisCycleCents = 12_500L,
        remainingThisCycleCents = 37_500L,
        overspentCents = 0L,
        earmarkedBalanceCents = 0L,
        budgetState = testBudgetState()
    )

    private fun testBudgetState() = net.loeu.wallybudget.domain.model.BudgetState(
        monthlyBudgetCents = 50_000L,
        totalSpentThisCycleCents = 12_500L,
        dailyBudgetCents = 2_500L,
        spentTodayCents = 500L,
        remainingTodayCents = 2_000L,
        daysRemainingInCycle = 12,
        cumulativeSavingsCents = 0L,
        paydayDate = 1,
        cycleStartDate = LocalDate.of(2026, 3, 1)
    )
}
