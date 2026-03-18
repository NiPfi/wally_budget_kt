package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.data.local.entity.BucketAllocationPolicyEntity
import net.loeu.wallybudget.data.local.entity.BucketMonthlyHistoryEntity
import net.loeu.wallybudget.data.local.entity.BudgetBucketEntity
import net.loeu.wallybudget.domain.model.BucketBalanceBehavior
import net.loeu.wallybudget.domain.model.BucketTrackingMode
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_NAME
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BucketAllocationResolver
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
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
                primaryBucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
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
                    isPrimary = true,
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
}
