package net.loeu.wallybudget.ui.screens.home

import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.BucketBalanceBehavior
import net.loeu.wallybudget.domain.model.BucketSummaryState
import net.loeu.wallybudget.domain.model.BucketTrackingMode
import net.loeu.wallybudget.domain.model.DEFAULT_FUND_UUID
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import net.loeu.wallybudget.domain.model.Fund
import net.loeu.wallybudget.domain.usecase.BucketDraft
import net.loeu.wallybudget.domain.usecase.internal.resolveSelectedOpenBucketUuid
import net.loeu.wallybudget.util.CurrencyFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeScreenTest {

    @Test
    fun sumOtherNamedBucketAllocationsForValidation_excludesDefaultBucketRemainder() {
        val defaultBucket = bucket(
            bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
            name = "Default",
            defaultAllocatedAmountCents = 70_00L,
            sortOrder = 0
        )
        val editedBucket = bucket("travel", "Travel", defaultAllocatedAmountCents = 30_00L, sortOrder = 1)
        val groceriesBucket = bucket("groceries", "Groceries", defaultAllocatedAmountCents = 15_00L, sortOrder = 2)

        val total = sumOtherNamedBucketAllocationsForValidation(
            allBuckets = listOf(defaultBucket, editedBucket, groceriesBucket),
            bucketSummaries = listOf(
                summary(defaultBucket, allocatedThisCycleCents = 70_00L),
                summary(editedBucket, allocatedThisCycleCents = 30_00L),
                summary(groceriesBucket, allocatedThisCycleCents = 15_00L)
            ),
            editedBucketUuid = editedBucket.bucketUuid
        )

        assertEquals(15_00L, total)
    }

    @Test
    fun defaultFund_returnsDefaultFundWhenPresent() {
        val expected = fund(DEFAULT_FUND_UUID, "Savings", balanceCents = 42_00L)

        val resolved = defaultFund(
            listOf(
                fund("travel", "Travel", balanceCents = 10_00L),
                expected
            )
        )

        assertEquals(expected, resolved)
    }

    @Test
    fun defaultFund_returnsNullWhenDefaultFundMissing() {
        val resolved = defaultFund(
            listOf(fund("travel", "Travel", balanceCents = 10_00L))
        )

        assertNull(resolved)
    }

    @Test
    fun formatFundTargetProgress_formatsTargetAndPercent() {
        val text = formatFundTargetProgress(
            fund(
                DEFAULT_FUND_UUID,
                "Savings",
                balanceCents = 50_00L,
                targetAmountCents = 120_00L
            )
        )

        assertEquals(
            "${CurrencyFormatter.format(50_00L)} of ${CurrencyFormatter.format(120_00L)} target · 42%",
            text
        )
    }

    @Test
    fun formatFundTargetProgress_returnsNullWithoutPositiveTarget() {
        assertNull(
            formatFundTargetProgress(
                fund(DEFAULT_FUND_UUID, "Savings", balanceCents = 50_00L, targetAmountCents = null)
            )
        )
        assertNull(
            formatFundTargetProgress(
                fund(DEFAULT_FUND_UUID, "Savings", balanceCents = 50_00L, targetAmountCents = 0L)
            )
        )
    }

    @Test
    fun buildFreshUpdatedBucketDrafts_updatesNamedBucketFromLatestMetadata() {
        val defaultBucket = bucket(
            bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
            name = "Default",
            defaultAllocatedAmountCents = 80_00L,
            sortOrder = 0
        )
        val travelBucket = bucket(
            bucketUuid = "travel",
            name = "Travel",
            defaultAllocatedAmountCents = 20_00L,
            sortOrder = 4
        )

        val result = buildFreshUpdatedBucketDrafts(
            allBuckets = listOf(defaultBucket, travelBucket),
            bucketSummaries = listOf(
                summary(defaultBucket, allocatedThisCycleCents = 80_00L),
                summary(travelBucket, allocatedThisCycleCents = 25_00L)
            ),
            updatedBucketDraft = BucketDraft(
                bucketUuid = "travel",
                name = "Trips",
                trackingMode = BucketTrackingMode.CYCLE_RESERVE,
                balanceBehavior = BucketBalanceBehavior.RETAIN_IN_BUCKET,
                defaultAllocatedAmountCents = 33_00L,
                sortOrder = 99
            )
        )

        val drafts = (result as BucketDraftBuildResult.Success).drafts
        val updatedDraft = drafts.first { it.bucketUuid == "travel" }
        assertEquals("Trips", updatedDraft.name)
        assertEquals(33_00L, updatedDraft.defaultAllocatedAmountCents)
        assertEquals(BucketTrackingMode.DAILY_TARGET, updatedDraft.trackingMode)
        assertEquals(BucketBalanceBehavior.RETURN_TO_PORTFOLIO, updatedDraft.balanceBehavior)
        assertEquals(4, updatedDraft.sortOrder)
    }

    @Test
    fun buildFreshUpdatedBucketDrafts_marksCloseRequestedForClosedBucketSubmission() {
        val defaultBucket = bucket(
            bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
            name = "Default",
            defaultAllocatedAmountCents = 70_00L,
            sortOrder = 0
        )
        val travelBucket = bucket("travel", "Travel", defaultAllocatedAmountCents = 30_00L, sortOrder = 1)

        val result = buildFreshUpdatedBucketDrafts(
            allBuckets = listOf(defaultBucket, travelBucket),
            bucketSummaries = listOf(
                summary(defaultBucket, allocatedThisCycleCents = 70_00L),
                summary(travelBucket, allocatedThisCycleCents = 30_00L)
            ),
            updatedBucketDraft = BucketDraft(
                bucketUuid = "travel",
                name = "Travel",
                trackingMode = BucketTrackingMode.DAILY_TARGET,
                balanceBehavior = BucketBalanceBehavior.RETURN_TO_PORTFOLIO,
                defaultAllocatedAmountCents = 30_00L,
                sortOrder = 1,
                closeRequested = true
            )
        )

        val updatedDraft = (result as BucketDraftBuildResult.Success).drafts.first { it.bucketUuid == "travel" }
        assertTrue(updatedDraft.closeRequested)
    }

    @Test
    fun buildFreshUpdatedBucketDrafts_forcesDefaultBucketAllocationToZero() {
        val defaultBucket = bucket(
            bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
            name = "Default",
            defaultAllocatedAmountCents = 70_00L,
            sortOrder = 0
        )

        val result = buildFreshUpdatedBucketDrafts(
            allBuckets = listOf(defaultBucket),
            bucketSummaries = listOf(summary(defaultBucket, allocatedThisCycleCents = 70_00L)),
            updatedBucketDraft = BucketDraft(
                bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                name = "Main",
                trackingMode = BucketTrackingMode.DAILY_TARGET,
                balanceBehavior = BucketBalanceBehavior.RETURN_TO_PORTFOLIO,
                defaultAllocatedAmountCents = 999_00L,
                sortOrder = 0
            )
        )

        val updatedDraft = (result as BucketDraftBuildResult.Success).drafts.single()
        assertEquals(0L, updatedDraft.defaultAllocatedAmountCents)
    }

    @Test
    fun buildFreshUpdatedBucketDrafts_returnsChangedWhenBucketMissingOrClosed() {
        val closedBucket = bucket(
            bucketUuid = "travel",
            name = "Travel",
            defaultAllocatedAmountCents = 20_00L,
            sortOrder = 1
        ).copy(closedAtEpochMs = 10L)

        val result = buildFreshUpdatedBucketDrafts(
            allBuckets = listOf(closedBucket),
            bucketSummaries = emptyList(),
            updatedBucketDraft = BucketDraft(
                bucketUuid = "travel",
                name = "Trips",
                trackingMode = BucketTrackingMode.DAILY_TARGET,
                balanceBehavior = BucketBalanceBehavior.RETURN_TO_PORTFOLIO,
                defaultAllocatedAmountCents = 25_00L,
                sortOrder = 1
            )
        )

        assertEquals(BucketDraftBuildResult.BucketChanged, result)
    }

    @Test
    fun resolveSelectedOpenBucketUuid_fallsBackToDefaultWhenClosingSelectedBucket() {
        val defaultBucket = bucket(
            bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
            name = "Default",
            defaultAllocatedAmountCents = 70_00L,
            sortOrder = 0
        )

        val resolved = resolveSelectedOpenBucketUuid(
            selectedBucketUuid = "travel",
            openBuckets = listOf(defaultBucket)
        )

        assertEquals(DEFAULT_SPENDING_BUCKET_UUID, resolved)
    }

    @Test
    fun resolveSelectedOpenBucketUuid_keepsOnlyDefaultBucketOpenAfterClosingLastNamedBucket() {
        val defaultBucket = bucket(
            bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
            name = "Default",
            defaultAllocatedAmountCents = 100_00L,
            sortOrder = 0
        )

        val resolved = resolveSelectedOpenBucketUuid(
            selectedBucketUuid = "travel",
            openBuckets = listOf(defaultBucket)
        )

        assertEquals(DEFAULT_SPENDING_BUCKET_UUID, resolved)
    }

    private fun summary(bucket: BudgetBucket, allocatedThisCycleCents: Long) = BucketSummaryState(
        bucket = bucket,
        allocatedThisCycleCents = allocatedThisCycleCents,
        spentThisCycleCents = 0L,
        remainingThisCycleCents = allocatedThisCycleCents,
        overspentCents = 0L,
        earmarkedBalanceCents = 0L
    )

    private fun bucket(
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
        originInstallId = "test-install-id",
        lastModifiedByInstallId = "test-install-id",
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
        modClock = "0000000000001-0000-test-install-id"
    )

    private fun fund(
        uuid: String,
        name: String,
        balanceCents: Long,
        targetAmountCents: Long? = null
    ) = Fund(
        uuid = uuid,
        name = name,
        balanceCents = balanceCents,
        allocationPerCycleCents = 0L,
        targetAmountCents = targetAmountCents,
        sortOrder = 0,
        originInstallId = "test-install-id",
        lastModifiedByInstallId = "test-install-id",
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
        modClock = "0000000000001-0000-test-install-id"
    )
}
