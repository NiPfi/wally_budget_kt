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
        val allocatedToFundsCents = 0L
        val isAllocatedOverBudget = allocatedToBucketsCents > portfolioTotalBudgetCents
        val completedCycleReserveCents = bucketHistory
            .filter { it.getCycleEnd() <= cycleStartDate }
            .sumOf { it.surplusCents }
        val remainingThisCycleCents = portfolioTotalBudgetCents - totalSpentThisCycleCents
        val netReserveCents = completedCycleReserveCents + remainingThisCycleCents
        val totalFundBalanceCents = funds.sumOf { it.balanceCents }

        return PortfolioState(
            portfolioTotalBudgetCents = portfolioTotalBudgetCents,
            allocatedToBucketsCents = allocatedToBucketsCents,
            allocatedToFundsCents = allocatedToFundsCents,
            unassignedPlannedBudgetCents = (portfolioTotalBudgetCents - allocatedToBucketsCents).coerceAtLeast(0L),
            totalSpentThisCycleCents = totalSpentThisCycleCents,
            remainingThisCycleCents = remainingThisCycleCents,
            completedCycleReserveCents = completedCycleReserveCents,
            netReserveCents = netReserveCents,
            totalFundBalanceCents = totalFundBalanceCents,
            cycleStartDate = cycleStartDate,
            cycleEndDateExclusive = cycleEndDateExclusive,
            isAllocatedOverBudget = isAllocatedOverBudget
        )
    }
}
