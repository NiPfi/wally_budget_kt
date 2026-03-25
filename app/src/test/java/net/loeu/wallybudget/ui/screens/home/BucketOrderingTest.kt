package net.loeu.wallybudget.ui.screens.home

import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.BucketBalanceBehavior
import net.loeu.wallybudget.domain.model.BucketTrackingMode
import org.junit.Assert.assertEquals
import org.junit.Test

class BucketOrderingTest {

    @Test
    fun closedBucketsSortLast() {
        val buckets = listOf(
            testBucket(bucketUuid = "closed", sortOrder = 0, isClosed = true),
            testBucket(bucketUuid = "open", sortOrder = 1, isClosed = false),
            testBucket(bucketUuid = "open-2", sortOrder = 2, isClosed = false)
        )

        val sorted = buckets.sortedWith(compareBucketsClosedLast())

        assertEquals(listOf("open", "open-2", "closed"), sorted.map { it.bucketUuid })
    }

    private fun testBucket(
        bucketUuid: String,
        sortOrder: Int,
        isClosed: Boolean
    ): BudgetBucket {
        return BudgetBucket(
            bucketUuid = bucketUuid,
            name = bucketUuid,
            trackingMode = BucketTrackingMode.DAILY_TARGET,
            balanceBehavior = BucketBalanceBehavior.RETURN_TO_PORTFOLIO,
            defaultAllocatedAmountCents = 0L,
            sortOrder = sortOrder,
            originInstallId = "test-install",
            lastModifiedByInstallId = "test-install",
            createdAtEpochMs = sortOrder.toLong(),
            updatedAtEpochMs = sortOrder.toLong(),
            closedAtEpochMs = if (isClosed) 1L else null,
            modClock = "clock-$bucketUuid"
        )
    }
}
