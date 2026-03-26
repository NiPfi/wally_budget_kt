package net.loeu.wallybudget.domain.service

import net.loeu.wallybudget.domain.model.BucketAllocationAdjustment
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class ResolvedBucketAllocation(
    val effectiveCycleAllocationCents: Long,
    val allocatedBeforeDateCents: Long,
    val plannedTodayAllocationCents: Long,
    val effectiveRecurringAllocationCents: Long
)

class BucketAllocationResolver {
    // Historical reconstruction only. Do not use for active/current-cycle reads.
    fun resolveBucketAllocation(
        cycleStart: LocalDate,
        cycleEndExclusive: LocalDate,
        baseAllocatedAmountCents: Long,
        adjustments: List<BucketAllocationAdjustment>,
        today: LocalDate
    ): ResolvedBucketAllocation {
        val normalizedToday = today.coerceInRange(cycleStart, cycleEndExclusive.minusDays(1))
        val segments = segmentsForCycle(
            cycleStart = cycleStart,
            cycleEndExclusive = cycleEndExclusive,
            baseAllocatedAmountCents = baseAllocatedAmountCents,
            adjustments = adjustments
        )
        val effectiveCycleAllocationCents = segments.sumOf { it.segmentAllocationCents }
        val allocatedBeforeDateCents = allocatedBeforeDate(segments, normalizedToday)
        val activeSegment = segments.firstOrNull { segment ->
            !normalizedToday.isBefore(segment.start) && normalizedToday.isBefore(segment.endExclusive)
        }
        val plannedTodayAllocationCents = if (activeSegment == null) {
            0L
        } else {
            allocatedBeforeDate(
                segments,
                minOf(normalizedToday.plusDays(1), cycleEndExclusive)
            ) - allocatedBeforeDateCents
        }
        val effectiveRecurringAllocationCents =
            segments.lastOrNull()?.allocatedAmountCents ?: baseAllocatedAmountCents

        return ResolvedBucketAllocation(
            effectiveCycleAllocationCents = effectiveCycleAllocationCents,
            allocatedBeforeDateCents = allocatedBeforeDateCents,
            plannedTodayAllocationCents = plannedTodayAllocationCents,
            effectiveRecurringAllocationCents = effectiveRecurringAllocationCents
        )
    }

    fun resolveEffectiveCycleAllocationAmount(
        cycleStart: LocalDate,
        cycleEndExclusive: LocalDate,
        baseAllocatedAmountCents: Long,
        adjustments: List<BucketAllocationAdjustment>
    ): Long {
        return segmentsForCycle(
            cycleStart = cycleStart,
            cycleEndExclusive = cycleEndExclusive,
            baseAllocatedAmountCents = baseAllocatedAmountCents,
            adjustments = adjustments
        ).sumOf { it.segmentAllocationCents }
    }

    fun currentAllocatedAmount(
        cycleStart: LocalDate,
        cycleEndExclusive: LocalDate,
        baseAllocatedAmountCents: Long,
        adjustments: List<BucketAllocationAdjustment>,
        onDate: LocalDate
    ): Long {
        val normalizedDate = onDate.coerceInRange(cycleStart, cycleEndExclusive.minusDays(1))
        return segmentsForCycle(cycleStart, cycleEndExclusive, baseAllocatedAmountCents, adjustments)
            .lastOrNull { !normalizedDate.isBefore(it.start) }
            ?.allocatedAmountCents
            ?: baseAllocatedAmountCents
    }

    private fun allocatedBeforeDate(
        segments: List<BucketAllocationSegment>,
        date: LocalDate
    ): Long {
        return segments.sumOf { segment -> segment.allocatedBefore(date) }
    }

    private fun segmentsForCycle(
        cycleStart: LocalDate,
        cycleEndExclusive: LocalDate,
        baseAllocatedAmountCents: Long,
        adjustments: List<BucketAllocationAdjustment>
    ): List<BucketAllocationSegment> {
        if (!cycleStart.isBefore(cycleEndExclusive)) return emptyList()

        val fullCycleDays = ChronoUnit.DAYS.between(cycleStart, cycleEndExclusive).toInt().coerceAtLeast(1)
        val cycleAdjustments = adjustments
            .filter { it.deletedAtEpochMs == null }
            .filter { it.cycleStart() == cycleStart }
            .filter { !it.effectiveDate().isBefore(cycleStart) && it.effectiveDate().isBefore(cycleEndExclusive) }
            .sortedWith(compareBy<BucketAllocationAdjustment> { it.effectiveDate() }.thenBy { it.updatedAtEpochMs })

        val segments = mutableListOf<BucketAllocationSegment>()
        var segmentStart = cycleStart
        var allocatedAmount = baseAllocatedAmountCents

        cycleAdjustments.forEach { adjustment ->
            if (adjustment.effectiveDate().isAfter(segmentStart)) {
                segments += BucketAllocationSegment(
                    start = segmentStart,
                    endExclusive = adjustment.effectiveDate(),
                    allocatedAmountCents = allocatedAmount,
                    fullCycleDays = fullCycleDays
                )
            }
            segmentStart = adjustment.effectiveDate()
            allocatedAmount = adjustment.newAllocatedAmountCents
        }

        if (segmentStart.isBefore(cycleEndExclusive)) {
            segments += BucketAllocationSegment(
                start = segmentStart,
                endExclusive = cycleEndExclusive,
                allocatedAmountCents = allocatedAmount,
                fullCycleDays = fullCycleDays
            )
        }

        return if (segments.isEmpty()) {
            listOf(
                BucketAllocationSegment(
                    start = cycleStart,
                    endExclusive = cycleEndExclusive,
                    allocatedAmountCents = baseAllocatedAmountCents,
                    fullCycleDays = fullCycleDays
                )
            )
        } else {
            segments
        }
    }
}

private data class BucketAllocationSegment(
    val start: LocalDate,
    val endExclusive: LocalDate,
    val allocatedAmountCents: Long,
    val fullCycleDays: Int
) {
    private val days: Int = ChronoUnit.DAYS.between(start, endExclusive).toInt().coerceAtLeast(1)

    val segmentAllocationCents: Long = proportionalAllocation(days)

    fun allocatedBefore(date: LocalDate): Long {
        if (!date.isAfter(start)) return 0L
        val coveredDays = ChronoUnit.DAYS.between(start, minOf(date, endExclusive)).toInt().coerceAtLeast(0)
        return proportionalAllocation(coveredDays)
    }

    private fun proportionalAllocation(coveredDays: Int): Long {
        return Math.floorDiv(allocatedAmountCents * coveredDays.toLong(), fullCycleDays.toLong())
    }
}

private fun LocalDate.coerceInRange(startInclusive: LocalDate, endInclusive: LocalDate): LocalDate {
    return when {
        isBefore(startInclusive) -> startInclusive
        isAfter(endInclusive) -> endInclusive
        else -> this
    }
}
