@file:Suppress("MaxLineLength")

package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.data.local.entity.BudgetPolicyEntity
import net.loeu.wallybudget.domain.model.PendingPaydayUndo
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
import org.junit.Assert.assertTrue
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
                bucketEntity(defaultAllocatedAmountCents = 70_000L),
                bucketEntity(id = 2L, bucketUuid = "travel", name = "Travel", balanceBehavior = BucketBalanceBehavior.RETAIN_IN_BUCKET, defaultAllocatedAmountCents = 30_000L, sortOrder = 1)
            )
        )
        val bucketAllocationPolicyDao = FakeBucketAllocationPolicyDao(
            listOf(
                bucketPolicyEntity(allocationUuid = "default-current", cycleStartDate = "2026-03-25", cycleEndDateExclusive = "2026-04-25", allocatedAmountCents = 70_000L),
                bucketPolicyEntity(id = 2L, allocationUuid = "travel-current", bucketUuid = "travel", cycleStartDate = "2026-03-25", cycleEndDateExclusive = "2026-04-25", allocatedAmountCents = 30_000L),
                bucketPolicyEntity(id = 4L, allocationUuid = "travel-future", bucketUuid = "travel", cycleStartDate = "2026-04-25", cycleEndDateExclusive = "2026-05-25", allocatedAmountCents = 50_000L)
            )
        )

        val useCase = UpdatePortfolioPlanUseCase(
            transactionRunner = FakeTransactionRunner(),
            userSettingsStore = settingsStore,
            budgetPolicyDao = budgetPolicyDao,
            budgetBucketDao = budgetBucketDao,
            bucketAllocationPolicyDao = bucketAllocationPolicyDao,
            bucketAllocationAdjustmentDao = FakeBucketAllocationAdjustmentDao(),
            bucketTransferDao = FakeBucketTransferDao(),
            expenseDao = FakeExpenseDao(),
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

    @Test
    @Suppress("LongMethod")
    fun invoke_clearsPendingPaydayUndoAfterPlanSave() = runBlocking {
        val settingsStore = FakeUserSettingsStore(
            UserSettings(
                monthlyBudgetCents = 100_000L,
                portfolioMonthlyBudgetCents = 100_000L,
                paydayDate = 25,
                selectedBucketUuid = DEFAULT_SPENDING_BUCKET_UUID
            )
        )
        settingsStore.savePendingPaydayUndo(
            PendingPaydayUndo(
                previousSettings = settingsStore.currentSettings,
                policiesToRestore = emptyList(),
                policiesToDeactivate = emptyList(),
                adjustmentsToRestore = emptyList(),
                adjustmentsToDeactivate = emptyList(),
                bucketPoliciesToRestore = emptyList(),
                bucketPoliciesToDeactivate = emptyList(),
                bucketAdjustmentsToRestore = emptyList(),
                bucketAdjustmentsToDeactivate = emptyList(),
                expiresAtExclusive = "2026-04-20"
            )
        )

        val bucketAllocationPolicyDao = FakeBucketAllocationPolicyDao(
            listOf(
                bucketPolicyEntity(allocationUuid = "default-current", cycleStartDate = "2026-03-25", cycleEndDateExclusive = "2026-04-25", allocatedAmountCents = 70_000L),
                bucketPolicyEntity(id = 2L, allocationUuid = "travel-current", bucketUuid = "travel", cycleStartDate = "2026-03-25", cycleEndDateExclusive = "2026-04-25", allocatedAmountCents = 30_000L),
                bucketPolicyEntity(id = 3L, allocationUuid = "default-future", cycleStartDate = "2026-04-25", cycleEndDateExclusive = "2026-05-25", allocatedAmountCents = 70_000L),
                bucketPolicyEntity(id = 4L, allocationUuid = "travel-future", bucketUuid = "travel", cycleStartDate = "2026-04-25", cycleEndDateExclusive = "2026-05-25", allocatedAmountCents = 30_000L)
            )
        )
        val useCase = UpdatePortfolioPlanUseCase(
            transactionRunner = FakeTransactionRunner(),
            userSettingsStore = settingsStore,
            budgetPolicyDao = FakeBudgetPolicyDao(
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
            ),
            budgetBucketDao = FakeBudgetBucketDao(
                listOf(bucketEntity(defaultAllocatedAmountCents = 100_000L))
            ),
            bucketAllocationPolicyDao = FakeBucketAllocationPolicyDao(
                listOf(
                    bucketPolicyEntity(allocationUuid = "default-current", cycleStartDate = "2026-03-25", cycleEndDateExclusive = "2026-04-25", allocatedAmountCents = 100_000L)
                )
            ),
            bucketAllocationAdjustmentDao = FakeBucketAllocationAdjustmentDao(),
            bucketTransferDao = FakeBucketTransferDao(),
            expenseDao = FakeExpenseDao(),
            currentDateProvider = FakeCurrentDateProvider(LocalDate.of(2026, 4, 10)),
            cycleScheduleResolver = CycleScheduleResolver(BudgetCalculationService()),
            hybridLogicalClockService = HybridLogicalClockService()
        )

        useCase(UpdatePortfolioPlanRequest(portfolioMonthlyBudgetCents = 120_000L))

        assertNull(settingsStore.pendingPaydayUndo.first())
    }

    @Test
    @Suppress("LongMethod")
    fun invoke_closingBucketLeavesFuturePoliciesUntouchedUntilRollover() = runBlocking {
        val settingsStore = FakeUserSettingsStore(
            UserSettings(
                monthlyBudgetCents = 100_000L,
                portfolioMonthlyBudgetCents = 100_000L,
                paydayDate = 25,
                selectedBucketUuid = DEFAULT_SPENDING_BUCKET_UUID
            )
        )
        val bucketAllocationPolicyDao = FakeBucketAllocationPolicyDao(
            listOf(
                bucketPolicyEntity(allocationUuid = "default-current", cycleStartDate = "2026-03-25", cycleEndDateExclusive = "2026-04-25", allocatedAmountCents = 70_000L),
                bucketPolicyEntity(id = 2L, allocationUuid = "travel-current", bucketUuid = "travel", cycleStartDate = "2026-03-25", cycleEndDateExclusive = "2026-04-25", allocatedAmountCents = 30_000L),
                bucketPolicyEntity(id = 3L, allocationUuid = "default-future", cycleStartDate = "2026-04-25", cycleEndDateExclusive = "2026-05-25", allocatedAmountCents = 70_000L),
                bucketPolicyEntity(id = 4L, allocationUuid = "travel-future", bucketUuid = "travel", cycleStartDate = "2026-04-25", cycleEndDateExclusive = "2026-05-25", allocatedAmountCents = 30_000L)
            )
        )
        val useCase = UpdatePortfolioPlanUseCase(
            transactionRunner = FakeTransactionRunner(),
            userSettingsStore = settingsStore,
            budgetPolicyDao = FakeBudgetPolicyDao(
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
            ),
            budgetBucketDao = FakeBudgetBucketDao(
                listOf(
                    bucketEntity(defaultAllocatedAmountCents = 70_000L),
                    bucketEntity(id = 2L, bucketUuid = "travel", name = "Travel", balanceBehavior = BucketBalanceBehavior.RETAIN_IN_BUCKET, defaultAllocatedAmountCents = 30_000L, sortOrder = 1)
                )
            ),
            bucketAllocationPolicyDao = bucketAllocationPolicyDao,
            bucketAllocationAdjustmentDao = FakeBucketAllocationAdjustmentDao(),
            bucketTransferDao = FakeBucketTransferDao(),
            expenseDao = FakeExpenseDao(),
            currentDateProvider = FakeCurrentDateProvider(LocalDate.of(2026, 4, 10)),
            cycleScheduleResolver = CycleScheduleResolver(BudgetCalculationService()),
            hybridLogicalClockService = HybridLogicalClockService()
        )

        useCase(
            UpdatePortfolioPlanRequest(
                portfolioMonthlyBudgetCents = 100_000L,
                buckets = listOf(
                    BucketDraft(
                        bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                        name = DEFAULT_SPENDING_BUCKET_NAME,
                        trackingMode = BucketTrackingMode.DAILY_TARGET,
                        balanceBehavior = BucketBalanceBehavior.RETURN_TO_PORTFOLIO,
                        defaultAllocatedAmountCents = 70_000L,
                        sortOrder = 0
                    ),
                    BucketDraft(
                        bucketUuid = "travel",
                        name = "Travel",
                        trackingMode = BucketTrackingMode.DAILY_TARGET,
                        balanceBehavior = BucketBalanceBehavior.RETAIN_IN_BUCKET,
                        defaultAllocatedAmountCents = 30_000L,
                        sortOrder = 1,
                        closeRequested = true
                    )
                )
            )
        )

        val futureDefaultPolicy = bucketAllocationPolicyDao.getAllForSnapshot()
            .first { it.allocationUuid == "default-future" }
        val futureTravelPolicy = bucketAllocationPolicyDao.getAllForSnapshot()
            .first { it.allocationUuid == "travel-future" }
        assertEquals(70_000L, futureDefaultPolicy.allocatedAmountCents)
        assertNull(futureDefaultPolicy.deletedAtEpochMs)
        assertEquals(30_000L, futureTravelPolicy.allocatedAmountCents)
        assertTrue(futureTravelPolicy.deletedAtEpochMs != null)
    }

    @Test
    @Suppress("LongMethod")
    fun invoke_bucketPlanSaveDoesNotRewriteFutureNamedBucketPolicies() = runBlocking {
        val settingsStore = FakeUserSettingsStore(
            UserSettings(
                monthlyBudgetCents = 100_000L,
                portfolioMonthlyBudgetCents = 100_000L,
                paydayDate = 25,
                selectedBucketUuid = DEFAULT_SPENDING_BUCKET_UUID
            )
        )
        val bucketAllocationPolicyDao = FakeBucketAllocationPolicyDao(
            listOf(
                bucketPolicyEntity(allocationUuid = "default-current", cycleStartDate = "2026-03-25", cycleEndDateExclusive = "2026-04-25", allocatedAmountCents = 70_000L),
                bucketPolicyEntity(id = 2L, allocationUuid = "travel-current", bucketUuid = "travel", cycleStartDate = "2026-03-25", cycleEndDateExclusive = "2026-04-25", allocatedAmountCents = 30_000L),
                bucketPolicyEntity(id = 3L, allocationUuid = "default-future", cycleStartDate = "2026-04-25", cycleEndDateExclusive = "2026-05-25", allocatedAmountCents = 70_000L),
                bucketPolicyEntity(id = 4L, allocationUuid = "travel-future", bucketUuid = "travel", cycleStartDate = "2026-04-25", cycleEndDateExclusive = "2026-05-25", allocatedAmountCents = 30_000L)
            )
        )
        val useCase = UpdatePortfolioPlanUseCase(
            transactionRunner = FakeTransactionRunner(),
            userSettingsStore = settingsStore,
            budgetPolicyDao = FakeBudgetPolicyDao(
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
            ),
            budgetBucketDao = FakeBudgetBucketDao(
                listOf(
                    bucketEntity(defaultAllocatedAmountCents = 70_000L),
                    bucketEntity(id = 2L, bucketUuid = "travel", name = "Travel", balanceBehavior = BucketBalanceBehavior.RETAIN_IN_BUCKET, defaultAllocatedAmountCents = 30_000L, sortOrder = 1)
                )
            ),
            bucketAllocationPolicyDao = bucketAllocationPolicyDao,
            bucketAllocationAdjustmentDao = FakeBucketAllocationAdjustmentDao(),
            bucketTransferDao = FakeBucketTransferDao(),
            expenseDao = FakeExpenseDao(),
            currentDateProvider = FakeCurrentDateProvider(LocalDate.of(2026, 4, 10)),
            cycleScheduleResolver = CycleScheduleResolver(BudgetCalculationService()),
            hybridLogicalClockService = HybridLogicalClockService()
        )

        useCase(
            UpdatePortfolioPlanRequest(
                portfolioMonthlyBudgetCents = 100_000L,
                buckets = listOf(
                    BucketDraft(
                        bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                        name = DEFAULT_SPENDING_BUCKET_NAME,
                        trackingMode = BucketTrackingMode.DAILY_TARGET,
                        balanceBehavior = BucketBalanceBehavior.RETURN_TO_PORTFOLIO,
                        defaultAllocatedAmountCents = 60_000L,
                        sortOrder = 0
                    ),
                    BucketDraft(
                        bucketUuid = "travel",
                        name = "Travel",
                        trackingMode = BucketTrackingMode.DAILY_TARGET,
                        balanceBehavior = BucketBalanceBehavior.RETAIN_IN_BUCKET,
                        defaultAllocatedAmountCents = 40_000L,
                        sortOrder = 1
                    )
                )
            )
        )

        val futureDefaultPolicy = bucketAllocationPolicyDao.getAllForSnapshot()
            .first { it.allocationUuid == "default-future" }
        val futureTravelPolicy = bucketAllocationPolicyDao.getAllForSnapshot()
            .first { it.allocationUuid == "travel-future" }
        assertEquals(70_000L, futureDefaultPolicy.allocatedAmountCents)
        assertEquals(30_000L, futureTravelPolicy.allocatedAmountCents)
    }

}
