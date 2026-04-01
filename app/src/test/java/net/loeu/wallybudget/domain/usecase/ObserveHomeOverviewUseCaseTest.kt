package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.coroutines.withTimeout
import net.loeu.wallybudget.data.local.entity.BucketMonthlyHistoryEntity
import net.loeu.wallybudget.data.local.entity.BudgetBucketEntity
import net.loeu.wallybudget.data.local.entity.BucketTransferEntity
import net.loeu.wallybudget.domain.model.BucketBalanceBehavior
import net.loeu.wallybudget.domain.model.BucketTrackingMode
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_NAME
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import net.loeu.wallybudget.domain.model.FundType
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.model.BucketTransferReason
import net.loeu.wallybudget.domain.model.DEFAULT_FUND_UUID
import net.loeu.wallybudget.domain.service.BucketAllocationResolver
import net.loeu.wallybudget.domain.service.BudgetAdjustmentResolver
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import net.loeu.wallybudget.domain.service.CurrentCycleBucketAllocationResolver
import net.loeu.wallybudget.domain.service.PortfolioCalculationService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ObserveHomeOverviewUseCaseTest {

    @Test
    fun invoke_combinesBudgetState_todayExpenses_sections_andPendingCloseout() = runBlocking {
        val expenseDao = FakeExpenseDao(
            listOf(
                expenseEntityOn(1L, LocalDate.of(2026, 4, 10), 2_000L),
                expenseEntityOn(2L, LocalDate.of(2026, 4, 9), 3_000L),
                expenseEntityOn(3L, LocalDate.of(2026, 4, 12), 4_000L)
            )
        )
        val bucketHistoryDao = FakeBucketMonthlyHistoryDao(
            listOf(
                BucketMonthlyHistoryEntity(
                    bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                    cycleStartDate = "2026-02-25",
                    budgetAmountCents = 100_000L,
                    totalSpentCents = 80_000L,
                    surplusCents = 20_000L,
                    cycleEndDate = "2026-03-25",
                    endTimestamp = LocalDate.of(2026, 3, 25)
                        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                )
            )
        )
        val useCase = createUseCase(
            expenseDao = expenseDao,
            settingsStore = FakeUserSettingsStore(
                userSettings(
                    monthlyBudgetCents = 100_000L,
                    portfolioMonthlyBudgetCents = 400_000L,
                    lastResetDate = LocalDate.of(2026, 3, 25),
                    pendingCycleStartDate = "2026-03-25",
                    pendingCycleEndDateExclusive = "2026-04-25"
                )
            ),
            currentDate = LocalDate.of(2026, 4, 10),
            budgetBucketDao = spendingBucketDao(defaultAllocatedAmountCents = 100_000L),
            bucketHistoryDao = bucketHistoryDao
        )

        val state = useCase().first()

        assertEquals(LocalDate.of(2026, 4, 10), state.effectiveCurrentDate)
        assertEquals(2_000L, state.selectedBucketOverview.todayExpenses.single().amountCents)
        assertEquals(LocalDate.of(2026, 3, 25), state.selectedBucketOverview.budgetState?.cycleStartDate)
        assertEquals(100_000L, state.selectedBucketOverview.budgetState?.monthlyBudgetCents)
        assertTrue(state.timelineLockState.isLocked)
        assertEquals(2, state.selectedBucketOverview.activeCycleExpenseSections.size)
        assertNotNull(state.pendingCycleCloseoutState)
    }

    @Test
    fun invoke_appliesBudgetAdjustmentsToPendingCloseoutTotals() = runBlocking {
        val expenseDao = FakeExpenseDao(
            listOf(
                expenseEntityOn(1L, LocalDate.of(2026, 3, 28), 6_000L),
                expenseEntityOn(2L, LocalDate.of(2026, 4, 5), 4_000L)
            )
        )
        val budgetPolicyDao = FakeBudgetPolicyDao(
            listOf(
                budgetPolicyEntity(
                    id = 1L,
                    cycleStart = LocalDate.of(2026, 3, 25),
                    cycleEndExclusive = LocalDate.of(2026, 4, 25),
                    budgetAmountCents = 100_000L
                )
            )
        )
        val budgetAdjustmentDao = FakeBudgetAdjustmentDao(
            listOf(
                budgetAdjustmentEntity(
                    id = 1L,
                    cycleStart = LocalDate.of(2026, 3, 25),
                    effectiveDate = LocalDate.of(2026, 4, 10),
                    previousMonthlyBudgetCents = 100_000L,
                    newMonthlyBudgetCents = 120_000L
                )
            )
        )
        val useCase = createUseCase(
            expenseDao = expenseDao,
            settingsStore = FakeUserSettingsStore(
                userSettings(
                    monthlyBudgetCents = 120_000L,
                    lastResetDate = LocalDate.of(2026, 4, 25),
                    pendingCycleStartDate = "2026-03-25",
                    pendingCycleEndDateExclusive = "2026-04-25"
                )
            ),
            currentDate = LocalDate.of(2026, 4, 26),
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            budgetBucketDao = spendingBucketDao(defaultAllocatedAmountCents = 100_000L)
        )

        val state = useCase().first()
        val pendingCloseout = requireNotNull(state.pendingCycleCloseoutState)

        assertEquals(109_678L, pendingCloseout.budgetAmountCents)
        assertEquals(99_678L, pendingCloseout.surplusCents)
    }

    @Test
    fun invoke_countsExpensesFromDeletedBucketsInPortfolioTotals() = runBlocking {
        val expenseDao = FakeExpenseDao(
            listOf(
                expenseEntityOn(1L, LocalDate.of(2026, 4, 10), 2_000L),
                expenseEntityOn(2L, LocalDate.of(2026, 4, 10), 5_000L).copy(bucketUuid = "deleted-bills-bucket")
            )
        )
        val useCase = createUseCase(
            expenseDao = expenseDao,
            settingsStore = FakeUserSettingsStore(
                userSettings(
                    monthlyBudgetCents = 100_000L,
                    portfolioMonthlyBudgetCents = 400_000L,
                    lastResetDate = LocalDate.of(2026, 3, 25),
                    pendingCycleStartDate = "2026-03-25",
                    pendingCycleEndDateExclusive = "2026-04-25"
                )
            ),
            currentDate = LocalDate.of(2026, 4, 10),
            budgetBucketDao = spendingBucketDao(defaultAllocatedAmountCents = 100_000L)
        )

        val state = useCase().first()

        assertEquals(7_000L, state.portfolioState.totalSpentThisCycleCents)
        assertEquals(393_000L, state.portfolioState.remainingThisCycleCents)
        assertEquals(2_000L, state.selectedBucketOverview.todayExpenses.single().amountCents)
        assertEquals(2_000L, state.selectedBucketOverview.summary.spentThisCycleCents)
        assertEquals(1, state.bucketSummaries.size)
    }

    @Test
    fun invoke_usesCurrentPortfolioPolicyDirectly_andExcludesFundsFromPlannedTotals() = runBlocking {
        val useCase = createUseCase(
            expenseDao = FakeExpenseDao(
                listOf(expenseEntityOn(1L, LocalDate.of(2026, 4, 10), 20_000L))
            ),
            settingsStore = FakeUserSettingsStore(
                userSettings(
                    monthlyBudgetCents = 100_000L,
                    portfolioMonthlyBudgetCents = 100_000L,
                    lastResetDate = LocalDate.of(2026, 3, 25),
                    pendingCycleStartDate = "2026-03-25",
                    pendingCycleEndDateExclusive = "2026-04-25"
                )
            ),
            currentDate = LocalDate.of(2026, 4, 10),
            budgetPolicyDao = FakeBudgetPolicyDao(
                listOf(budgetPolicyEntity(1L, LocalDate.of(2026, 3, 25), LocalDate.of(2026, 4, 25), 100_000L))
            ),
            budgetAdjustmentDao = FakeBudgetAdjustmentDao(
                listOf(
                    budgetAdjustmentEntity(
                        id = 1L,
                        cycleStart = LocalDate.of(2026, 3, 25),
                        effectiveDate = LocalDate.of(2026, 4, 1),
                        previousMonthlyBudgetCents = 100_000L,
                        newMonthlyBudgetCents = 120_000L
                    )
                )
            ),
            budgetBucketDao = spendingBucketDao(defaultAllocatedAmountCents = 60_000L),
            fundDao = FakeFundDao(
                listOf(
                    fundEntity(
                        uuid = DEFAULT_FUND_UUID,
                        balanceCents = 43_48L,
                        allocationPerCycleCents = 40_000L
                    )
                )
            )
        )

        val state = useCase().first()

        assertEquals(100_000L, state.portfolioState.portfolioTotalBudgetCents)
        assertEquals(60_000L, state.portfolioState.allocatedToBucketsCents)
        assertEquals(0L, state.portfolioState.allocatedToFundsCents)
        assertEquals(40_000L, state.portfolioState.unassignedPlannedBudgetCents)
        assertEquals(80_000L, state.portfolioState.remainingThisCycleCents)
    }

    @Test
    fun invoke_buildsExpenseSectionsOnlyForDaysWithExpenses() = runBlocking {
        val expenseDao = FakeExpenseDao(
            listOf(
                expenseEntityOn(1L, LocalDate.of(2026, 4, 9), 3_000L).copy(bucketUuid = "reserve"),
                expenseEntityOn(2L, LocalDate.of(2026, 4, 10), 2_000L).copy(bucketUuid = "reserve")
            )
        )
        val useCase = createUseCase(
            expenseDao = expenseDao,
            settingsStore = FakeUserSettingsStore(
                userSettings(
                    monthlyBudgetCents = 100_000L,
                    portfolioMonthlyBudgetCents = 100_000L,
                    lastResetDate = LocalDate.of(2026, 3, 25),
                    pendingCycleStartDate = "2026-03-25",
                    pendingCycleEndDateExclusive = "2026-04-25"
                ).copy(selectedBucketUuid = "reserve")
            ),
            currentDate = LocalDate.of(2026, 4, 10),
            budgetBucketDao = FakeBudgetBucketDao(listOf(reserveBucketEntity()))
        )

        val state = useCase().first()

        assertEquals(
            listOf(
                LocalDate.of(2026, 4, 10),
                LocalDate.of(2026, 4, 9)
            ),
            state.selectedBucketOverview.activeCycleExpenseSections.map { it.date }
        )
    }

    @Test
    fun invoke_doesNotDoubleCountSettlementTransfersInCurrentCycleTotals() = runBlocking {
        val useCase = createSettledCloseOverviewUseCase()

        val state = useCase().first()
        val defaultBucketSummary = state.bucketSummaries
            .first { it.bucket.bucketUuid == DEFAULT_SPENDING_BUCKET_UUID }
        val billsBucketSummary = state.bucketSummaries
            .first { it.bucket.bucketUuid == "bills" }

        assertEquals(350_000L, state.portfolioState.allocatedToBucketsCents)
        assertEquals(225_000L, state.portfolioState.remainingThisCycleCents)
        assertEquals(225_000L, defaultBucketSummary.allocatedThisCycleCents)
        assertEquals(125_000L, billsBucketSummary.allocatedThisCycleCents)
    }

    @Test
    fun invoke_reemitsOverviewWhenGoalFundChanges() = runBlocking {
        val fundDao = FakeFundDao(
            listOf(
                fundEntity(
                    uuid = DEFAULT_FUND_UUID,
                    name = "Savings",
                    fundType = FundType.DEFAULT_RESERVE,
                    sortOrder = 0
                )
            )
        )
        val settingsStore = FakeUserSettingsStore()
        val useCase = createUseCase(
            expenseDao = FakeExpenseDao(),
            settingsStore = settingsStore,
            currentDate = LocalDate.of(2026, 4, 10),
            fundDao = fundDao
        )
        val emissions = mutableListOf<List<String>>()
        val firstEmissionReady = CompletableDeferred<Unit>()
        val collectionJob = launch {
            useCase()
                .map { overview -> overview.funds.map { it.name } }
                .take(2)
                .collect { emission ->
                    emissions += emission
                    if (emissions.size == 1) {
                        firstEmissionReady.complete(Unit)
                    }
                }
        }

        firstEmissionReady.await()
        CreateGoalFundUseCase(
            fundDao = fundDao,
            userSettingsStore = settingsStore,
            hybridLogicalClockService = HybridLogicalClockService()
        )(
            CreateGoalFundRequest(
                name = "Travel",
                targetAmountCents = 75_00L
            )
        )

        withTimeout(5_000L) {
            collectionJob.join()
        }

        assertEquals(listOf(listOf("Savings"), listOf("Savings", "Travel")), emissions)
    }

    private fun createUseCase(
        expenseDao: FakeExpenseDao,
        settingsStore: FakeUserSettingsStore,
        currentDate: LocalDate,
        budgetPolicyDao: FakeBudgetPolicyDao = FakeBudgetPolicyDao(),
        budgetAdjustmentDao: FakeBudgetAdjustmentDao = FakeBudgetAdjustmentDao(),
        budgetBucketDao: FakeBudgetBucketDao = spendingBucketDao(),
        bucketCycleBaselineDao: FakeBucketCycleBaselineDao = FakeBucketCycleBaselineDao(),
        bucketTransferDao: FakeBucketTransferDao = FakeBucketTransferDao(),
        bucketHistoryDao: FakeBucketMonthlyHistoryDao = FakeBucketMonthlyHistoryDao(),
        fundDao: FakeFundDao = FakeFundDao()
    ): ObserveHomeOverviewUseCase {
        val budgetCalculationService = BudgetCalculationService()
        return ObserveHomeOverviewUseCase(
            expenseDao = expenseDao,
            cycleOverviewDao = FakeCycleOverviewDao(expenseDao),
            budgetAdjustmentDao = budgetAdjustmentDao,
            budgetPolicyDao = budgetPolicyDao,
            budgetBucketDao = budgetBucketDao,
            fundDao = fundDao,
            bucketCycleBaselineDao = bucketCycleBaselineDao,
            bucketTransferDao = bucketTransferDao,
            bucketMonthlyHistoryDao = bucketHistoryDao,
            userSettingsStore = settingsStore,
            currentDateProvider = FakeCurrentDateProvider(currentDate),
            budgetCalculationService = budgetCalculationService,
            cycleScheduleResolver = CycleScheduleResolver(budgetCalculationService),
            budgetAdjustmentResolver = BudgetAdjustmentResolver(),
            currentCycleBucketAllocationResolver = CurrentCycleBucketAllocationResolver(),
            portfolioCalculationService = PortfolioCalculationService()
        )
    }

    private fun spendingBucketDao(defaultAllocatedAmountCents: Long = 100_000L): FakeBudgetBucketDao {
        return FakeBudgetBucketDao(listOf(spendingBucketEntity(defaultAllocatedAmountCents)))
    }

    private fun spendingBucketEntity(defaultAllocatedAmountCents: Long): BudgetBucketEntity {
        return BudgetBucketEntity(
            bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
            name = DEFAULT_SPENDING_BUCKET_NAME,
            trackingMode = BucketTrackingMode.DAILY_TARGET,
            balanceBehavior = BucketBalanceBehavior.RETURN_TO_PORTFOLIO,
            defaultAllocatedAmountCents = defaultAllocatedAmountCents,
            sortOrder = 0,
            originInstallId = "test-install-id",
            lastModifiedByInstallId = "test-install-id",
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
            modClock = "0000000000001-0000-test-install-id"
        )
    }

    private fun reserveBucketEntity(): BudgetBucketEntity {
        return BudgetBucketEntity(
            bucketUuid = "reserve",
            name = "Reserve",
            trackingMode = BucketTrackingMode.CYCLE_RESERVE,
            balanceBehavior = BucketBalanceBehavior.RETAIN_IN_BUCKET,
            defaultAllocatedAmountCents = 100_000L,
            sortOrder = 1,
            originInstallId = "test-install-id",
            lastModifiedByInstallId = "test-install-id",
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
            modClock = "0000000000001-0000-test-install-id"
        )
    }

    private fun createSettledCloseOverviewUseCase(): ObserveHomeOverviewUseCase {
        val currentCycleStart = LocalDate.of(2026, 3, 25)
        val currentCycleEnd = LocalDate.of(2026, 4, 25)
        return createUseCase(
            expenseDao = settledCloseExpenseDao(),
            settingsStore = settledCloseSettingsStore(currentCycleStart, currentCycleEnd),
            currentDate = LocalDate.of(2026, 4, 10),
            budgetPolicyDao = settledClosePortfolioPolicyDao(currentCycleStart, currentCycleEnd),
            budgetBucketDao = settledCloseBucketDao(currentCycleEnd),
            bucketCycleBaselineDao = settledCloseBucketBaselineDao(currentCycleStart, currentCycleEnd),
            bucketTransferDao = FakeBucketTransferDao(listOf(settlementTransfer(currentCycleStart, currentCycleEnd)))
        )
    }

    private fun settledCloseExpenseDao(): FakeExpenseDao {
        return FakeExpenseDao(
            listOf(
                expenseEntityOn(1L, LocalDate.of(2026, 4, 10), 125_000L)
                    .copy(bucketUuid = "bills")
            )
        )
    }

    private fun settledCloseSettingsStore(
        currentCycleStart: LocalDate,
        currentCycleEnd: LocalDate
    ): FakeUserSettingsStore {
        return FakeUserSettingsStore(
            userSettings(
                monthlyBudgetCents = 100_000L,
                portfolioMonthlyBudgetCents = 350_000L,
                lastResetDate = currentCycleStart,
                pendingCycleStartDate = currentCycleStart.toString(),
                pendingCycleEndDateExclusive = currentCycleEnd.toString()
            )
        )
    }

    private fun settledClosePortfolioPolicyDao(
        currentCycleStart: LocalDate,
        currentCycleEnd: LocalDate
    ): FakeBudgetPolicyDao {
        return FakeBudgetPolicyDao(
            listOf(
                budgetPolicyEntity(
                    id = 1L,
                    cycleStart = currentCycleStart,
                    cycleEndExclusive = currentCycleEnd,
                    budgetAmountCents = 350_000L
                )
            )
        )
    }

    private fun settledCloseBucketDao(currentCycleEnd: LocalDate): FakeBudgetBucketDao {
        return FakeBudgetBucketDao(
            listOf(
                bucketEntity(
                    bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                    name = DEFAULT_SPENDING_BUCKET_NAME,
                    defaultAllocatedAmountCents = 100_000L
                ),
                bucketEntity(
                    id = 2L,
                    bucketUuid = "bills",
                    name = "Bills",
                    trackingMode = BucketTrackingMode.CYCLE_RESERVE,
                    balanceBehavior = BucketBalanceBehavior.RETURN_TO_PORTFOLIO,
                    defaultAllocatedAmountCents = 250_000L,
                    sortOrder = 1,
                    settledCloseCycleEndDateExclusive = currentCycleEnd.toString()
                )
            )
        )
    }

    private fun settledCloseBucketPolicyDao(
        currentCycleStart: LocalDate,
        currentCycleEnd: LocalDate
    ): FakeBucketAllocationPolicyDao {
        return FakeBucketAllocationPolicyDao(
            listOf(
                bucketPolicyEntity(
                    allocationUuid = "default-current",
                    cycleStartDate = currentCycleStart.toString(),
                    cycleEndDateExclusive = currentCycleEnd.toString(),
                    allocatedAmountCents = 225_000L
                ),
                bucketPolicyEntity(
                    id = 2L,
                    allocationUuid = "bills-current",
                    bucketUuid = "bills",
                    cycleStartDate = currentCycleStart.toString(),
                    cycleEndDateExclusive = currentCycleEnd.toString(),
                    allocatedAmountCents = 125_000L
                )
            )
        )
    }

    private fun settledCloseBucketBaselineDao(
        currentCycleStart: LocalDate,
        currentCycleEnd: LocalDate
    ): FakeBucketCycleBaselineDao {
        return FakeBucketCycleBaselineDao(
            listOf(
                bucketCycleBaselineEntity(
                    id = 1L,
                    baselineUuid = "default-baseline",
                    bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                    cycleStartDate = currentCycleStart.toString(),
                    cycleEndDateExclusive = currentCycleEnd.toString(),
                    baselineAmountCents = 100_000L
                ),
                bucketCycleBaselineEntity(
                    id = 2L,
                    baselineUuid = "bills-baseline",
                    bucketUuid = "bills",
                    cycleStartDate = currentCycleStart.toString(),
                    cycleEndDateExclusive = currentCycleEnd.toString(),
                    baselineAmountCents = 250_000L
                )
            )
        )
    }

    private fun settlementTransfer(
        currentCycleStart: LocalDate,
        currentCycleEnd: LocalDate
    ): BucketTransferEntity {
        return BucketTransferEntity(
            id = 1L,
            transferUuid = "settlement-1",
            fromBucketUuid = "bills",
            toBucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
            amountCents = 125_000L,
            reason = BucketTransferReason.CLOSE_SETTLEMENT,
            cycleStartDate = currentCycleStart.toString(),
            cycleEndDateExclusive = currentCycleEnd.toString(),
            effectiveDate = LocalDate.of(2026, 4, 10).toString(),
            originInstallId = "test-install-id",
            lastModifiedByInstallId = "test-install-id",
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
            modClock = "0000000000001-0000-test-install-id"
        )
    }

    private fun userSettings(
        monthlyBudgetCents: Long,
        lastResetDate: LocalDate,
        pendingCycleStartDate: String,
        pendingCycleEndDateExclusive: String,
        portfolioMonthlyBudgetCents: Long? = null
    ): UserSettings {
        return UserSettings(
            monthlyBudgetCents = monthlyBudgetCents,
            portfolioMonthlyBudgetCents = portfolioMonthlyBudgetCents,
            paydayDate = 25,
            selectedBucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
            lastResetTimestamp = lastResetDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            pendingCycleStartDate = pendingCycleStartDate,
            pendingCycleEndDateExclusive = pendingCycleEndDateExclusive
        )
    }
}
