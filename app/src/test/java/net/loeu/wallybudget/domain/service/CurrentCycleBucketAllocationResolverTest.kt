package net.loeu.wallybudget.domain.service

import net.loeu.wallybudget.domain.model.BucketCycleBaseline
import net.loeu.wallybudget.domain.model.BucketTransfer
import net.loeu.wallybudget.domain.model.BucketTransferReason
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CurrentCycleBucketAllocationResolverTest {

    private val resolver = CurrentCycleBucketAllocationResolver()
    private val cycleStart = LocalDate.of(2026, 3, 25)

    @Test
    fun resolve_returnsBaselineWhenNoTransfersExist() {
        val allocation = resolver.resolve(
            bucketUuid = "bills",
            cycleStart = cycleStart,
            fallbackAllocationCents = 0L,
            baselines = listOf(baseline("bills", 150_000L)),
            transfers = emptyList()
        )

        assertEquals(150_000L, allocation.baselineAmountCents)
        assertEquals(0L, allocation.transferDeltaCents)
        assertEquals(150_000L, allocation.effectiveAllocationCents)
    }

    @Test
    fun resolve_appliesIncomingAndOutgoingTransfers() {
        val allocation = resolver.resolve(
            bucketUuid = "bills",
            cycleStart = cycleStart,
            fallbackAllocationCents = 0L,
            baselines = listOf(baseline("bills", 200_000L)),
            transfers = listOf(
                transfer(fromBucketUuid = "default", toBucketUuid = "bills", amountCents = 50_000L),
                transfer(fromBucketUuid = "bills", toBucketUuid = "travel", amountCents = 10_000L)
            )
        )

        assertEquals(40_000L, allocation.transferDeltaCents)
        assertEquals(240_000L, allocation.effectiveAllocationCents)
    }

    @Test
    fun resolve_keepsCloseSettlementAsTransferInsteadOfMutatingBaseline() {
        val defaultAllocation = resolver.resolve(
            bucketUuid = "default",
            cycleStart = cycleStart,
            fallbackAllocationCents = 0L,
            baselines = listOf(
                baseline("default", 100_000L),
                baseline("bills", 250_000L)
            ),
            transfers = listOf(
                transfer(
                    fromBucketUuid = "bills",
                    toBucketUuid = "default",
                    amountCents = 125_000L,
                    reason = BucketTransferReason.CLOSE_SETTLEMENT
                )
            )
        )
        val closingAllocation = resolver.resolve(
            bucketUuid = "bills",
            cycleStart = cycleStart,
            fallbackAllocationCents = 0L,
            baselines = listOf(
                baseline("default", 100_000L),
                baseline("bills", 250_000L)
            ),
            transfers = listOf(
                transfer(
                    fromBucketUuid = "bills",
                    toBucketUuid = "default",
                    amountCents = 125_000L,
                    reason = BucketTransferReason.CLOSE_SETTLEMENT
                )
            )
        )

        assertEquals(100_000L, defaultAllocation.baselineAmountCents)
        assertEquals(225_000L, defaultAllocation.effectiveAllocationCents)
        assertEquals(250_000L, closingAllocation.baselineAmountCents)
        assertEquals(125_000L, closingAllocation.effectiveAllocationCents)
    }

    @Test
    fun resolve_prefersNewestMatchingBaselineRegardlessOfInputOrder() {
        val allocation = resolver.resolve(
            bucketUuid = "bills",
            cycleStart = cycleStart,
            fallbackAllocationCents = 0L,
            baselines = listOf(
                baseline("bills", 225_000L, baselineUuid = "newer", updatedAtEpochMs = 20L),
                baseline("bills", 150_000L, baselineUuid = "older", updatedAtEpochMs = 10L)
            ),
            transfers = emptyList()
        )

        assertEquals(225_000L, allocation.baselineAmountCents)
    }

    @Test
    fun resolve_breaksUpdatedAtTiesByBaselineUuid() {
        val allocation = resolver.resolve(
            bucketUuid = "bills",
            cycleStart = cycleStart,
            fallbackAllocationCents = 0L,
            baselines = listOf(
                baseline("bills", 150_000L, baselineUuid = "baseline-a", updatedAtEpochMs = 10L),
                baseline("bills", 175_000L, baselineUuid = "baseline-z", updatedAtEpochMs = 10L)
            ),
            transfers = emptyList()
        )

        assertEquals(175_000L, allocation.baselineAmountCents)
    }

    private fun baseline(
        bucketUuid: String,
        amountCents: Long,
        baselineUuid: String = "$bucketUuid-baseline",
        updatedAtEpochMs: Long = 1L
    ) = BucketCycleBaseline(
        baselineUuid = baselineUuid,
        bucketUuid = bucketUuid,
        cycleStartDate = cycleStart.toString(),
        cycleEndDateExclusive = cycleStart.plusMonths(1).toString(),
        baselineAmountCents = amountCents,
        originInstallId = "test-install-id",
        lastModifiedByInstallId = "test-install-id",
        createdAtEpochMs = 1L,
        updatedAtEpochMs = updatedAtEpochMs,
        modClock = "0000000000001-0000-test-install-id"
    )

    private fun transfer(
        fromBucketUuid: String,
        toBucketUuid: String,
        amountCents: Long,
        reason: BucketTransferReason = BucketTransferReason.MANUAL_REALLOCATION
    ) = BucketTransfer(
        transferUuid = "$fromBucketUuid-$toBucketUuid-$amountCents",
        fromBucketUuid = fromBucketUuid,
        toBucketUuid = toBucketUuid,
        amountCents = amountCents,
        reason = reason,
        cycleStartDate = cycleStart.toString(),
        cycleEndDateExclusive = cycleStart.plusMonths(1).toString(),
        effectiveDate = cycleStart.toString(),
        originInstallId = "test-install-id",
        lastModifiedByInstallId = "test-install-id",
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
        modClock = "0000000000001-0000-test-install-id"
    )
}
