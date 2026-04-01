package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.domain.model.DEFAULT_FUND_UUID
import net.loeu.wallybudget.domain.model.FundType
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalFundUseCasesTest {

    private val hybridLogicalClockService = HybridLogicalClockService()
    private val currentEpochTimeProvider = FakeCurrentEpochTimeProvider(1_234_567_890L)

    @Test
    fun createGoalFundUseCase_insertsTrimmedGoalAtNextSortOrder() = runBlocking {
        val fundDao = FakeFundDao(
            listOf(
                fundEntity(
                    uuid = DEFAULT_FUND_UUID,
                    name = "Savings",
                    fundType = FundType.DEFAULT_RESERVE,
                    sortOrder = 0
                ),
                fundEntity(
                    uuid = "goal-a",
                    name = "Travel",
                    fundType = FundType.GOAL,
                    targetAmountCents = 100_00L,
                    sortOrder = 1
                )
            )
        )
        val useCase = CreateGoalFundUseCase(
            fundDao = fundDao,
            userSettingsStore = FakeUserSettingsStore(),
            currentEpochTimeProvider = currentEpochTimeProvider,
            hybridLogicalClockService = hybridLogicalClockService
        )

        val createdUuid = useCase(
            CreateGoalFundRequest(
                name = "  New Car  ",
                targetAmountCents = 500_00L
            )
        )

        val created = requireNotNull(fundDao.findByUuid(createdUuid))
        assertEquals("New Car", created.name)
        assertEquals(FundType.GOAL, created.fundType)
        assertEquals(500_00L, created.targetAmountCents)
        assertEquals(0L, created.balanceCents)
        assertEquals(2, created.sortOrder)
        assertTrue(created.updatedAtEpochMs >= created.createdAtEpochMs)
        assertTrue(created.modClock.isNotBlank())
    }

    @Test
    fun createGoalFundUseCase_rejectsBlankNamesAndNonPositiveTargets() = runBlocking {
        val useCase = CreateGoalFundUseCase(
            fundDao = FakeFundDao(),
            userSettingsStore = FakeUserSettingsStore(),
            currentEpochTimeProvider = currentEpochTimeProvider,
            hybridLogicalClockService = hybridLogicalClockService
        )

        assertIllegalArgument("Goal name cannot be blank.") {
            useCase(CreateGoalFundRequest(name = "   ", targetAmountCents = 1L))
        }
        assertIllegalArgument("Goal target must be greater than zero.") {
            useCase(CreateGoalFundRequest(name = "Travel", targetAmountCents = 0L))
        }
    }

    @Test
    fun updateGoalFundUseCase_updatesPersistedGoalWithoutChangingBalance() = runBlocking {
        val fundDao = FakeFundDao(
            listOf(
                fundEntity(
                    uuid = DEFAULT_FUND_UUID,
                    name = "Savings",
                    fundType = FundType.DEFAULT_RESERVE,
                    sortOrder = 0
                ),
                fundEntity(
                    uuid = "goal-a",
                    name = "Travel",
                    fundType = FundType.GOAL,
                    balanceCents = 25_00L,
                    targetAmountCents = 100_00L,
                    sortOrder = 1
                )
            )
        )
        val useCase = UpdateGoalFundUseCase(
            fundDao = fundDao,
            userSettingsStore = FakeUserSettingsStore(),
            currentEpochTimeProvider = currentEpochTimeProvider,
            hybridLogicalClockService = hybridLogicalClockService
        )

        useCase(
            UpdateGoalFundRequest(
                fundUuid = "goal-a",
                name = "  Weekend Trip  ",
                targetAmountCents = 125_00L
            )
        )

        val updated = requireNotNull(fundDao.findByUuid("goal-a"))
        assertEquals("Weekend Trip", updated.name)
        assertEquals(125_00L, updated.targetAmountCents)
        assertEquals(25_00L, updated.balanceCents)
        assertEquals(1, updated.sortOrder)
        assertTrue(updated.updatedAtEpochMs >= updated.createdAtEpochMs)
        assertTrue(updated.modClock.isNotBlank())
    }

    @Test
    fun updateGoalFundUseCase_rejectsInvalidInput() = runBlocking {
        val useCase = UpdateGoalFundUseCase(
            fundDao = FakeFundDao(
                listOf(
                    fundEntity(
                        uuid = "goal-a",
                        name = "Travel",
                        fundType = FundType.GOAL,
                        targetAmountCents = 100_00L
                    )
                )
            ),
            userSettingsStore = FakeUserSettingsStore(),
            currentEpochTimeProvider = currentEpochTimeProvider,
            hybridLogicalClockService = hybridLogicalClockService
        )

        assertIllegalArgument("Goal name cannot be blank.") {
            useCase(UpdateGoalFundRequest(fundUuid = "goal-a", name = " ", targetAmountCents = 1L))
        }
        assertIllegalArgument("Goal target must be greater than zero.") {
            useCase(UpdateGoalFundRequest(fundUuid = "goal-a", name = "Travel", targetAmountCents = -1L))
        }
    }

    private suspend fun <T> assertIllegalArgument(
        message: String,
        block: suspend () -> T
    ) {
        try {
            block()
            throw AssertionError("Expected IllegalArgumentException with message: $message")
        } catch (exception: IllegalArgumentException) {
            assertEquals(message, exception.message)
        }
    }
}
