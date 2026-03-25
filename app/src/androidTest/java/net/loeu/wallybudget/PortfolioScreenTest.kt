package net.loeu.wallybudget

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.BucketBalanceBehavior
import net.loeu.wallybudget.domain.model.BucketSummaryState
import net.loeu.wallybudget.domain.model.BucketTrackingMode
import net.loeu.wallybudget.domain.model.DEFAULT_FUND_UUID
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_NAME
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import net.loeu.wallybudget.domain.model.Fund
import net.loeu.wallybudget.domain.model.PortfolioState
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.usecase.BucketDraft
import net.loeu.wallybudget.ui.screens.home.BUCKET_CHANGED_SNACKBAR_MESSAGE
import net.loeu.wallybudget.ui.screens.home.PortfolioScreen
import net.loeu.wallybudget.ui.theme.WallyBudgetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class PortfolioScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tappingBucketRowOpensBucketSettingsSheet() {
        setPortfolioContent()

        composeRule.onNodeWithText("Current cycle buckets").assertIsDisplayed()
        composeRule.onNodeWithTag("bucket_row_travel").performClick()

        composeRule.onNodeWithText("Bucket settings").assertIsDisplayed()
        composeRule.onNodeWithText("Close bucket").assertIsDisplayed()
    }

    @Test
    fun settledClosingBucketRemainsVisibleAndIsReadOnly() {
        setPortfolioContent()

        composeRule.onNodeWithTag("bucket_row_bills").assertIsDisplayed().assertHasNoClickAction()
        composeRule.onNodeWithText("Bills").assertIsDisplayed()
    }

    @Test
    fun savingBucketEditsFromPortfolioCallsOnSavePortfolioPlanWithUpdatedDraftList() {
        val saveCalls = mutableListOf<List<BucketDraft>>()

        setPortfolioContent(saveCalls = saveCalls)

        composeRule.onNodeWithTag("bucket_row_travel").performClick()
        composeRule.onNodeWithText("Bucket name").performTextReplacement("Trips")
        composeRule.onNodeWithText("Cycle allocation").performTextReplacement("35.00")
        composeRule.onNodeWithText("Save").performClick()

        val latestCall = saveCalls.single()
        val updatedDraft = latestCall.first { it.bucketUuid == "travel" }
        assertEquals("Trips", updatedDraft.name)
        assertEquals(35_00L, updatedDraft.defaultAllocatedAmountCents)
        assertFalse(updatedDraft.closeRequested)
    }

    @Test
    fun defaultBucketEditorDoesNotShowCloseBucketAction() {
        setPortfolioContent()

        composeRule.onNodeWithTag("bucket_row_$DEFAULT_SPENDING_BUCKET_UUID").performClick()

        composeRule.onNodeWithText("Bucket settings").assertIsDisplayed()
        composeRule.onAllNodesWithText("Close bucket").assertCountEquals(0)
    }

    @Test
    fun confirmingCloseKeepsEditorVisibleThenSubmitsClosedDraftAndRefreshesList() {
        val saveCalls = mutableListOf<List<BucketDraft>>()

        setPortfolioContent(saveCalls = saveCalls)

        composeRule.onNodeWithTag("bucket_row_travel").performClick()
        composeRule.onNodeWithTag("bucket_editor_close_action").performClick()

        composeRule.onNodeWithText("Bucket settings").assertIsDisplayed()
        composeRule.onNodeWithText("Close bucket?").assertIsDisplayed()
        composeRule.onAllNodesWithText("Close bucket")[1].performClick()

        val latestCall = saveCalls.single()
        val updatedDraft = latestCall.first { it.bucketUuid == "travel" }
        assertTrue(updatedDraft.closeRequested)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Travel").assertCountEquals(0)
    }

    @Test
    fun cancelUsesDiscardConfirmationOnlyWhenFormIsDirty() {
        setPortfolioContent()

        composeRule.onNodeWithTag("bucket_row_travel").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onAllNodesWithText("Bucket settings").assertCountEquals(0)

        composeRule.onNodeWithTag("bucket_row_travel").performClick()
        composeRule.onNodeWithText("Bucket name").performTextReplacement("Trips")
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Discard changes?").assertIsDisplayed()
        composeRule.onNodeWithText("Keep editing").performClick()
        composeRule.onNodeWithText("Bucket settings").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Discard changes?").assertIsDisplayed()
        composeRule.onNodeWithText("Discard changes").performClick()
        composeRule.onAllNodesWithText("Bucket settings").assertCountEquals(0)
    }

    @Test
    fun staleSubmitShowsSnackbarInsteadOfSaving() {
        val saveCalls = mutableListOf<List<BucketDraft>>()
        lateinit var closeTravelExternally: () -> Unit

        setPortfolioContent(
            saveCalls = saveCalls,
            onContentReady = { closeTravelExternally = it }
        )

        composeRule.onNodeWithTag("bucket_row_travel").performClick()
        composeRule.runOnUiThread { closeTravelExternally() }
        composeRule.onNodeWithText("Save").performClick()

        assertTrue(saveCalls.isEmpty())
        composeRule.onNodeWithText(BUCKET_CHANGED_SNACKBAR_MESSAGE).assertIsDisplayed()
    }

    @Test
    fun fundsSectionRemainsReadOnly() {
        setPortfolioContent()

        composeRule.onNodeWithText("Savings").assertHasNoClickAction()
    }

    private fun setPortfolioContent(
        saveCalls: MutableList<List<BucketDraft>> = mutableListOf(),
        onContentReady: (((() -> Unit)) -> Unit)? = null
    ) {
        val initialBuckets = listOf(
            testBucket(
                bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                name = DEFAULT_SPENDING_BUCKET_NAME,
                defaultAllocatedAmountCents = 70_00L,
                sortOrder = 0
            ),
            testBucket(
                bucketUuid = "travel",
                name = "Travel",
                defaultAllocatedAmountCents = 30_00L,
                sortOrder = 1
            ),
            testBucket(
                bucketUuid = "bills",
                name = "Bills",
                defaultAllocatedAmountCents = 25_00L,
                sortOrder = 2
            ).copy(settledCloseCycleEndDateExclusive = "2026-04-01")
        )
        val initialSummaries = listOf(
            testSummary(initialBuckets[0], allocatedThisCycleCents = 45_00L),
            testSummary(initialBuckets[1], allocatedThisCycleCents = 30_00L),
            testSummary(initialBuckets[2], allocatedThisCycleCents = 25_00L)
        )
        val initialPortfolioState = PortfolioState(
            portfolioTotalBudgetCents = 100_00L,
            allocatedToBucketsCents = 100_00L,
            unassignedPlannedBudgetCents = 0L,
            totalSpentThisCycleCents = 15_00L,
            remainingThisCycleCents = 85_00L,
            completedCycleReserveCents = 0L,
            netReserveCents = 0L,
            cycleStartDate = LocalDate.of(2026, 3, 1),
            cycleEndDateExclusive = LocalDate.of(2026, 4, 1)
        )

        composeRule.setContent {
            var buckets by remember { mutableStateOf(initialBuckets) }
            var summaries by remember { mutableStateOf(initialSummaries) }
            onContentReady?.invoke {
                buckets = buckets.map { bucket ->
                    if (bucket.bucketUuid == "travel") bucket.copy(closedAtEpochMs = 10L) else bucket
                }
                summaries = summaries.filterNot { it.bucket.bucketUuid == "travel" }
            }

            WallyBudgetTheme {
                PortfolioScreen(
                    portfolioState = initialPortfolioState,
                    bucketSummaries = summaries,
                    funds = listOf(testFund()),
                    allBuckets = buckets,
                    userSettings = UserSettings(
                        monthlyBudgetCents = 100_00L,
                        portfolioMonthlyBudgetCents = 100_00L,
                        paydayDate = 1,
                        selectedBucketUuid = "travel"
                    ),
                    onSavePortfolioPlan = { _, drafts ->
                        saveCalls += drafts
                        buckets = applyDraftsToBuckets(buckets, drafts)
                        summaries = applyDraftsToSummaries(summaries, drafts, 100_00L)
                    },
                    onNavigateToSettings = {},
                    showTopRightSettingsAction = true
                )
            }
        }
    }

    private fun applyDraftsToBuckets(
        buckets: List<BudgetBucket>,
        drafts: List<BucketDraft>
    ): List<BudgetBucket> {
        val draftsByUuid = drafts.associateBy { it.bucketUuid }
        return buckets.map { bucket ->
            val draft = draftsByUuid[bucket.bucketUuid] ?: return@map bucket
            bucket.copy(
                name = draft.name,
                defaultAllocatedAmountCents = draft.defaultAllocatedAmountCents,
                closedAtEpochMs = if (draft.closeRequested) 10L else null
            )
        }
    }

    private fun applyDraftsToSummaries(
        summaries: List<BucketSummaryState>,
        drafts: List<BucketDraft>,
        portfolioBudgetCents: Long
    ): List<BucketSummaryState> {
        val namedAllocatedCents = drafts
            .filterNot { it.bucketUuid == DEFAULT_SPENDING_BUCKET_UUID || it.closeRequested }
            .sumOf { it.defaultAllocatedAmountCents }
        return summaries.mapNotNull { summary ->
            val draft = drafts.firstOrNull { it.bucketUuid == summary.bucket.bucketUuid } ?: return@mapNotNull summary
            if (draft.closeRequested) {
                null
            } else {
                val allocation = if (draft.bucketUuid == DEFAULT_SPENDING_BUCKET_UUID) {
                    (portfolioBudgetCents - namedAllocatedCents).coerceAtLeast(0L)
                } else {
                    draft.defaultAllocatedAmountCents
                }
                summary.copy(
                    bucket = summary.bucket.copy(
                        name = draft.name,
                        defaultAllocatedAmountCents = allocation
                    ),
                    allocatedThisCycleCents = allocation,
                    remainingThisCycleCents = allocation - summary.spentThisCycleCents
                )
            }
        }
    }

    private fun testBucket(
        bucketUuid: String,
        name: String,
        defaultAllocatedAmountCents: Long,
        sortOrder: Int
    ) = BudgetBucket(
        bucketUuid = bucketUuid,
        name = name,
        trackingMode = BucketTrackingMode.DAILY_TARGET,
        balanceBehavior = BucketBalanceBehavior.RETURN_TO_PORTFOLIO,
        defaultAllocatedAmountCents = defaultAllocatedAmountCents,
        sortOrder = sortOrder,
        originInstallId = "test-install",
        lastModifiedByInstallId = "test-install",
        createdAtEpochMs = sortOrder.toLong(),
        updatedAtEpochMs = sortOrder.toLong(),
        modClock = "clock-$bucketUuid"
    )

    private fun testSummary(bucket: BudgetBucket, allocatedThisCycleCents: Long) = BucketSummaryState(
        bucket = bucket,
        allocatedThisCycleCents = allocatedThisCycleCents,
        spentThisCycleCents = 5_00L,
        remainingThisCycleCents = allocatedThisCycleCents - 5_00L,
        overspentCents = 0L,
        earmarkedBalanceCents = 0L
    )

    private fun testFund() = Fund(
        uuid = DEFAULT_FUND_UUID,
        name = "Savings",
        balanceCents = 10_00L,
        allocationPerCycleCents = 0L,
        targetAmountCents = 50_00L,
        sortOrder = 0,
        originInstallId = "test-install",
        lastModifiedByInstallId = "test-install",
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
        modClock = "clock-fund"
    )
}
