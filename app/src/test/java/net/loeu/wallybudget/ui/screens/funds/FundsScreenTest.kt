package net.loeu.wallybudget.ui.screens.funds

import net.loeu.wallybudget.domain.model.DEFAULT_FUND_UUID
import net.loeu.wallybudget.domain.model.Fund
import net.loeu.wallybudget.domain.model.FundType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FundsScreenTest {

    @Test
    fun buildFundsOverviewUiState_separatesReserveAndOrdersActiveGoalsByPriority() {
        val reserve = fund(
            uuid = DEFAULT_FUND_UUID,
            name = "Savings",
            fundType = FundType.DEFAULT_RESERVE,
            balanceCents = 40_00L,
            targetAmountCents = 100_00L,
            sortOrder = 0,
            createdAtEpochMs = 1L
        )
        val firstGoal = fund(
            uuid = "goal-a",
            name = "Emergency",
            fundType = FundType.GOAL,
            balanceCents = 60_00L,
            targetAmountCents = 200_00L,
            sortOrder = 1,
            createdAtEpochMs = 1L
        )
        val secondGoal = fund(
            uuid = "goal-b",
            name = "Vacation",
            fundType = FundType.GOAL,
            balanceCents = 30_00L,
            targetAmountCents = 120_00L,
            sortOrder = 1,
            createdAtEpochMs = 2L
        )
        val thirdGoal = fund(
            uuid = "goal-c",
            name = "Car",
            fundType = FundType.GOAL,
            balanceCents = 15_00L,
            targetAmountCents = 300_00L,
            sortOrder = 3,
            createdAtEpochMs = 3L
        )

        val state = buildFundsOverviewUiState(
            funds = listOf(secondGoal, reserve, thirdGoal, firstGoal)
        )

        assertEquals(reserve, state.reserveFund)
        assertEquals(listOf("goal-a", "goal-b", "goal-c"), state.activeGoals.map { it.uuid })
        assertEquals(listOf(1, 2, 3), state.activeGoals.map { it.priorityOrder })
        assertEquals(140_00L, state.activeGoals[0].remainingAmountCents)
        assertEquals(60_00L, state.activeGoals[0].balanceCents)
    }

    @Test
    fun buildFundsOverviewUiState_returnsReserveOnlyWhenNoGoalsExist() {
        val reserve = fund(
            uuid = DEFAULT_FUND_UUID,
            name = "Savings",
            fundType = FundType.DEFAULT_RESERVE,
            balanceCents = 25_00L,
            targetAmountCents = null,
            sortOrder = 0,
            createdAtEpochMs = 1L
        )

        val state = buildFundsOverviewUiState(listOf(reserve))

        assertEquals(reserve, state.reserveFund)
        assertEquals(emptyList<FundGoalUiState>(), state.activeGoals)
    }

    @Test
    fun buildFundsOverviewUiState_ignoresClosedFunds() {
        val reserve = fund(
            uuid = DEFAULT_FUND_UUID,
            name = "Savings",
            fundType = FundType.DEFAULT_RESERVE,
            balanceCents = 25_00L,
            targetAmountCents = null,
            sortOrder = 0,
            createdAtEpochMs = 1L
        )
        val closedGoal = fund(
            uuid = "goal-a",
            name = "Vacation",
            fundType = FundType.GOAL,
            balanceCents = 10_00L,
            targetAmountCents = 50_00L,
            sortOrder = 1,
            createdAtEpochMs = 2L
        ).copy(closedAtEpochMs = 10L)

        val state = buildFundsOverviewUiState(listOf(reserve, closedGoal))

        assertEquals(reserve, state.reserveFund)
        assertEquals(emptyList<FundGoalUiState>(), state.activeGoals)
    }

    @Test
    fun buildFundsOverviewUiState_returnsNullReserveWhenMissing() {
        val goal = fund(
            uuid = "goal-a",
            name = "Vacation",
            fundType = FundType.GOAL,
            balanceCents = 10_00L,
            targetAmountCents = 50_00L,
            sortOrder = 1,
            createdAtEpochMs = 2L
        )

        val state = buildFundsOverviewUiState(listOf(goal))

        assertNull(state.reserveFund)
        assertEquals(listOf("goal-a"), state.activeGoals.map { it.uuid })
    }

    private fun fund(
        uuid: String,
        name: String,
        fundType: FundType,
        balanceCents: Long,
        targetAmountCents: Long?,
        sortOrder: Int,
        createdAtEpochMs: Long
    ) = Fund(
        uuid = uuid,
        name = name,
        fundType = fundType,
        balanceCents = balanceCents,
        allocationPerCycleCents = 0L,
        targetAmountCents = targetAmountCents,
        sortOrder = sortOrder,
        originInstallId = "test-install",
        lastModifiedByInstallId = "test-install",
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = createdAtEpochMs,
        modClock = "clock-$uuid"
    )
}
