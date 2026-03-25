package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.data.local.entity.BudgetBucketEntity
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_NAME
import net.loeu.wallybudget.domain.model.BucketBalanceBehavior
import net.loeu.wallybudget.domain.model.BucketTrackingMode
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CompleteOnboardingUseCaseTest {

    @Test
    fun invoke_persistsSettings_andArchivesPreviousCycleWhenProvided() = runBlocking {
        val transactionRunner = FakeTransactionRunner()
        val budgetBucketDao = FakeBudgetBucketDao()
        val bucketCycleBaselineDao = FakeBucketCycleBaselineDao()
        val bucketMonthlyHistoryDao = FakeBucketMonthlyHistoryDao()
        val budgetPolicyDao = FakeBudgetPolicyDao()
        val historyDao = FakeMonthlyHistoryDao()
        val settingsStore = FakeUserSettingsStore()
        val currentDateProvider = FakeCurrentDateProvider(LocalDate.of(2026, 4, 10))
        val useCase = CompleteOnboardingUseCase(
            transactionRunner = transactionRunner,
            budgetBucketDao = budgetBucketDao,
            bucketCycleBaselineDao = bucketCycleBaselineDao,
            bucketMonthlyHistoryDao = bucketMonthlyHistoryDao,
            budgetPolicyDao = budgetPolicyDao,
            monthlyHistoryDao = historyDao,
            userSettingsStore = settingsStore,
            currentDateProvider = currentDateProvider,
            budgetCalculationService = BudgetCalculationService(),
            hybridLogicalClockService = HybridLogicalClockService()
        )

        useCase(
            monthlyBudgetCents = 100_000L,
            paydayDate = 25,
            cycleStartDate = LocalDate.of(2026, 3, 25),
            previousExpensesCents = 12_000L
        )

        assertEquals(2, transactionRunner.transactionCount)
        assertEquals(1, historyDao.currentHistory.size)
        assertEquals(2, budgetPolicyDao.currentPolicies.size)
        assertEquals(1, bucketMonthlyHistoryDao.getAll().size)
        assertEquals(100_000L, settingsStore.currentSettings.monthlyBudgetCents)
        assertEquals(100_000L, settingsStore.currentSettings.portfolioMonthlyBudgetCents)
        assertEquals(25, settingsStore.currentSettings.paydayDate)
        assertEquals(DEFAULT_SPENDING_BUCKET_UUID, settingsStore.currentSettings.selectedBucketUuid)
        assertEquals(1, budgetBucketDao.countAll())
        assertEquals(2, bucketCycleBaselineDao.countAll())
        assertTrue(settingsStore.currentSettings.isOnboardingCompleted)
        assertTrue(settingsStore.completedOnboarding)
    }

    @Test
    @Suppress("LongMethod")
    fun invoke_updatesPreseededDefaultBucketAndCurrentPolicy() = runBlocking {
        val budgetBucketDao = FakeBudgetBucketDao(
            listOf(
                BudgetBucketEntity(
                    id = 1L,
                    bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                    name = DEFAULT_SPENDING_BUCKET_NAME,
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
            )
        )
        val bucketCycleBaselineDao = FakeBucketCycleBaselineDao(
            listOf(
                bucketCycleBaselineEntity(
                    baselineUuid = "seeded-default-baseline",
                    cycleStartDate = "2026-03-25",
                    cycleEndDateExclusive = "2026-04-25",
                    baselineAmountCents = 0L
                )
            )
        )
        val budgetPolicyDao = FakeBudgetPolicyDao()
        val useCase = CompleteOnboardingUseCase(
            transactionRunner = FakeTransactionRunner(),
            budgetBucketDao = budgetBucketDao,
            bucketCycleBaselineDao = bucketCycleBaselineDao,
            bucketMonthlyHistoryDao = FakeBucketMonthlyHistoryDao(),
            budgetPolicyDao = budgetPolicyDao,
            monthlyHistoryDao = FakeMonthlyHistoryDao(),
            userSettingsStore = FakeUserSettingsStore(),
            currentDateProvider = FakeCurrentDateProvider(LocalDate.of(2026, 4, 10)),
            budgetCalculationService = BudgetCalculationService(),
            hybridLogicalClockService = HybridLogicalClockService()
        )

        useCase(
            monthlyBudgetCents = 100_000L,
            paydayDate = 25,
            cycleStartDate = LocalDate.of(2026, 3, 25),
            previousExpensesCents = 0L
        )

        val updatedBucket = budgetBucketDao.findByBucketUuid(DEFAULT_SPENDING_BUCKET_UUID)
        val updatedBaseline = bucketCycleBaselineDao.findActiveBaselineForCycle(
            bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
            cycleStartDate = "2026-03-25"
        )
        assertEquals(100_000L, updatedBucket?.defaultAllocatedAmountCents)
        assertEquals(100_000L, updatedBaseline?.baselineAmountCents)
        assertEquals("2026-04-25", updatedBaseline?.cycleEndDateExclusive)
        assertEquals(1, budgetBucketDao.countAll())
        assertEquals(1, bucketCycleBaselineDao.countAll())
    }
}
