package net.loeu.wallybudget.domain.usecase.internal

import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.BucketCycleBaseline
import net.loeu.wallybudget.domain.model.BucketTransfer
import net.loeu.wallybudget.domain.model.BucketTransferReason
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_NAME
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BucketTransferSupportTest {

    private val cycleStart = LocalDate.of(2026, 3, 25)
    private val cycleEndExclusive = LocalDate.of(2026, 4, 25)

    @Test
    fun resolveCurrentCycleAllocationTotal_preservesPortfolioBudgetAcrossInternalTransfers() {
        val total = resolveCurrentCycleAllocationTotal(
            buckets = listOf(
                bucket(DEFAULT_SPENDING_BUCKET_UUID, DEFAULT_SPENDING_BUCKET_NAME, 100_000L, 0),
                bucket("bills", "Bills", 300_000L, 1)
            ),
            cycleStart = cycleStart,
            baselines = listOf(
                baseline(DEFAULT_SPENDING_BUCKET_UUID, 400_000L),
                baseline("bills", 0L)
            ),
            transfers = listOf(
                transfer(
                    fromBucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                    toBucketUuid = "bills",
                    amountCents = 300_000L
                )
            )
        )

        assertEquals(400_000L, total)
    }

    @Test
    fun resolveCurrentCycleDefaultAllocation_keepsPortfolioTotalStableForRestoredState() {
        val portfolioBudgetCents = 400_000L
        val namedBuckets = listOf(bucket("bills", "Bills", 300_000L, 1))
        val existingBaselines = listOf(
            baseline(DEFAULT_SPENDING_BUCKET_UUID, 400_000L),
            baseline("bills", 0L)
        )
        val transfers = listOf(
            transfer(
                fromBucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                toBucketUuid = "bills",
                amountCents = 300_000L
            )
        )

        val repairedDefaultBaseline = resolveCurrentCycleDefaultAllocation(
            portfolioMonthlyBudgetCents = portfolioBudgetCents,
            namedBuckets = namedBuckets,
            cycleStart = cycleStart,
            baselines = existingBaselines,
            transfers = transfers
        )
        val repairedBaselines = listOf(
            baseline(DEFAULT_SPENDING_BUCKET_UUID, repairedDefaultBaseline),
            baseline("bills", 0L)
        )

        val total = resolveCurrentCycleAllocationTotal(
            buckets = listOf(bucket(DEFAULT_SPENDING_BUCKET_UUID, DEFAULT_SPENDING_BUCKET_NAME, 100_000L, 0)) +
                namedBuckets,
            cycleStart = cycleStart,
            baselines = repairedBaselines,
            transfers = transfers
        )

        assertEquals(400_000L, repairedDefaultBaseline)
        assertEquals(portfolioBudgetCents, total)
    }

    private fun bucket(
        bucketUuid: String,
        name: String,
        defaultAllocatedAmountCents: Long,
        sortOrder: Int
    ) = BudgetBucket(
        bucketUuid = bucketUuid,
        name = name,
        defaultAllocatedAmountCents = defaultAllocatedAmountCents,
        sortOrder = sortOrder,
        originInstallId = "test-install-id",
        lastModifiedByInstallId = "test-install-id",
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
        modClock = "0000000000001-0000-test-install-id"
    )

    private fun baseline(bucketUuid: String, amountCents: Long) = BucketCycleBaseline(
        baselineUuid = "$bucketUuid-baseline",
        bucketUuid = bucketUuid,
        cycleStartDate = cycleStart.toString(),
        cycleEndDateExclusive = cycleEndExclusive.toString(),
        baselineAmountCents = amountCents,
        originInstallId = "test-install-id",
        lastModifiedByInstallId = "test-install-id",
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
        modClock = "0000000000001-0000-test-install-id"
    )

    private fun transfer(
        fromBucketUuid: String,
        toBucketUuid: String,
        amountCents: Long
    ) = BucketTransfer(
        transferUuid = "$fromBucketUuid-$toBucketUuid-$amountCents",
        fromBucketUuid = fromBucketUuid,
        toBucketUuid = toBucketUuid,
        amountCents = amountCents,
        reason = BucketTransferReason.MANUAL_REALLOCATION,
        cycleStartDate = cycleStart.toString(),
        cycleEndDateExclusive = cycleEndExclusive.toString(),
        effectiveDate = cycleStart.toString(),
        originInstallId = "test-install-id",
        lastModifiedByInstallId = "test-install-id",
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
        modClock = "0000000000001-0000-test-install-id"
    )
}
