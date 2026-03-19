package net.loeu.wallybudget.domain.service

import net.loeu.wallybudget.domain.model.BucketMonthlyHistory
import net.loeu.wallybudget.domain.model.BucketSummaryState
import net.loeu.wallybudget.domain.model.PortfolioState
import java.time.LocalDate

class PortfolioCalculationService {
    fun calculatePortfolioState(
        portfolioTotalBudgetCents: Long,
        bucketSummaries: List<BucketSummaryState>,
        totalSpentThisCycleCents: Long,
        bucketHistory: List<BucketMonthlyHistory>,
        cycleStartDate: LocalDate,
        cycleEndDateExclusive: LocalDate
    ): PortfolioState {
        val allocatedToBucketsCents = bucketSummaries.sumOf { it.allocatedThisCycleCents }
        val completedCycleReserveCents = bucketHistory
            .filter { it.getCycleEnd() <= cycleStartDate }
            .sumOf { it.surplusCents }
        val remainingThisCycleCents = portfolioTotalBudgetCents - totalSpentThisCycleCents
        val netReserveCents = completedCycleReserveCents + remainingThisCycleCents
        val earmarkedReserveCents = bucketSummaries
            .filter {
                it.bucket.balanceBehavior ==
                    net.loeu.wallybudget.domain.model.BucketBalanceBehavior.RETAIN_IN_BUCKET
            }
            .filterNot { it.bucket.isClosed }
            .sumOf { it.earmarkedBalanceCents }
        val unassignedReserveCents = netReserveCents - earmarkedReserveCents

        return PortfolioState(
            portfolioTotalBudgetCents = portfolioTotalBudgetCents,
            allocatedToBucketsCents = allocatedToBucketsCents,
            unassignedPlannedBudgetCents = (portfolioTotalBudgetCents - allocatedToBucketsCents).coerceAtLeast(0L),
            totalSpentThisCycleCents = totalSpentThisCycleCents,
            remainingThisCycleCents = remainingThisCycleCents,
            completedCycleReserveCents = completedCycleReserveCents,
            netReserveCents = netReserveCents,
            earmarkedReserveCents = earmarkedReserveCents,
            unassignedReserveCents = unassignedReserveCents,
            cycleStartDate = cycleStartDate,
            cycleEndDateExclusive = cycleEndDateExclusive
        )
    }

    fun calculateBucketEarmarkedBalanceCents(
        bucketHistory: List<BucketMonthlyHistory>,
        currentRemainingThisCycleCents: Long,
        currentCycleStartDate: LocalDate
    ): Long {
        val completedCarryover = bucketHistory
            .filter { it.getCycleEnd() <= currentCycleStartDate }
            .sumOf { it.surplusCents }
        return (completedCarryover + currentRemainingThisCycleCents).coerceAtLeast(0L)
    }
}
