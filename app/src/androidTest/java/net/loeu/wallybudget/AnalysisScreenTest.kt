package net.loeu.wallybudget

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.domain.model.MonthlyHistory
import net.loeu.wallybudget.domain.model.SpendingForecast
import net.loeu.wallybudget.ui.screens.analysis.AnalysisScreen
import net.loeu.wallybudget.ui.theme.WallyBudgetTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import androidx.compose.ui.unit.height

@RunWith(AndroidJUnit4::class)
class AnalysisScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun analysis_sections_follow_verdict_evidence_actions_confidence_order() {
        setAnalysisScreenContent()

        composeRule.onNodeWithTag("analysis_verdict_section").assertIsDisplayed()
        composeRule.onNodeWithTag("analysis_evidence_section").assertIsDisplayed()

        val expandedHeight = composeRule.onNodeWithTag("analysis_verdict_section").getBoundsInRoot().height
        val verdictTop = composeRule.onNodeWithTag("analysis_verdict_section").getBoundsInRoot().top
        val evidenceTop = composeRule.onNodeWithTag("analysis_evidence_section").getBoundsInRoot().top

        assertTrue(verdictTop < evidenceTop)

        composeRule.onNodeWithTag("analysis_list")
            .performScrollToNode(hasTestTag("analysis_actions_section"))
        composeRule.waitForIdle()

        val collapsedHeight = composeRule.onNodeWithTag("analysis_verdict_section").getBoundsInRoot().height

        assertTrue(collapsedHeight < expandedHeight)
        composeRule.onNodeWithTag("analysis_actions_section").assertIsDisplayed()
        composeRule.onNodeWithTag("analysis_list")
            .performScrollToNode(hasTestTag("analysis_confidence_section"))
        composeRule.onNodeWithTag("analysis_confidence_section").assertIsDisplayed()
    }

    @Test
    fun analysis_screen_switches_verdict_copy_for_atRisk_state() {
        setAnalysisScreenContent(
            budgetState = testBudgetState(remainingTodayCents = -500L),
            spendingForecast = testForecast(
                estimatedEndCycleRemainingCents = -9_000L,
                upperBoundCents = 110_000L,
                projectedDailySpendCents = 4_700L,
                confidenceScore = 0.84,
                recoverableOverspendCents = 0L
            )
        )

        composeRule.onNodeWithText("Risk of overspending").assertIsDisplayed()
        composeRule.onNodeWithTag("analysis_verdict_section")
            .assert(hasStateDescription("Analysis verdict at risk"))
    }

    @Test
    fun analysis_screen_uses_stable_copy_when_rangeCrossing_is_not_supported_by_history() {
        setAnalysisScreenContent(
            spendingForecast = testForecast(
                estimatedEndCycleRemainingCents = 13_180L,
                upperBoundCents = 105_249L,
                projectedDailySpendCents = 2_856L,
                confidenceScore = 0.59,
                recoverableOverspendCents = 3_473L
            ),
            monthlyHistory = histories(2_100L, 90_400L, 42_500L, 0L, 66_200L, -1_500L)
        )

        composeRule.onNodeWithText("On track").assertIsDisplayed()
        composeRule.onNodeWithTag("analysis_verdict_section")
            .assert(hasStateDescription("Analysis verdict stable"))
    }

    @Test
    fun analysis_screen_uses_watchful_copy_when_history_shows_some_precedent() {
        setAnalysisScreenContent(
            spendingForecast = testForecast(
                estimatedEndCycleRemainingCents = 13_180L,
                upperBoundCents = 105_249L,
                projectedDailySpendCents = 2_856L,
                confidenceScore = 0.72,
                recoverableOverspendCents = 3_473L
            ),
            monthlyHistory = histories(2_100L, 90_400L, 42_500L, -16_000L, 66_200L, -1_500L)
        )

        composeRule.onNodeWithText("Watch the upper range").assertIsDisplayed()
        composeRule.onNodeWithTag("analysis_verdict_section")
            .assert(hasStateDescription("Analysis verdict watchful"))
    }

    @Test
    fun analysis_screen_lowConfidence_explainer_mentions_monitorTiming() {
        setAnalysisScreenContent(
            spendingForecast = testForecast(
                estimatedEndCycleRemainingCents = -4_000L,
                upperBoundCents = 104_000L,
                projectedDailySpendCents = 4_000L,
                confidenceScore = 0.41
            )
        )

        composeRule.onNodeWithTag("analysis_list")
            .performScrollToNode(hasTestTag("analysis_confidence_section"))
        composeRule.onAllNodesWithText("check back in 2 days", substring = true).assertCountEquals(2)
    }

    @Test
    fun analysis_screen_hidesHistoryTile_whenNoHistoryExists() {
        setAnalysisScreenContent(monthlyHistory = emptyList())

        composeRule.onNodeWithTag("analysis_list")
            .performScrollToNode(hasTestTag("analysis_history_fallback"))
        composeRule.onAllNodesWithText("Historical tendency").assertCountEquals(0)
        composeRule.onNodeWithTag("analysis_history_fallback").assertIsDisplayed()
    }

    @Test
    fun analysis_screen_timelineLock_bannerRenders_withoutActionCtas() {
        setAnalysisScreenContent(timelineLockReason = "Home is read-only until the pending cycle is concluded.")

        composeRule.onNodeWithText("Home is read-only until the pending cycle is concluded.").assertIsDisplayed()
        composeRule.onAllNodesWithText("Review cycle expenses").assertCountEquals(0)
    }

    @Test
    fun analysis_screen_loadingState_showsPlaceholdersWithoutFinalVerdictCopy() {
        setAnalysisScreenContent(isLoading = true)

        composeRule.onNodeWithTag("analysis_verdict_section").assertIsDisplayed()
        composeRule.onAllNodesWithText("On track").assertCountEquals(0)
        composeRule.onAllNodesWithText("Risk of overspending").assertCountEquals(0)
    }

    private fun setAnalysisScreenContent(
        budgetState: BudgetState = testBudgetState(),
        spendingForecast: SpendingForecast = testForecast(),
        monthlyHistory: List<MonthlyHistory> = listOf(history(4_000L)),
        effectiveCurrentDate: LocalDate = LocalDate.of(2026, 3, 7),
        timelineLockReason: String? = null,
        isLoading: Boolean = false
    ) {
        composeRule.setContent {
            WallyBudgetTheme {
                AnalysisScreen(
                    budgetState = budgetState,
                    spendingForecast = spendingForecast,
                    monthlyHistory = monthlyHistory,
                    effectiveCurrentDate = effectiveCurrentDate,
                    timelineLockReason = timelineLockReason,
                    isLoading = isLoading,
                    onNavigateToSettings = {}
                )
            }
        }
    }

    private fun testBudgetState(
        totalSpentThisCycleCents: Long = 82_000L,
        remainingTodayCents: Long = 1_800L
    ) = BudgetState(
        monthlyBudgetCents = 100_000L,
        totalSpentThisCycleCents = totalSpentThisCycleCents,
        dailyBudgetCents = 3_000L,
        spentTodayCents = (3_000L - remainingTodayCents).coerceAtLeast(0L),
        remainingTodayCents = remainingTodayCents,
        daysRemainingInCycle = 10,
        cumulativeSavingsCents = 9_000L,
        paydayDate = 1,
        cycleStartDate = LocalDate.of(2026, 3, 1)
    )

    private fun testForecast(
        estimatedEndCycleRemainingCents: Long = 6_000L,
        upperBoundCents: Long = 98_000L,
        projectedDailySpendCents: Long = 2_900L,
        confidenceScore: Double = 0.72,
        recoverableOverspendCents: Long = 1_200L
    ) = SpendingForecast(
        estimatedEndCycleRemainingCents = estimatedEndCycleRemainingCents,
        projectedTotalSpentCents = 100_000L - estimatedEndCycleRemainingCents,
        projectedDailySpendCents = projectedDailySpendCents,
        confidenceScore = confidenceScore,
        lowerBoundCents = 91_000L,
        upperBoundCents = upperBoundCents,
        dailyAverageWeightedCents = projectedDailySpendCents,
        recoverableOverspendCents = recoverableOverspendCents,
        grossRecoverableOverspendCents = recoverableOverspendCents
    )

    private fun history(surplusCents: Long) = MonthlyHistory(
        cycleStartDate = "2026-02-01",
        budgetAmountCents = 100_000L,
        totalSpentCents = 100_000L - surplusCents,
        surplusCents = surplusCents,
        cycleEndDate = "2026-03-01",
        endTimestamp = 1L
    )

    private fun histories(vararg surpluses: Long): List<MonthlyHistory> {
        return surpluses.mapIndexed { index, surplusCents ->
            MonthlyHistory(
                cycleStartDate = LocalDate.of(2026, 2, 1).minusDays(index.toLong() * 31L).toString(),
                budgetAmountCents = 100_000L,
                totalSpentCents = 100_000L - surplusCents,
                surplusCents = surplusCents,
                cycleEndDate = LocalDate.of(2026, 3, 1).minusDays(index.toLong() * 31L).toString(),
                endTimestamp = LocalDate.of(2026, 3, 1).minusDays(index.toLong() * 31L).toEpochDay()
            )
        }
    }

    private fun hasStateDescription(value: String): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)
}
