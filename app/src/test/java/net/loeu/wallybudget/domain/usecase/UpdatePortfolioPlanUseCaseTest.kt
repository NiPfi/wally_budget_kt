package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.data.local.entity.BucketAllocationPolicyEntity
import net.loeu.wallybudget.data.local.entity.BudgetBucketEntity
import net.loeu.wallybudget.data.local.entity.BudgetPolicyEntity
import net.loeu.wallybudget.domain.model.BucketBalanceBehavior
import net.loeu.wallybudget.domain.model.BucketTrackingMode
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_NAME
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class UpdatePortfolioPlanUseCaseTest {

    @Test
    @Suppress("LongMethod")
    fun invoke_budgetOnlySavePreservesFutureBucketPolicies() = runBlocking {
        val settingsStore = FakeUserSettingsStore(
            UserSettings(
                monthlyBudgetCents = 100_000L,
                portfolioMonthlyBudgetCents = 100_000L,
                paydayDate = 25,
                selectedBucketUuid = DEFAULT_SPENDING_BUCKET_UUID
            )
        )
        val budgetPolicyDao = FakeBudgetPolicyDao(
            listOf(
                BudgetPolicyEntity(
                    id = 1L,
                    policyUuid = "current-policy",
                    cycleStartDate = "2026-03-25",
                    cycleEndDateExclusive = "2026-04-25",
                    budgetAmountCents = 100_000L,
                    paydayDayOfMonth = 25,
                    originInstallId = "test-install-id",
                    lastModifiedByInstallId = "test-install-id",
                    createdAtEpochMs = 1L,
                    updatedAtEpochMs = 1L,
                    modClock = "0000000000001-0000-test-install-id"
                )
            )
        )
        val budgetBucketDao = FakeBudgetBucketDao(
            listOf(
                bucketEntity(DEFAULT_SPENDING_BUCKET_UUID, DEFAULT_SPENDING_BUCKET_NAME, 70_000L, 0),
                bucketEntity("travel", "Travel", 30_000L, 1)
            )
        )
        val bucketAllocationPolicyDao = FakeBucketAllocationPolicyDao(
            listOf(
                bucketPolicyEntity(
                    "default-current",
                    DEFAULT_SPENDING_BUCKET_UUID,
                    "2026-03-25",
                    "2026-04-25",
                    70_000L
                ),
                bucketPolicyEntity("travel-current", "travel", "2026-03-25", "2026-04-25", 30_000L),
                bucketPolicyEntity("travel-future", "travel", "2026-04-25", "2026-05-25", 50_000L)
            )
        )

        val useCase = UpdatePortfolioPlanUseCase(
            transactionRunner = FakeTransactionRunner(),
            userSettingsStore = settingsStore,
            budgetPolicyDao = budgetPolicyDao,
            budgetBucketDao = budgetBucketDao,
            bucketAllocationPolicyDao = bucketAllocationPolicyDao,
            bucketAllocationAdjustmentDao = FakeBucketAllocationAdjustmentDao(),
            currentDateProvider = FakeCurrentDateProvider(LocalDate.of(2026, 4, 10)),
            cycleScheduleResolver = CycleScheduleResolver(BudgetCalculationService()),
            hybridLogicalClockService = HybridLogicalClockService()
        )

        useCase(UpdatePortfolioPlanRequest(portfolioMonthlyBudgetCents = 120_000L))

        val futureTravelPolicy = bucketAllocationPolicyDao.getAllForSnapshot()
            .first { it.allocationUuid == "travel-future" }
        assertNull(futureTravelPolicy.deletedAtEpochMs)
        assertEquals(50_000L, futureTravelPolicy.allocatedAmountCents)
    }

    private fun bucketEntity(
        bucketUuid: String,
        name: String,
        defaultAllocatedAmountCents: Long,
        sortOrder: Int
    ) = BudgetBucketEntity(
        id = if (bucketUuid == DEFAULT_SPENDING_BUCKET_UUID) 1L else 2L,
        bucketUuid = bucketUuid,
        name = name,
        trackingMode = BucketTrackingMode.DAILY_TARGET,
        balanceBehavior = if (bucketUuid == DEFAULT_SPENDING_BUCKET_UUID) {
            BucketBalanceBehavior.RETURN_TO_PORTFOLIO
        } else {
            BucketBalanceBehavior.RETAIN_IN_BUCKET
        },
        defaultAllocatedAmountCents = defaultAllocatedAmountCents,
        sortOrder = sortOrder,
        originInstallId = "test-install-id",
        lastModifiedByInstallId = "test-install-id",
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
        modClock = "0000000000001-0000-test-install-id"
    )

    private fun bucketPolicyEntity(
        allocationUuid: String,
        bucketUuid: String,
        cycleStartDate: String,
        cycleEndDateExclusive: String,
        allocatedAmountCents: Long
    ) = BucketAllocationPolicyEntity(
        id = when (allocationUuid) {
            "default-current" -> 1L
            "travel-current" -> 2L
            else -> 3L
        },
        allocationUuid = allocationUuid,
        bucketUuid = bucketUuid,
        cycleStartDate = cycleStartDate,
        cycleEndDateExclusive = cycleEndDateExclusive,
        allocatedAmountCents = allocatedAmountCents,
        originInstallId = "test-install-id",
        lastModifiedByInstallId = "test-install-id",
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
        modClock = "0000000000001-0000-test-install-id"
    )
}
