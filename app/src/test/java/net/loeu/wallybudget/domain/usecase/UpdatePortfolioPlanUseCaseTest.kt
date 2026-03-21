package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.data.local.entity.BucketAllocationPolicyEntity
import net.loeu.wallybudget.data.local.entity.BudgetBucketEntity
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
                bucketPolicyEntity(
                    "default-current",
                    DEFAULT_SPENDING_BUCKET_UUID,
                    "2026-03-25",
                    "2026-04-25",
                    70_000L
                ),
                bucketPolicyEntity("travel-current", "travel", "2026-03-25", "2026-04-25", 30_000L),
                bucketPolicyEntity(
                    "default-future",
                    DEFAULT_SPENDING_BUCKET_UUID,
                    "2026-04-25",
                    "2026-05-25",
                    70_000L
                ),
                bucketPolicyEntity("travel-future", "travel", "2026-04-25", "2026-05-25", 30_000L)
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
                listOf(bucketEntity(DEFAULT_SPENDING_BUCKET_UUID, DEFAULT_SPENDING_BUCKET_NAME, 100_000L, 0))
            ),
            bucketAllocationPolicyDao = FakeBucketAllocationPolicyDao(
                listOf(
                    bucketPolicyEntity(
                        "default-current",
                        DEFAULT_SPENDING_BUCKET_UUID,
                        "2026-03-25",
                        "2026-04-25",
                        100_000L
                    )
                )
            ),
            bucketAllocationAdjustmentDao = FakeBucketAllocationAdjustmentDao(),
            currentDateProvider = FakeCurrentDateProvider(LocalDate.of(2026, 4, 10)),
            cycleScheduleResolver = CycleScheduleResolver(BudgetCalculationService()),
            hybridLogicalClockService = HybridLogicalClockService()
        )

        useCase(UpdatePortfolioPlanRequest(portfolioMonthlyBudgetCents = 120_000L))

        assertNull(settingsStore.pendingPaydayUndo.first())
    }

    @Test
    @Suppress("LongMethod")
    fun invoke_closingBucketReallocatesFutureDefaultPolicy() = runBlocking {
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
                bucketPolicyEntity(
                    "default-current",
                    DEFAULT_SPENDING_BUCKET_UUID,
                    "2026-03-25",
                    "2026-04-25",
                    70_000L
                ),
                bucketPolicyEntity("travel-current", "travel", "2026-03-25", "2026-04-25", 30_000L),
                bucketPolicyEntity(
                    "default-future",
                    DEFAULT_SPENDING_BUCKET_UUID,
                    "2026-04-25",
                    "2026-05-25",
                    70_000L
                ),
                bucketPolicyEntity("travel-future", "travel", "2026-04-25", "2026-05-25", 30_000L)
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
                    bucketEntity(DEFAULT_SPENDING_BUCKET_UUID, DEFAULT_SPENDING_BUCKET_NAME, 70_000L, 0),
                    bucketEntity("travel", "Travel", 30_000L, 1)
                )
            ),
            bucketAllocationPolicyDao = bucketAllocationPolicyDao,
            bucketAllocationAdjustmentDao = FakeBucketAllocationAdjustmentDao(),
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
        assertEquals(100_000L, futureDefaultPolicy.allocatedAmountCents)
        assertNull(futureDefaultPolicy.deletedAtEpochMs)
        assertEquals(30_000L, futureTravelPolicy.allocatedAmountCents)
        assertTrue(futureTravelPolicy.deletedAtEpochMs != null)
    }

    @Test
    @Suppress("LongMethod")
    fun invoke_bucketPlanSaveRewritesChangedFutureNamedBucketPolicies() = runBlocking {
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
                bucketPolicyEntity(
                    "default-current",
                    DEFAULT_SPENDING_BUCKET_UUID,
                    "2026-03-25",
                    "2026-04-25",
                    70_000L
                ),
                bucketPolicyEntity("travel-current", "travel", "2026-03-25", "2026-04-25", 30_000L),
                bucketPolicyEntity(
                    "default-future",
                    DEFAULT_SPENDING_BUCKET_UUID,
                    "2026-04-25",
                    "2026-05-25",
                    70_000L
                ),
                bucketPolicyEntity("travel-future", "travel", "2026-04-25", "2026-05-25", 30_000L)
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
                    bucketEntity(DEFAULT_SPENDING_BUCKET_UUID, DEFAULT_SPENDING_BUCKET_NAME, 70_000L, 0),
                    bucketEntity("travel", "Travel", 30_000L, 1)
                )
            ),
            bucketAllocationPolicyDao = bucketAllocationPolicyDao,
            bucketAllocationAdjustmentDao = FakeBucketAllocationAdjustmentDao(),
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
        assertEquals(60_000L, futureDefaultPolicy.allocatedAmountCents)
        assertEquals(40_000L, futureTravelPolicy.allocatedAmountCents)
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
            "default-future" -> 3L
            else -> 4L
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
