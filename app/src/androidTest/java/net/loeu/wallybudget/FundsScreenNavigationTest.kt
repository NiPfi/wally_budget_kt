package net.loeu.wallybudget

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.BucketSummaryState
import net.loeu.wallybudget.domain.model.DEFAULT_FUND_UUID
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_NAME
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import net.loeu.wallybudget.domain.model.Fund
import net.loeu.wallybudget.domain.model.FundType
import net.loeu.wallybudget.domain.model.PortfolioState
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.ui.screens.funds.FundsScreen
import net.loeu.wallybudget.ui.screens.home.PortfolioScreen
import net.loeu.wallybudget.ui.theme.WallyBudgetTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import androidx.compose.ui.text.AnnotatedString

@RunWith(AndroidJUnit4::class)
class FundsScreenNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tappingViewFundsOpensFundsScreenAndBackReturnsToPortfolio() {
        composeRule.setContent {
            var showFunds by remember { mutableStateOf(false) }

            WallyBudgetTheme {
                if (showFunds) {
                    FundsScreen(
                        funds = funds(),
                        onNavigateBack = { showFunds = false },
                        onCreateGoalFund = { _, _ -> },
                        onUpdateGoalFund = { _, _, _ -> }
                    )
                } else {
                    PortfolioScreen(
                        portfolioState = portfolioState(),
                        bucketSummaries = listOf(bucketSummary()),
                        funds = funds(),
                        allBuckets = listOf(bucket()),
                        userSettings = userSettings(),
                        onSavePortfolioPlan = { _, _ -> },
                        onNavigateToFunds = { showFunds = true },
                        onNavigateToSettings = {},
                        showTopRightSettingsAction = true
                    )
                }
            }
        }

        composeRule.onNodeWithTag("funds_section_card").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Default reserve").assertIsDisplayed()
        composeRule.onNodeWithText("Priority 1").assertIsDisplayed()
        composeRule.onNodeWithText("Priority 2").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("funds_section_card").assertIsDisplayed()
    }

    @Test
    fun reserveOnlyScreenShowsDefaultReserveAndEmptyGoalsMessage() {
        composeRule.setContent {
            WallyBudgetTheme {
                FundsScreen(
                    funds = listOf(
                        fund(
                            uuid = DEFAULT_FUND_UUID,
                            name = "Savings",
                            fundType = FundType.DEFAULT_RESERVE,
                            balanceCents = 40_00L,
                            targetAmountCents = 80_00L,
                            sortOrder = 0,
                            createdAtEpochMs = 1L
                        )
                    ),
                    onNavigateBack = {},
                    onCreateGoalFund = { _, _ -> },
                    onUpdateGoalFund = { _, _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("Default reserve").assertIsDisplayed()
        composeRule.onNodeWithTag("fund_reserve_card").assertIsDisplayed()
        composeRule.onNodeWithText("No active goals yet.").assertIsDisplayed()
        composeRule.onNodeWithTag("funds_empty_state").assertIsDisplayed()
    }

    @Test
    fun addGoalSheetValidatesInputsAndCreatesGoal() {
        composeRule.setContent {
            var funds by remember { mutableStateOf(funds()) }

            WallyBudgetTheme {
                FundsScreen(
                    funds = funds,
                    onNavigateBack = {},
                    onCreateGoalFund = { name, targetAmountCents ->
                        funds = funds + fund(
                            uuid = "goal-new",
                            name = name,
                            fundType = FundType.GOAL,
                            balanceCents = 0L,
                            targetAmountCents = targetAmountCents,
                            sortOrder = funds.maxOfOrNull { it.sortOrder }?.plus(1) ?: 0,
                            createdAtEpochMs = 4L
                        )
                    },
                    onUpdateGoalFund = { _, _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("fund_goal_add_button").performClick()
        composeRule.onNodeWithTag("goal_save_button").performClick()
        composeRule.onNodeWithTag("goal_editor_error").assertTextContains("Enter a goal name.")

        composeRule.onNodeWithTag("goal_name_field").performTextInput("Retirement")
        composeRule.onNodeWithTag("goal_target_field").performTextInput("0")
        composeRule.onNodeWithTag("goal_save_button").performClick()
        composeRule.onNodeWithTag("goal_editor_error").assertTextContains("Enter a positive target amount.")

        composeRule.onNodeWithTag("goal_target_field").performTextClearance()
        composeRule.onNodeWithTag("goal_target_field").performTextInput("250.00")
        composeRule.onNodeWithTag("goal_save_button").performClick()

        composeRule.onNodeWithText("Retirement").assertIsDisplayed()
    }

    @Test
    fun editGoalSheetUpdatesGoalImmediately() {
        composeRule.setContent {
            var funds by remember { mutableStateOf(funds()) }

            WallyBudgetTheme {
                FundsScreen(
                    funds = funds,
                    onNavigateBack = {},
                    onCreateGoalFund = { _, _ -> },
                    onUpdateGoalFund = { uuid, name, targetAmountCents ->
                        funds = funds.map { existing ->
                            if (existing.uuid == uuid) {
                                existing.copy(
                                    name = name,
                                    targetAmountCents = targetAmountCents,
                                    updatedAtEpochMs = existing.updatedAtEpochMs + 1L,
                                    modClock = "${existing.updatedAtEpochMs + 1L}-0000-test-install"
                                )
                            } else {
                                existing
                            }
                        }
                    }
                )
            }
        }

        composeRule.onNodeWithTag("fund_goal_edit_button_goal-a").performClick()
        composeRule.onNodeWithTag("goal_name_field").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("Emergency"))
        )
        composeRule.onNodeWithTag("goal_target_field").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("50.00"))
        )
        composeRule.onNodeWithTag("goal_name_field").performTextClearance()
        composeRule.onNodeWithTag("goal_name_field").performTextInput("Weekend Trip")
        composeRule.onNodeWithTag("goal_target_field").performTextClearance()
        composeRule.onNodeWithTag("goal_target_field").performTextInput("125.00")
        composeRule.onNodeWithTag("goal_save_button").performClick()

        composeRule.onNodeWithText("Weekend Trip").assertIsDisplayed()
        composeRule.onNodeWithTag("fund_goal_goal-a").assertIsDisplayed()
    }

    private fun funds(): List<Fund> {
        return listOf(
            fund(
                uuid = DEFAULT_FUND_UUID,
                name = "Savings",
                fundType = FundType.DEFAULT_RESERVE,
                balanceCents = 40_00L,
                targetAmountCents = 100_00L,
                sortOrder = 0,
                createdAtEpochMs = 1L
            ),
            fund(
                uuid = "goal-b",
                name = "Vacation",
                fundType = FundType.GOAL,
                balanceCents = 15_00L,
                targetAmountCents = 60_00L,
                sortOrder = 2,
                createdAtEpochMs = 3L
            ),
            fund(
                uuid = "goal-a",
                name = "Emergency",
                fundType = FundType.GOAL,
                balanceCents = 25_00L,
                targetAmountCents = 50_00L,
                sortOrder = 1,
                createdAtEpochMs = 2L
            )
        )
    }

    private fun bucket() = BudgetBucket(
        bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
        name = DEFAULT_SPENDING_BUCKET_NAME,
        defaultAllocatedAmountCents = 70_00L,
        sortOrder = 0,
        originInstallId = "test-install",
        lastModifiedByInstallId = "test-install",
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
        modClock = "clock-default"
    )

    private fun bucketSummary() = BucketSummaryState(
        bucket = bucket(),
        allocatedThisCycleCents = 70_00L,
        spentThisCycleCents = 5_00L,
        remainingThisCycleCents = 65_00L,
        overspentCents = 0L,
        earmarkedBalanceCents = 0L
    )

    private fun portfolioState() = PortfolioState(
        portfolioTotalBudgetCents = 100_00L,
        allocatedToBucketsCents = 70_00L,
        unassignedPlannedBudgetCents = 30_00L,
        totalSpentThisCycleCents = 5_00L,
        remainingThisCycleCents = 95_00L,
        completedCycleReserveCents = 0L,
        netReserveCents = 0L,
        cycleStartDate = LocalDate.of(2026, 3, 1),
        cycleEndDateExclusive = LocalDate.of(2026, 4, 1)
    )

    private fun userSettings() = UserSettings(
        monthlyBudgetCents = 100_00L,
        portfolioMonthlyBudgetCents = 100_00L,
        paydayDate = 1,
        selectedBucketUuid = DEFAULT_SPENDING_BUCKET_UUID
    )

    private fun fund(
        uuid: String,
        name: String,
        fundType: FundType,
        balanceCents: Long,
        targetAmountCents: Long?,
        sortOrder: Int,
        createdAtEpochMs: Long
    ) = Fund(
        uuid = uuid,
        name = name,
        fundType = fundType,
        balanceCents = balanceCents,
        allocationPerCycleCents = 0L,
        targetAmountCents = targetAmountCents,
        sortOrder = sortOrder,
        originInstallId = "test-install",
        lastModifiedByInstallId = "test-install",
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = createdAtEpochMs,
        modClock = "clock-$uuid"
    )
}
