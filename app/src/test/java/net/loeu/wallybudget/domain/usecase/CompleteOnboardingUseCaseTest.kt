package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.runBlocking
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
        val budgetPolicyDao = FakeBudgetPolicyDao()
        val historyDao = FakeMonthlyHistoryDao()
        val settingsStore = FakeUserSettingsStore()
        val currentDateProvider = FakeCurrentDateProvider(LocalDate.of(2026, 4, 10))
        val useCase = CompleteOnboardingUseCase(
            transactionRunner = transactionRunner,
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
        assertEquals(25, settingsStore.currentSettings.paydayDate)
        assertTrue(settingsStore.currentSettings.isOnboardingCompleted)
        assertTrue(settingsStore.completedOnboarding)
    }
}
