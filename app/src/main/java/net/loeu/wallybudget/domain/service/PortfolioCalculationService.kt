package net.loeu.wallybudget.domain.service

import net.loeu.wallybudget.domain.model.BucketMonthlyHistory
import net.loeu.wallybudget.domain.model.BucketSummaryState
import net.loeu.wallybudget.domain.model.Fund
import net.loeu.wallybudget.domain.model.PortfolioState
import java.time.LocalDate
import java.util.logging.Logger

class PortfolioCalculationService {
    private val logger = Logger.getLogger(PortfolioCalculationService::class.java.name)

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
        val totalPlannedCents = allocatedToBucketsCents + allocatedToFundsCents
        if (totalPlannedCents > portfolioTotalBudgetCents) {
            logger.warning(
                "Current plan exceeds portfolio budget: planned=$totalPlannedCents budget=$portfolioTotalBudgetCents"
            )
        }
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
            unassignedPlannedBudgetCents = (portfolioTotalBudgetCents - totalPlannedCents).coerceAtLeast(0L),
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
