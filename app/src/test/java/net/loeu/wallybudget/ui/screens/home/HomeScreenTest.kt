package net.loeu.wallybudget.ui.screens.home

import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.BucketBalanceBehavior
import net.loeu.wallybudget.domain.model.BucketSummaryState
import net.loeu.wallybudget.domain.model.BucketTrackingMode
import net.loeu.wallybudget.domain.model.DEFAULT_FUND_UUID
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import net.loeu.wallybudget.domain.model.Fund
import net.loeu.wallybudget.domain.model.FundState
import net.loeu.wallybudget.util.CurrencyFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun defaultFundState_returnsDefaultFundWhenPresent() {
        val expected = fundState(DEFAULT_FUND_UUID, "Savings", balanceCents = 42_00L)

        val resolved = defaultFundState(
            listOf(
                fundState("travel", "Travel", balanceCents = 10_00L),
                expected
            )
        )

        assertEquals(expected, resolved)
    }

    @Test
    fun defaultFundState_returnsNullWhenDefaultFundMissing() {
        val resolved = defaultFundState(
            listOf(fundState("travel", "Travel", balanceCents = 10_00L))
        )

        assertNull(resolved)
    }

    @Test
    fun formatFundTargetProgress_formatsTargetAndPercent() {
        val text = formatFundTargetProgress(
            fundState(
                DEFAULT_FUND_UUID,
                "Savings",
                balanceCents = 50_00L,
                targetAmountCents = 120_00L,
                progressPercent = 41.6f
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
                fundState(DEFAULT_FUND_UUID, "Savings", balanceCents = 50_00L, targetAmountCents = null)
            )
        )
        assertNull(
            formatFundTargetProgress(
                fundState(DEFAULT_FUND_UUID, "Savings", balanceCents = 50_00L, targetAmountCents = 0L)
            )
        )
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

    private fun fundState(
        uuid: String,
        name: String,
        balanceCents: Long,
        targetAmountCents: Long? = null,
        progressPercent: Float? = null
    ) = FundState(
        fund = Fund(
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
        ),
        balanceCents = balanceCents,
        targetAmountCents = targetAmountCents,
        progressPercent = progressPercent
    )
}
