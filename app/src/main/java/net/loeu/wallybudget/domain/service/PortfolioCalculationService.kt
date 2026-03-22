package net.loeu.wallybudget.domain.service

import net.loeu.wallybudget.domain.model.BucketMonthlyHistory
import net.loeu.wallybudget.domain.model.BucketSummaryState
import net.loeu.wallybudget.domain.model.Fund
import net.loeu.wallybudget.domain.model.PortfolioState
import java.time.LocalDate

class PortfolioCalculationService {
    fun calculatePortfolioState(
        portfolioTotalBudgetCents: Long,
        bucketSummaries: List<BucketSummaryState>,
        funds: List<Fund> = emptyList(),
        totalSpentThisCycleCents: Long,
        bucketHistory: List<BucketMonthlyHistory>,
        cycleStartDate: LocalDate,
        cycleEndDateExclusive: LocalDate
    ): PortfolioState {
        val allocatedToBucketsCents = bucketSummaries.sumOf { it.allocatedThisCycleCents }
        val allocatedToFundsCents = funds.sumOf { it.allocationPerCycleCents }
        // Use one effective baseline for current-cycle portfolio math so reserve and plan
        // calculations cannot diverge when the portfolio plan was increased mid-cycle.
        val totalPlannedCents = allocatedToBucketsCents + allocatedToFundsCents
        val effectiveCycleBaselineCents = maxOf(portfolioTotalBudgetCents, totalPlannedCents)
        val completedCycleReserveCents = bucketHistory
            .filter { it.getCycleEnd() <= cycleStartDate }
            .sumOf { it.surplusCents }
        val remainingThisCycleCents = effectiveCycleBaselineCents - totalSpentThisCycleCents
        val netReserveCents = completedCycleReserveCents + remainingThisCycleCents
        val totalFundBalanceCents = funds.sumOf { it.balanceCents }

        return PortfolioState(
            portfolioTotalBudgetCents = effectiveCycleBaselineCents,
            allocatedToBucketsCents = allocatedToBucketsCents,
            allocatedToFundsCents = allocatedToFundsCents,
            unassignedPlannedBudgetCents = (effectiveCycleBaselineCents - totalPlannedCents).coerceAtLeast(0L),
            totalSpentThisCycleCents = totalSpentThisCycleCents,
            remainingThisCycleCents = remainingThisCycleCents,
            completedCycleReserveCents = completedCycleReserveCents,
            netReserveCents = netReserveCents,
            totalFundBalanceCents = totalFundBalanceCents,
            cycleStartDate = cycleStartDate,
            cycleEndDateExclusive = cycleEndDateExclusive
        )
    }
}
