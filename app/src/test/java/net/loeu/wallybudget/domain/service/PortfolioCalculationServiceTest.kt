package net.loeu.wallybudget.domain.service

import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.BucketBalanceBehavior
import net.loeu.wallybudget.domain.model.BucketSummaryState
import net.loeu.wallybudget.domain.model.BucketTrackingMode
import net.loeu.wallybudget.domain.model.Fund
import net.loeu.wallybudget.domain.model.FundType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PortfolioCalculationServiceTest {

    private val service = PortfolioCalculationService()

    @Test
    fun calculatePortfolioState_usesStoredPortfolioBudget_whenPlanExceedsStoredPortfolioBudget() {
        val state = service.calculatePortfolioState(
            portfolioTotalBudgetCents = 100_000L,
            bucketSummaries = listOf(
                summary(bucket("default"), allocatedThisCycleCents = 100_000L, spentThisCycleCents = 74_125L),
                summary(bucket("bills"), allocatedThisCycleCents = 350_000L, spentThisCycleCents = 405_000L)
            ),
            totalSpentThisCycleCents = 479_125L,
            bucketHistory = emptyList(),
            cycleStartDate = LocalDate.of(2026, 2, 25),
            cycleEndDateExclusive = LocalDate.of(2026, 3, 25)
        )

        assertEquals(100_000L, state.portfolioTotalBudgetCents)
        assertEquals(450_000L, state.allocatedToBucketsCents)
        assertEquals(0L, state.completedCycleReserveCents)
        assertEquals(-379_125L, state.remainingThisCycleCents)
        assertEquals(-379_125L, state.netReserveCents)
    }

    @Test
    fun calculatePortfolioState_usesStoredPortfolioBudgetAsCycleBaseline_whenPlanIsAtLeastAllocated() {
        val state = service.calculatePortfolioState(
            portfolioTotalBudgetCents = 500_000L,
            bucketSummaries = listOf(
                summary(bucket("default"), allocatedThisCycleCents = 100_000L, spentThisCycleCents = 74_125L),
                summary(bucket("bills"), allocatedThisCycleCents = 350_000L, spentThisCycleCents = 405_000L)
            ),
            totalSpentThisCycleCents = 479_125L,
            bucketHistory = emptyList(),
            cycleStartDate = LocalDate.of(2026, 2, 25),
            cycleEndDateExclusive = LocalDate.of(2026, 3, 25)
        )

        assertEquals(500_000L, state.portfolioTotalBudgetCents)
        assertEquals(450_000L, state.allocatedToBucketsCents)
        assertEquals(50_000L, state.unassignedPlannedBudgetCents)
        assertEquals(0L, state.completedCycleReserveCents)
        assertEquals(20_875L, state.remainingThisCycleCents)
        assertEquals(20_875L, state.netReserveCents)
    }

    @Test
    fun calculatePortfolioState_excludesFundsFromCurrentPlannedTotals() {
        val state = service.calculatePortfolioState(
            portfolioTotalBudgetCents = 500_000L,
            bucketSummaries = listOf(
                summary(bucket("default"), allocatedThisCycleCents = 300_000L, spentThisCycleCents = 0L)
            ),
            funds = listOf(fund(allocationPerCycleCents = 200_000L)),
            totalSpentThisCycleCents = 0L,
            bucketHistory = emptyList(),
            cycleStartDate = LocalDate.of(2026, 2, 25),
            cycleEndDateExclusive = LocalDate.of(2026, 3, 25)
        )

        assertEquals(300_000L, state.allocatedToBucketsCents)
        assertEquals(0L, state.allocatedToFundsCents)
        assertEquals(200_000L, state.unassignedPlannedBudgetCents)
    }

    private fun summary(
        bucket: BudgetBucket,
        allocatedThisCycleCents: Long,
        spentThisCycleCents: Long
    ) = BucketSummaryState(
        bucket = bucket,
        allocatedThisCycleCents = allocatedThisCycleCents,
        spentThisCycleCents = spentThisCycleCents,
        remainingThisCycleCents = allocatedThisCycleCents - spentThisCycleCents,
        overspentCents = (spentThisCycleCents - allocatedThisCycleCents).coerceAtLeast(0L),
        earmarkedBalanceCents = 0L
    )

    private fun bucket(bucketUuid: String) = BudgetBucket(
        bucketUuid = bucketUuid,
        name = bucketUuid,
        trackingMode = BucketTrackingMode.DAILY_TARGET,
        balanceBehavior = BucketBalanceBehavior.RETURN_TO_PORTFOLIO,
        defaultAllocatedAmountCents = 0L,
        sortOrder = 0,
        originInstallId = "test-install-id",
        lastModifiedByInstallId = "test-install-id",
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
        modClock = "0000000000001-0000-test-install-id"
    )

    private fun fund(allocationPerCycleCents: Long) = Fund(
        uuid = "fund",
        name = "Savings",
        fundType = FundType.GOAL,
        balanceCents = 0L,
        allocationPerCycleCents = allocationPerCycleCents,
        targetAmountCents = null,
        sortOrder = 0,
        originInstallId = "test-install-id",
        lastModifiedByInstallId = "test-install-id",
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
        modClock = "0000000000001-0000-test-install-id"
    )
}
