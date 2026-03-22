@file:Suppress("MaxLineLength")

package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.data.local.entity.BucketAllocationPolicyEntity
import net.loeu.wallybudget.data.local.entity.BucketMonthlyHistoryEntity
import net.loeu.wallybudget.data.local.entity.BudgetBucketEntity
import net.loeu.wallybudget.data.local.entity.BudgetPolicyEntity
import net.loeu.wallybudget.domain.model.BucketBalanceBehavior
import net.loeu.wallybudget.domain.model.BucketTrackingMode
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_NAME
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BucketAllocationResolver
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ObserveForecastUseCaseTest {

    @Test
    @Suppress("LongMethod")
    fun invoke_usesRecentExpensesAndHistoryToProduceForecast() = runBlocking {
        val expenseDao = FakeExpenseDao(
            listOf(
                expenseEntityOn(1L, LocalDate.of(2026, 3, 26), 4_000L),
                expenseEntityOn(2L, LocalDate.of(2026, 4, 1), 5_000L),
                expenseEntityOn(3L, LocalDate.of(2026, 4, 9), 6_000L),
                expenseEntityOn(4L, LocalDate.of(2026, 4, 10), 2_000L)
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
        val settingsStore = FakeUserSettingsStore(
            UserSettings(
                monthlyBudgetCents = 100_000L,
                paydayDate = 25,
                selectedBucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                lastResetTimestamp = LocalDate.of(2026, 3, 25)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            )
        )
        val budgetBucketDao = FakeBudgetBucketDao(
            listOf(
                BudgetBucketEntity(
                    bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                    name = DEFAULT_SPENDING_BUCKET_NAME,
                    trackingMode = BucketTrackingMode.DAILY_TARGET,
                    balanceBehavior = BucketBalanceBehavior.RETURN_TO_PORTFOLIO,
                    defaultAllocatedAmountCents = 100_000L,
                    sortOrder = 0,
                    originInstallId = "test-install-id",
                    lastModifiedByInstallId = "test-install-id",
                    createdAtEpochMs = 1L,
                    updatedAtEpochMs = 1L,
                    modClock = "0000000000001-0000-test-install-id"
                )
            )
        )
        val bucketAllocationPolicyDao = FakeBucketAllocationPolicyDao(
            listOf(
                BucketAllocationPolicyEntity(
                    allocationUuid = "alloc-1",
                    bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                    cycleStartDate = "2026-03-25",
                    cycleEndDateExclusive = "2026-04-25",
                    allocatedAmountCents = 100_000L,
                    originInstallId = "test-install-id",
                    lastModifiedByInstallId = "test-install-id",
                    createdAtEpochMs = 1L,
                    updatedAtEpochMs = 1L,
                    modClock = "0000000000001-0000-test-install-id"
                )
            )
        )
        val bucketAllocationAdjustmentDao = FakeBucketAllocationAdjustmentDao()
        val budgetCalculationService = BudgetCalculationService()
        val useCase = ObserveForecastUseCase(
            budgetPolicyDao = FakeBudgetPolicyDao(),
            budgetBucketDao = budgetBucketDao,
            bucketAllocationPolicyDao = bucketAllocationPolicyDao,
            bucketAllocationAdjustmentDao = bucketAllocationAdjustmentDao,
            bucketMonthlyHistoryDao = bucketHistoryDao,
            expenseDao = expenseDao,
            userSettingsStore = settingsStore,
            currentDateProvider = FakeCurrentDateProvider(LocalDate.of(2026, 4, 10)),
            budgetCalculationService = budgetCalculationService,
            cycleScheduleResolver = CycleScheduleResolver(budgetCalculationService),
            bucketAllocationResolver = BucketAllocationResolver()
        )

        val forecast = requireNotNull(useCase().first())

        assertTrue(forecast.projectedTotalSpentCents > 0L)
        assertTrue(forecast.usedDataPoints > 0)
    }

    @Test
    fun invoke_matchesBucketPolicyToRewrittenPortfolioCycleAfterPaydayChange() = runBlocking {
        val settingsStore = FakeUserSettingsStore(
            UserSettings(
                monthlyBudgetCents = 100_000L,
                portfolioMonthlyBudgetCents = 100_000L,
                paydayDate = 20,
                selectedBucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                lastResetTimestamp = LocalDate.of(2026, 3, 25)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            )
        )
        val budgetBucketDao = FakeBudgetBucketDao(
            listOf(
                defaultBucketEntity()
            )
        )
        val bucketAllocationPolicyDao = FakeBucketAllocationPolicyDao(
            listOf(
                bucketPolicyEntity(id = 1L, allocationUuid = "stale-policy", cycleStartDate = "2026-03-25", cycleEndDateExclusive = "2026-04-25", allocatedAmountCents = 100_000L),
                bucketPolicyEntity(id = 2L, allocationUuid = "rewritten-policy", cycleStartDate = "2026-03-25", cycleEndDateExclusive = "2026-04-20", allocatedAmountCents = 80_000L, createdAtEpochMs = 2L)
            )
        )
        val budgetCalculationService = BudgetCalculationService()
        val useCase = ObserveForecastUseCase(
            budgetPolicyDao = FakeBudgetPolicyDao(
                listOf(
                    budgetPolicyEntity(
                        cycleStartDate = "2026-03-25",
                        cycleEndDateExclusive = "2026-04-20",
                        budgetAmountCents = 100_000L
                    )
                )
            ),
            budgetBucketDao = budgetBucketDao,
            bucketAllocationPolicyDao = bucketAllocationPolicyDao,
            bucketAllocationAdjustmentDao = FakeBucketAllocationAdjustmentDao(),
            bucketMonthlyHistoryDao = FakeBucketMonthlyHistoryDao(),
            expenseDao = FakeExpenseDao(),
            userSettingsStore = settingsStore,
            currentDateProvider = FakeCurrentDateProvider(LocalDate.of(2026, 4, 10)),
            budgetCalculationService = budgetCalculationService,
            cycleScheduleResolver = CycleScheduleResolver(budgetCalculationService),
            bucketAllocationResolver = BucketAllocationResolver()
        )

        val forecast = requireNotNull(useCase().first())

        assertEquals(69_353L, forecast.estimatedEndCycleRemainingCents)
    }

    @Test
    @Suppress("LongMethod")
    fun invoke_usesResolvedOpenBucketForHistoryAndAdjustmentsWhenSelectionIsStale() = runBlocking {
        val settingsStore = FakeUserSettingsStore(
            UserSettings(
                monthlyBudgetCents = 100_000L,
                portfolioMonthlyBudgetCents = 100_000L,
                paydayDate = 25,
                selectedBucketUuid = "closed-bucket"
            )
        )
        val budgetBucketDao = FakeBudgetBucketDao(
            listOf(
                defaultBucketEntity()
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
                ),
                BucketMonthlyHistoryEntity(
                    bucketUuid = "closed-bucket",
                    cycleStartDate = "2026-02-25",
                    budgetAmountCents = 100_000L,
                    totalSpentCents = 100_000L,
                    surplusCents = 0L,
                    cycleEndDate = "2026-03-25",
                    endTimestamp = LocalDate.of(2026, 3, 25)
                        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                )
            )
        )
        val bucketAllocationPolicyDao = FakeBucketAllocationPolicyDao(
            listOf(
                bucketPolicyEntity(id = 1L, allocationUuid = "default-policy", cycleStartDate = "2026-03-25", cycleEndDateExclusive = "2026-04-25", allocatedAmountCents = 100_000L)
            )
        )
        val bucketAllocationAdjustmentDao = FakeBucketAllocationAdjustmentDao()
        bucketAllocationAdjustmentDao.insert(
            net.loeu.wallybudget.data.local.entity.BucketAllocationAdjustmentEntity(
                id = 1L,
                adjustmentUuid = "default-adjustment",
                bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                cycleStartDate = "2026-03-25",
                effectiveDate = "2026-04-10",
                previousAllocatedAmountCents = 100_000L,
                newAllocatedAmountCents = 80_000L,
                originInstallId = "test-install-id",
                lastModifiedByInstallId = "test-install-id",
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L,
                modClock = "0000000000001-0000-test-install-id"
            )
        )
        bucketAllocationAdjustmentDao.insert(
            net.loeu.wallybudget.data.local.entity.BucketAllocationAdjustmentEntity(
                id = 2L,
                adjustmentUuid = "closed-adjustment",
                bucketUuid = "closed-bucket",
                cycleStartDate = "2026-03-25",
                effectiveDate = "2026-04-10",
                previousAllocatedAmountCents = 100_000L,
                newAllocatedAmountCents = 10_000L,
                originInstallId = "test-install-id",
                lastModifiedByInstallId = "test-install-id",
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L,
                modClock = "0000000000001-0000-test-install-id"
            )
        )
        val budgetPolicyDao = FakeBudgetPolicyDao(
            listOf(
                budgetPolicyEntity(
                    cycleStartDate = "2026-03-25",
                    cycleEndDateExclusive = "2026-04-25",
                    budgetAmountCents = 100_000L
                )
            )
        )
        val useCase = ObserveForecastUseCase(
            budgetPolicyDao = budgetPolicyDao,
            budgetBucketDao = budgetBucketDao,
            bucketAllocationPolicyDao = bucketAllocationPolicyDao,
            bucketAllocationAdjustmentDao = bucketAllocationAdjustmentDao,
            bucketMonthlyHistoryDao = bucketHistoryDao,
            expenseDao = FakeExpenseDao(),
            userSettingsStore = settingsStore,
            currentDateProvider = FakeCurrentDateProvider(LocalDate.of(2026, 4, 10)),
            budgetCalculationService = BudgetCalculationService(),
            cycleScheduleResolver = CycleScheduleResolver(BudgetCalculationService()),
            bucketAllocationResolver = BucketAllocationResolver()
        )
        val fallbackSelectedUseCase = ObserveForecastUseCase(
            budgetPolicyDao = budgetPolicyDao,
            budgetBucketDao = budgetBucketDao,
            bucketAllocationPolicyDao = bucketAllocationPolicyDao,
            bucketAllocationAdjustmentDao = bucketAllocationAdjustmentDao,
            bucketMonthlyHistoryDao = bucketHistoryDao,
            expenseDao = FakeExpenseDao(),
            userSettingsStore = FakeUserSettingsStore(
                settingsStore.currentSettings.copy(selectedBucketUuid = DEFAULT_SPENDING_BUCKET_UUID)
            ),
            currentDateProvider = FakeCurrentDateProvider(LocalDate.of(2026, 4, 10)),
            budgetCalculationService = BudgetCalculationService(),
            cycleScheduleResolver = CycleScheduleResolver(BudgetCalculationService()),
            bucketAllocationResolver = BucketAllocationResolver()
        )

        val forecast = requireNotNull(useCase().first())
        val expectedForecast = requireNotNull(fallbackSelectedUseCase().first())

        assertEquals(expectedForecast, forecast)
    }

    private fun defaultBucketEntity() = BudgetBucketEntity(
        bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
        name = DEFAULT_SPENDING_BUCKET_NAME,
        trackingMode = BucketTrackingMode.DAILY_TARGET,
        balanceBehavior = BucketBalanceBehavior.RETURN_TO_PORTFOLIO,
        defaultAllocatedAmountCents = 100_000L,
        sortOrder = 0,
        originInstallId = "test-install-id",
        lastModifiedByInstallId = "test-install-id",
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
        modClock = "0000000000001-0000-test-install-id"
    )

    private fun budgetPolicyEntity(
        cycleStartDate: String,
        cycleEndDateExclusive: String,
        budgetAmountCents: Long
    ) = BudgetPolicyEntity(
        id = 1L,
        policyUuid = "budget-$cycleStartDate",
        cycleStartDate = cycleStartDate,
        cycleEndDateExclusive = cycleEndDateExclusive,
        budgetAmountCents = budgetAmountCents,
        paydayDayOfMonth = 20,
        originInstallId = "test-install-id",
        lastModifiedByInstallId = "test-install-id",
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
        modClock = "0000000000001-0000-test-install-id"
    )
}
