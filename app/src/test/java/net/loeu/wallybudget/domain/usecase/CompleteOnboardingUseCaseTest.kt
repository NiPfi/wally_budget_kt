package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
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
        val bucketAllocationPolicyDao = FakeBucketAllocationPolicyDao()
        val budgetPolicyDao = FakeBudgetPolicyDao()
        val historyDao = FakeMonthlyHistoryDao()
        val settingsStore = FakeUserSettingsStore()
        val currentDateProvider = FakeCurrentDateProvider(LocalDate.of(2026, 4, 10))
        val useCase = CompleteOnboardingUseCase(
            transactionRunner = transactionRunner,
            budgetBucketDao = budgetBucketDao,
            bucketAllocationPolicyDao = bucketAllocationPolicyDao,
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
        assertEquals(100_000L, settingsStore.currentSettings.monthlyBudgetCents)
        assertEquals(100_000L, settingsStore.currentSettings.portfolioMonthlyBudgetCents)
        assertEquals(25, settingsStore.currentSettings.paydayDate)
        assertEquals(DEFAULT_SPENDING_BUCKET_UUID, settingsStore.currentSettings.selectedBucketUuid)
        assertEquals(1, budgetBucketDao.countAll())
        assertEquals(1, bucketAllocationPolicyDao.countAll())
        assertTrue(settingsStore.currentSettings.isOnboardingCompleted)
        assertTrue(settingsStore.completedOnboarding)
    }
}
