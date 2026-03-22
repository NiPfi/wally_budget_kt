package net.loeu.wallybudget.domain.service

import net.loeu.wallybudget.domain.model.BucketMonthlyHistory
import net.loeu.wallybudget.domain.model.BucketSummaryState
import net.loeu.wallybudget.domain.model.FundState
import net.loeu.wallybudget.domain.model.PortfolioState
import java.time.LocalDate

class PortfolioCalculationService {
    fun calculatePortfolioState(
        portfolioTotalBudgetCents: Long,
        bucketSummaries: List<BucketSummaryState>,
        fundStates: List<FundState> = emptyList(),
        totalSpentThisCycleCents: Long,
        bucketHistory: List<BucketMonthlyHistory>,
        cycleStartDate: LocalDate,
        cycleEndDateExclusive: LocalDate
    ): PortfolioState {
        val allocatedToBucketsCents = bucketSummaries.sumOf { it.allocatedThisCycleCents }
        val allocatedToFundsCents = fundStates.sumOf { it.fund.allocationPerCycleCents }
        // Use one effective baseline for current-cycle portfolio math so reserve and plan
        // calculations cannot diverge when the portfolio plan was increased mid-cycle.
        val totalPlannedCents = allocatedToBucketsCents + allocatedToFundsCents
        val effectiveCycleBaselineCents = maxOf(portfolioTotalBudgetCents, totalPlannedCents)
        val completedCycleReserveCents = bucketHistory
            .filter { it.getCycleEnd() <= cycleStartDate }
            .sumOf { it.surplusCents }
        val remainingThisCycleCents = effectiveCycleBaselineCents - totalSpentThisCycleCents
        val netReserveCents = completedCycleReserveCents + remainingThisCycleCents
        val totalFundBalanceCents = fundStates.sumOf { it.balanceCents }

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
