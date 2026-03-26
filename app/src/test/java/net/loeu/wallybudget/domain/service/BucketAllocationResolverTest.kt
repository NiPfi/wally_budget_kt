package net.loeu.wallybudget.domain.service

import net.loeu.wallybudget.domain.model.BucketAllocationAdjustment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BucketAllocationResolverTest {

    private val resolver = BucketAllocationResolver()
    private val cycleStart = LocalDate.of(2026, 1, 1)
    private val cycleEndExclusive = LocalDate.of(2026, 2, 1)

    @Test
    fun resolveBucketAllocation_usesExactCumulativeMathForUnevenFullCycle() {
        val allocation = resolver.resolveBucketAllocation(
            cycleStart = cycleStart,
            cycleEndExclusive = cycleEndExclusive,
            baseAllocatedAmountCents = 100L,
            adjustments = emptyList(),
            today = cycleStart.plusDays(10)
        )

        assertEquals(100L, allocation.effectiveCycleAllocationCents)
        assertEquals(32L, allocation.allocatedBeforeDateCents)
        assertEquals(3L, allocation.plannedTodayAllocationCents)
        assertEquals(100L, allocation.effectiveRecurringAllocationCents)
    }

    @Test
    fun resolveEffectiveCycleAllocationAmount_matchesExactSegmentTotalsAfterAdjustment() {
        val adjustments = listOf(
            adjustment(
                adjustmentUuid = "raise-mid-cycle",
                effectiveDate = cycleStart.plusDays(10),
                previousAllocatedAmountCents = 100L,
                newAllocatedAmountCents = 160L
            )
        )

        val effectiveAllocation = resolver.resolveEffectiveCycleAllocationAmount(
            cycleStart = cycleStart,
            cycleEndExclusive = cycleEndExclusive,
            baseAllocatedAmountCents = 100L,
            adjustments = adjustments
        )
        val allocation = resolver.resolveBucketAllocation(
            cycleStart = cycleStart,
            cycleEndExclusive = cycleEndExclusive,
            baseAllocatedAmountCents = 100L,
            adjustments = adjustments,
            today = cycleStart.plusDays(15)
        )

        assertEquals(140L, effectiveAllocation)
        assertEquals(effectiveAllocation, allocation.effectiveCycleAllocationCents)
        assertEquals(160L, allocation.effectiveRecurringAllocationCents)
        assertEquals(
            160L,
            resolver.currentAllocatedAmount(
                cycleStart,
                cycleEndExclusive,
                100L,
                adjustments,
                cycleStart.plusDays(15)
            )
        )
    }

    @Test
    fun resolveBucketAllocation_keepsAllocatedBeforeMonotonicAndPlannedTodayAsDailyDelta() {
        val adjustments = listOf(
            adjustment(
                adjustmentUuid = "raise-mid-cycle",
                effectiveDate = cycleStart.plusDays(10),
                previousAllocatedAmountCents = 100L,
                newAllocatedAmountCents = 160L
            )
        )
        val effectiveAllocation = resolver.resolveEffectiveCycleAllocationAmount(
            cycleStart = cycleStart,
            cycleEndExclusive = cycleEndExclusive,
            baseAllocatedAmountCents = 100L,
            adjustments = adjustments
        )

        var previousAllocatedBefore = -1L
        var totalPlannedToday = 0L
        generateSequence(cycleStart) { date ->
            date.plusDays(1).takeIf { it.isBefore(cycleEndExclusive) }
        }.forEach { date ->
            val allocation = resolver.resolveBucketAllocation(
                cycleStart = cycleStart,
                cycleEndExclusive = cycleEndExclusive,
                baseAllocatedAmountCents = 100L,
                adjustments = adjustments,
                today = date
            )
            val tomorrowAllocation = resolver.resolveBucketAllocation(
                cycleStart = cycleStart,
                cycleEndExclusive = cycleEndExclusive,
                baseAllocatedAmountCents = 100L,
                adjustments = adjustments,
                today = minOf(date.plusDays(1), cycleEndExclusive.minusDays(1))
            )

            assertTrue(allocation.allocatedBeforeDateCents >= previousAllocatedBefore)
            assertTrue(allocation.allocatedBeforeDateCents <= effectiveAllocation)
            if (date.plusDays(1).isBefore(cycleEndExclusive)) {
                assertEquals(
                    tomorrowAllocation.allocatedBeforeDateCents - allocation.allocatedBeforeDateCents,
                    allocation.plannedTodayAllocationCents
                )
            }
            previousAllocatedBefore = allocation.allocatedBeforeDateCents
            totalPlannedToday += allocation.plannedTodayAllocationCents
        }

        assertEquals(effectiveAllocation, totalPlannedToday)
    }

    @Test
    fun resolveBucketAllocation_preservesSingleCentAcrossThirtyOneDayCycle() {
        val plannedPerDay = generateSequence(cycleStart) { date ->
            date.plusDays(1).takeIf { it.isBefore(cycleEndExclusive) }
        }.map { date ->
            resolver.resolveBucketAllocation(
                cycleStart = cycleStart,
                cycleEndExclusive = cycleEndExclusive,
                baseAllocatedAmountCents = 1L,
                adjustments = emptyList(),
                today = date
            ).plannedTodayAllocationCents
        }.toList()

        assertEquals(1L, plannedPerDay.sum())
        assertEquals(1L, plannedPerDay.last())
        assertTrue(plannedPerDay.dropLast(1).all { it == 0L })
    }

    private fun adjustment(
        adjustmentUuid: String,
        effectiveDate: LocalDate,
        previousAllocatedAmountCents: Long,
        newAllocatedAmountCents: Long
    ) = BucketAllocationAdjustment(
        adjustmentUuid = adjustmentUuid,
        bucketUuid = "bucket-1",
        cycleStartDate = cycleStart.toString(),
        effectiveDate = effectiveDate.toString(),
        previousAllocatedAmountCents = previousAllocatedAmountCents,
        newAllocatedAmountCents = newAllocatedAmountCents,
        originInstallId = "test-install-id",
        lastModifiedByInstallId = "test-install-id",
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
        modClock = "0000000000001-0000-test-install-id"
    )
}
