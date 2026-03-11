package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class EnsureBudgetPolicyHistoryUseCaseTest {

    @Test
    fun invoke_backfillsPoliciesFromHistoryAndAddsCurrentCycleWhenMissing() = runBlocking {
        val budgetPolicyDao = FakeBudgetPolicyDao()
        val monthlyHistoryDao = FakeMonthlyHistoryDao(
            listOf(
                historyEntity(
                    cycleStart = LocalDate.of(2026, 2, 25),
                    cycleEndExclusive = LocalDate.of(2026, 3, 25),
                    totalSpentCents = 40_000L,
                    budgetAmountCents = 100_000L
                )
            )
        )
        val settingsStore = FakeUserSettingsStore(
            UserSettings(
                monthlyBudgetCents = 120_000L,
                paydayDate = 25,
                isOnboardingCompleted = true,
                lastResetTimestamp = LocalDate.of(2026, 3, 25)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            )
        )
        val useCase = EnsureBudgetPolicyHistoryUseCase(
            budgetPolicyDao = budgetPolicyDao,
            monthlyHistoryDao = monthlyHistoryDao,
            userSettingsStore = settingsStore,
            budgetCalculationService = BudgetCalculationService(),
            hybridLogicalClockService = HybridLogicalClockService()
        )

        useCase(LocalDate.of(2026, 4, 10))

        assertEquals(2, budgetPolicyDao.currentPolicies.size)
        assertEquals("2026-02-25", budgetPolicyDao.currentPolicies.first().cycleStartDate)
        assertEquals("2026-03-25", budgetPolicyDao.currentPolicies.last().cycleStartDate)
        assertEquals(120_000L, budgetPolicyDao.currentPolicies.last().budgetAmountCents)
    }

    @Test
    fun invoke_doesNotDuplicateExistingCurrentCyclePolicy() = runBlocking {
        val currentPolicy = budgetPolicyEntity(
            id = 1L,
            cycleStart = LocalDate.of(2026, 3, 25),
            cycleEndExclusive = LocalDate.of(2026, 4, 25),
            budgetAmountCents = 120_000L
        )
        val budgetPolicyDao = FakeBudgetPolicyDao(listOf(currentPolicy))
        val useCase = EnsureBudgetPolicyHistoryUseCase(
            budgetPolicyDao = budgetPolicyDao,
            monthlyHistoryDao = FakeMonthlyHistoryDao(),
            userSettingsStore = FakeUserSettingsStore(
                UserSettings(
                    monthlyBudgetCents = 120_000L,
                    paydayDate = 25,
                    isOnboardingCompleted = true,
                    lastResetTimestamp = LocalDate.of(2026, 3, 25)
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                )
            ),
            budgetCalculationService = BudgetCalculationService(),
            hybridLogicalClockService = HybridLogicalClockService()
        )

        useCase(LocalDate.of(2026, 4, 10))

        assertEquals(1, budgetPolicyDao.currentPolicies.size)
        assertEquals(currentPolicy.policyUuid, budgetPolicyDao.currentPolicies.single().policyUuid)
    }

    @Test
    fun invoke_doesNothingWhileOnboardingIsIncomplete() = runBlocking {
        val budgetPolicyDao = FakeBudgetPolicyDao()
        val useCase = EnsureBudgetPolicyHistoryUseCase(
            budgetPolicyDao = budgetPolicyDao,
            monthlyHistoryDao = FakeMonthlyHistoryDao(),
            userSettingsStore = FakeUserSettingsStore(
                UserSettings(
                    monthlyBudgetCents = 120_000L,
                    paydayDate = 25,
                    isOnboardingCompleted = false
                )
            ),
            budgetCalculationService = BudgetCalculationService(),
            hybridLogicalClockService = HybridLogicalClockService()
        )

        useCase(LocalDate.of(2026, 4, 10))

        assertTrue(budgetPolicyDao.currentPolicies.isEmpty())
    }
}
