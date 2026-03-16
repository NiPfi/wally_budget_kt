package net.loeu.wallybudget.domain.service

import net.loeu.wallybudget.domain.model.BudgetAdjustment
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

data class ResolvedCycleBudget(
    val effectiveCycleBudgetCents: Long,
    val allocatedBeforeDateCents: Long,
    val plannedTodayBudgetCents: Long,
    val effectiveMonthlyBudgetCents: Long
)

class BudgetAdjustmentResolver {
    fun resolveCycleBudget(
        cycleStart: LocalDate,
        cycleEndExclusive: LocalDate,
        baseMonthlyBudgetCents: Long,
        adjustments: List<BudgetAdjustment>,
        today: LocalDate
    ): ResolvedCycleBudget {
        val normalizedToday = today.coerceInRange(cycleStart, cycleEndExclusive.minusDays(1))
        val segments = segmentsForCycle(
            cycleStart = cycleStart,
            cycleEndExclusive = cycleEndExclusive,
            baseMonthlyBudgetCents = baseMonthlyBudgetCents,
            adjustments = adjustments
        )
        val effectiveCycleBudgetCents = segments.sumOf { it.segmentBudgetCents }
        val allocatedBeforeDateCents = segments.sumOf { segment ->
            segment.allocatedBefore(normalizedToday)
        }
        val plannedTodayBudgetCents = segments.firstOrNull { segment ->
            !normalizedToday.isBefore(segment.start) && normalizedToday.isBefore(segment.endExclusive)
        }?.dailyBudgetCents ?: 0L
        val effectiveMonthlyBudgetCents = segments.lastOrNull()?.monthlyBudgetCents ?: baseMonthlyBudgetCents

        return ResolvedCycleBudget(
            effectiveCycleBudgetCents = effectiveCycleBudgetCents,
            allocatedBeforeDateCents = allocatedBeforeDateCents,
            plannedTodayBudgetCents = plannedTodayBudgetCents,
            effectiveMonthlyBudgetCents = effectiveMonthlyBudgetCents
        )
    }

    fun resolveEffectiveCycleBudgetAmount(
        cycleStart: LocalDate,
        cycleEndExclusive: LocalDate,
        baseMonthlyBudgetCents: Long,
        adjustments: List<BudgetAdjustment>
    ): Long {
        return segmentsForCycle(
            cycleStart = cycleStart,
            cycleEndExclusive = cycleEndExclusive,
            baseMonthlyBudgetCents = baseMonthlyBudgetCents,
            adjustments = adjustments
        ).sumOf { it.segmentBudgetCents }
    }

    fun currentMonthlyBudget(
        cycleStart: LocalDate,
        cycleEndExclusive: LocalDate,
        baseMonthlyBudgetCents: Long,
        adjustments: List<BudgetAdjustment>,
        onDate: LocalDate
    ): Long {
        val normalizedDate = onDate.coerceInRange(cycleStart, cycleEndExclusive.minusDays(1))
        return segmentsForCycle(cycleStart, cycleEndExclusive, baseMonthlyBudgetCents, adjustments)
            .lastOrNull { !normalizedDate.isBefore(it.start) }
            ?.monthlyBudgetCents
            ?: baseMonthlyBudgetCents
    }

    private fun segmentsForCycle(
        cycleStart: LocalDate,
        cycleEndExclusive: LocalDate,
        baseMonthlyBudgetCents: Long,
        adjustments: List<BudgetAdjustment>
    ): List<BudgetBudgetSegment> {
        if (!cycleStart.isBefore(cycleEndExclusive)) return emptyList()

        val fullCycleDays = ChronoUnit.DAYS.between(cycleStart, cycleEndExclusive).toInt().coerceAtLeast(1)
        val cycleAdjustments = adjustments
            .filter { it.deletedAtEpochMs == null }
            .filter { it.cycleStart() == cycleStart }
            .filter { !it.effectiveDate().isBefore(cycleStart) && it.effectiveDate().isBefore(cycleEndExclusive) }
            .sortedWith(compareBy<BudgetAdjustment> { it.effectiveDate() }.thenBy { it.updatedAtEpochMs })

        val segments = mutableListOf<BudgetBudgetSegment>()
        var segmentStart = cycleStart
        var monthlyBudget = baseMonthlyBudgetCents

        cycleAdjustments.forEach { adjustment ->
            if (adjustment.effectiveDate().isAfter(segmentStart)) {
                segments += BudgetBudgetSegment(
                    start = segmentStart,
                    endExclusive = adjustment.effectiveDate(),
                    monthlyBudgetCents = monthlyBudget,
                    fullCycleDays = fullCycleDays
                )
            }
            segmentStart = adjustment.effectiveDate()
            monthlyBudget = adjustment.newMonthlyBudgetCents
        }

        if (segmentStart.isBefore(cycleEndExclusive)) {
            segments += BudgetBudgetSegment(
                start = segmentStart,
                endExclusive = cycleEndExclusive,
                monthlyBudgetCents = monthlyBudget,
                fullCycleDays = fullCycleDays
            )
        }

        return if (segments.isEmpty()) {
            listOf(
                BudgetBudgetSegment(
                    start = cycleStart,
                    endExclusive = cycleEndExclusive,
                    monthlyBudgetCents = baseMonthlyBudgetCents,
                    fullCycleDays = fullCycleDays
                )
            )
        } else {
            segments
        }
    }
}

private data class BudgetBudgetSegment(
    val start: LocalDate,
    val endExclusive: LocalDate,
    val monthlyBudgetCents: Long,
    val fullCycleDays: Int
) {
    private val days: Int = ChronoUnit.DAYS.between(start, endExclusive).toInt().coerceAtLeast(1)

    val dailyBudgetCents: Long = (monthlyBudgetCents.toDouble() / fullCycleDays).roundToLong()

    val segmentBudgetCents: Long = ((monthlyBudgetCents.toDouble() * days) / fullCycleDays).roundToLong()

    fun allocatedBefore(date: LocalDate): Long {
        if (!date.isAfter(start)) return 0L
        val coveredDays = ChronoUnit.DAYS.between(start, minOf(date, endExclusive)).toInt().coerceAtLeast(0)
        return ((monthlyBudgetCents.toDouble() * coveredDays) / fullCycleDays).roundToLong()
    }
}

private fun LocalDate.coerceInRange(startInclusive: LocalDate, endInclusive: LocalDate): LocalDate {
    return when {
        isBefore(startInclusive) -> startInclusive
        isAfter(endInclusive) -> endInclusive
        else -> this
    }
}
