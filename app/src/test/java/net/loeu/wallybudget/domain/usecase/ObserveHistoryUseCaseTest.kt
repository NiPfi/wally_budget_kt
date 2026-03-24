@file:Suppress("LongMethod")

package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.BucketBalanceBehavior
import net.loeu.wallybudget.domain.model.BucketSummaryState
import net.loeu.wallybudget.domain.model.BucketTrackingMode
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_NAME
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import net.loeu.wallybudget.domain.model.PortfolioOverviewState
import net.loeu.wallybudget.domain.model.PortfolioState
import net.loeu.wallybudget.domain.model.SelectedBucketOverview
import net.loeu.wallybudget.domain.model.TimelineLockState
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ObserveHistoryUseCaseTest {
    @Test
    fun invoke_buildsCurrentFutureAndCompletedSections_inDisplayOrder() = runBlocking {
        val expenseDao = FakeExpenseDao(
            listOf(
                expenseEntityOn(4L, LocalDate.of(2026, 3, 10), 1_500L),
                expenseEntityOn(5L, LocalDate.of(2026, 2, 5), 2_500L),
                expenseEntityOn(1L, LocalDate.of(2026, 4, 10), 2_000L),
                expenseEntityOn(2L, LocalDate.of(2026, 4, 12), 3_000L),
                expenseEntityOn(3L, LocalDate.of(2026, 3, 28), 4_000L)
            )
        )
        val historyDao = FakeMonthlyHistoryDao(
            listOf(
                historyEntity(LocalDate.of(2026, 2, 25), LocalDate.of(2026, 3, 25), 50_000L),
                historyEntity(LocalDate.of(2026, 1, 25), LocalDate.of(2026, 2, 25), 60_000L)
            )
        )
        val budgetPolicyDao = FakeBudgetPolicyDao()
        val budgetBucketDao = FakeBudgetBucketDao(listOf(defaultSpendingBucketEntity()))
        val bucketAllocationPolicyDao = FakeBucketAllocationPolicyDao()
        val bucketHistoryDao = FakeBucketMonthlyHistoryDao()
        val settingsStore = FakeUserSettingsStore(
            UserSettings(
                monthlyBudgetCents = 100_000L,
                paydayDate = 25,
                primaryBucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                selectedBucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                lastResetTimestamp = LocalDate.of(2026, 3, 25)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            )
        )
        val budgetCalculationService = BudgetCalculationService()
        val useCase = ObserveHistoryUseCase(
            expenseDao = expenseDao,
            monthlyHistoryDao = historyDao,
            budgetPolicyDao = budgetPolicyDao,
            budgetBucketDao = budgetBucketDao,
            bucketAllocationPolicyDao = bucketAllocationPolicyDao,
            bucketMonthlyHistoryDao = bucketHistoryDao,
            userSettingsStore = settingsStore,
            cycleScheduleResolver = CycleScheduleResolver(budgetCalculationService)
        )
        val today = LocalDate.of(2026, 4, 10)
        val bucket = defaultSpendingBucket()
        val portfolioOverviewState = PortfolioOverviewState(
            effectiveCurrentDate = today,
            portfolioState = PortfolioState(
                portfolioTotalBudgetCents = 100_000L,
                allocatedToBucketsCents = 100_000L,
                unassignedPlannedBudgetCents = 0L,
                totalSpentThisCycleCents = 6_000L,
                remainingThisCycleCents = 94_000L,
                completedCycleReserveCents = 0L,
                netReserveCents = 94_000L,

                cycleStartDate = LocalDate.of(2026, 3, 25),
                cycleEndDateExclusive = LocalDate.of(2026, 4, 25)
            ),
            bucketSummaries = listOf(
                BucketSummaryState(
                    bucket = bucket,
                    allocatedThisCycleCents = 100_000L,
                    spentThisCycleCents = 6_000L,
                    remainingThisCycleCents = 94_000L,
                    overspentCents = 0L,
                    earmarkedBalanceCents = 0L
                )
            ),
            selectedBucketOverview = SelectedBucketOverview(
                bucket = bucket,
                summary = BucketSummaryState(
                    bucket = bucket,
                    allocatedThisCycleCents = 100_000L,
                    spentThisCycleCents = 6_000L,
                    remainingThisCycleCents = 94_000L,
                    overspentCents = 0L,
                    earmarkedBalanceCents = 0L
                ),
                budgetState = budgetCalculationService.calculateBudgetState(
                    settings = settingsStore.currentSettings,
                    now = today,
                    totalSpentThisCycleCents = 6_000L,
                    spentTodayCents = 2_000L,
                    cumulativeSavingsCents = 0L,
                    cycleBudgetAmountCents = 100_000L
                ),
                todayExpenses = emptyList(),
                activeCycleExpenseSections = emptyList(),
                spendingForecast = null
            ),
            pendingCycleCloseoutState = null,
            timelineLockState = TimelineLockState()
        )
        val state = useCase(flowOf(portfolioOverviewState)).first()
        assertEquals(2, state.monthlyHistory.size)
        assertEquals(
            expectedHistorySectionTitles(),
            state.historySections.map { it.title }
        )
        assertEquals(false, state.historySections.first().daySections.all { it.isEditable })
        assertEquals(1, state.historySections[2].daySections.size)
        assertEquals(1_500L, state.historySections[2].daySections.single().totalSpentCents)
        assertEquals(1, state.historySections[3].daySections.size)
        assertEquals(2_500L, state.historySections[3].daySections.single().totalSpentCents)
    }

    @Test
    fun invoke_includesDeletedBucketExpensesInCurrentCycleBucketSummaries() = runBlocking {
        val expenseDao = FakeExpenseDao(
            listOf(
                expenseEntityOn(1L, LocalDate.of(2026, 4, 10), 2_000L),
                expenseEntityOn(2L, LocalDate.of(2026, 4, 10), 5_000L).copy(bucketUuid = "deleted-bills-bucket")
            )
        )
        val settingsStore = FakeUserSettingsStore(
            UserSettings(
                monthlyBudgetCents = 100_000L,
                paydayDate = 25,
                primaryBucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                selectedBucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                lastResetTimestamp = LocalDate.of(2026, 3, 25)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            )
        )
        val budgetCalculationService = BudgetCalculationService()
        val useCase = ObserveHistoryUseCase(
            expenseDao = expenseDao,
            monthlyHistoryDao = FakeMonthlyHistoryDao(),
            budgetPolicyDao = FakeBudgetPolicyDao(),
            budgetBucketDao = FakeBudgetBucketDao(listOf(defaultSpendingBucketEntity())),
            bucketAllocationPolicyDao = FakeBucketAllocationPolicyDao(),
            bucketMonthlyHistoryDao = FakeBucketMonthlyHistoryDao(),
            userSettingsStore = settingsStore,
            cycleScheduleResolver = CycleScheduleResolver(budgetCalculationService)
        )
        val today = LocalDate.of(2026, 4, 10)
        val bucket = defaultSpendingBucket()
        val state = useCase(
            flowOf(
                PortfolioOverviewState(
                    effectiveCurrentDate = today,
                    portfolioState = PortfolioState(
                        portfolioTotalBudgetCents = 100_000L,
                        allocatedToBucketsCents = 100_000L,
                        unassignedPlannedBudgetCents = 0L,
                        totalSpentThisCycleCents = 7_000L,
                        remainingThisCycleCents = 93_000L,
                        completedCycleReserveCents = 0L,
                        netReserveCents = 93_000L,

                        cycleStartDate = LocalDate.of(2026, 3, 25),
                        cycleEndDateExclusive = LocalDate.of(2026, 4, 25)
                    ),
                    bucketSummaries = listOf(
                        BucketSummaryState(
                            bucket = bucket,
                            allocatedThisCycleCents = 100_000L,
                            spentThisCycleCents = 2_000L,
                            remainingThisCycleCents = 98_000L,
                            overspentCents = 0L,
                            earmarkedBalanceCents = 0L
                        )
                    ),
                    selectedBucketOverview = SelectedBucketOverview(
                        bucket = bucket,
                        summary = BucketSummaryState(
                            bucket = bucket,
                            allocatedThisCycleCents = 100_000L,
                            spentThisCycleCents = 2_000L,
                            remainingThisCycleCents = 98_000L,
                            overspentCents = 0L,
                            earmarkedBalanceCents = 0L
                        ),
                        budgetState = budgetCalculationService.calculateBudgetState(
                            settings = settingsStore.currentSettings,
                            now = today,
                            totalSpentThisCycleCents = 2_000L,
                            spentTodayCents = 2_000L,
                            cumulativeSavingsCents = 0L,
                            cycleBudgetAmountCents = 100_000L
                        ),
                        todayExpenses = emptyList(),
                        activeCycleExpenseSections = emptyList(),
                        spendingForecast = null
                    ),
                    pendingCycleCloseoutState = null,
                    timelineLockState = TimelineLockState()
                )
            )
        ).first()

        val currentCycleBucketSummaries = state.historySections.first().bucketSummaries
        assertEquals(2, currentCycleBucketSummaries.size)
        assertEquals("Deleted bucket", currentCycleBucketSummaries.first().bucketName)
        assertEquals(5_000L, currentCycleBucketSummaries.first().spentCents)
        assertEquals(5_000L, currentCycleBucketSummaries.first().overspentCents)
        assertEquals(DEFAULT_SPENDING_BUCKET_NAME, currentCycleBucketSummaries.last().bucketName)
    }
}

private fun defaultSpendingBucket(): BudgetBucket {
    return BudgetBucket(
        bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
        name = DEFAULT_SPENDING_BUCKET_NAME,
        trackingMode = BucketTrackingMode.DAILY_TARGET,
        balanceBehavior = BucketBalanceBehavior.RETURN_TO_PORTFOLIO,
        defaultAllocatedAmountCents = 100_000L,
        sortOrder = 0,
        isPrimary = true,
        originInstallId = "test-install-id",
        lastModifiedByInstallId = "test-install-id",
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
        modClock = "0000000000001-0000-test-install-id"
    )
}

private fun defaultSpendingBucketEntity() = defaultSpendingBucket().toEntity()

private fun expectedHistorySectionTitles(): List<String> {
    return listOf(
        "Current cycle",
        "Upcoming 2026-03-25 - 2026-04-24",
        "Feb 25 - Mar 24, 2026",
        "Jan 25 - Feb 24, 2026"
    )
}
