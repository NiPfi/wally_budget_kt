package net.loeu.wallybudget.domain.service

import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.BucketBalanceBehavior
import net.loeu.wallybudget.domain.model.BucketSummaryState
import net.loeu.wallybudget.domain.model.BucketTrackingMode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PortfolioCalculationServiceTest {

    private val service = PortfolioCalculationService()

    @Test
    fun calculatePortfolioState_usesAllocatedBucketsAsCycleBaseline_whenPlanExceedsStoredPortfolioBudget() {
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

        assertEquals(450_000L, state.portfolioTotalBudgetCents)
        assertEquals(450_000L, state.allocatedToBucketsCents)
        assertEquals(0L, state.completedCycleReserveCents)
        assertEquals(-29_125L, state.remainingThisCycleCents)
        assertEquals(-29_125L, state.netReserveCents)
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
}
