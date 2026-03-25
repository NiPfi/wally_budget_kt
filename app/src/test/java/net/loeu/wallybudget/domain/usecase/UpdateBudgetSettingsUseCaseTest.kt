package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.domain.model.BudgetChangeMode
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BudgetAdjustmentResolver
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class UpdateBudgetSettingsUseCaseTest {

    @Test
    fun invoke_applyCurrentNowUpdatesCurrentPolicyWithoutCreatingAdjustment() = runBlocking {
        val settingsStore = FakeUserSettingsStore(
            UserSettings(
                monthlyBudgetCents = 100_000L,
                portfolioMonthlyBudgetCents = 100_000L,
                paydayDate = 25
            )
        )
        val budgetPolicyDao = FakeBudgetPolicyDao(
            listOf(budgetPolicyEntity(1L, LocalDate.of(2026, 3, 25), LocalDate.of(2026, 4, 25)))
        )
        val budgetAdjustmentDao = FakeBudgetAdjustmentDao(
            listOf(
                budgetAdjustmentEntity(
                    1L,
                    LocalDate.of(2026, 3, 25),
                    LocalDate.of(2026, 4, 1),
                    100_000L,
                    110_000L
                )
            )
        )
        val useCase = useCase(
            settingsStore = settingsStore,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao
        )

        val result = useCase(
            UpdateBudgetSettingsRequest(
                portfolioMonthlyBudgetCents = 120_000L,
                paydayDate = 25,
                budgetChangeMode = BudgetChangeMode.APPLY_CURRENT_NOW
            )
        )

        assertEquals("Portfolio budget applied from 2026-04-10.", result.summaryMessage)
        assertEquals(120_000L, settingsStore.currentSettings.resolvedPortfolioMonthlyBudgetCents)
        assertEquals(120_000L, budgetPolicyDao.findActivePolicyForCycle("2026-03-25")?.budgetAmountCents)
        assertTrue(budgetAdjustmentDao.getActiveForCycle("2026-03-25").isEmpty())
    }

    @Test
    fun invoke_applyNextCycleCreatesReplacementFuturePolicy() = runBlocking {
        val settingsStore = FakeUserSettingsStore(
            UserSettings(
                monthlyBudgetCents = 100_000L,
                portfolioMonthlyBudgetCents = 100_000L,
                paydayDate = 25
            )
        )
        val budgetPolicyDao = FakeBudgetPolicyDao(
            listOf(
                budgetPolicyEntity(1L, LocalDate.of(2026, 3, 25), LocalDate.of(2026, 4, 25)),
                budgetPolicyEntity(2L, LocalDate.of(2026, 4, 25), LocalDate.of(2026, 5, 25))
            )
        )
        val useCase = useCase(settingsStore = settingsStore, budgetPolicyDao = budgetPolicyDao)

        val result = useCase(
            UpdateBudgetSettingsRequest(
                portfolioMonthlyBudgetCents = 120_000L,
                paydayDate = 20,
                budgetChangeMode = BudgetChangeMode.APPLY_NEXT_CYCLE
            )
        )

        assertTrue(result.summaryMessage.contains("2026-04-25"))
        val nextPolicies = budgetPolicyDao.getAllForSnapshot().filter { it.cycleStartDate == "2026-04-25" }
        assertEquals(2, nextPolicies.size)
        assertTrue(nextPolicies.any { it.deletedAtEpochMs != null && it.policyUuid == "policy-2" })
        assertTrue(
            nextPolicies.any {
                it.deletedAtEpochMs == null &&
                    it.paydayDayOfMonth == 20 &&
                    it.budgetAmountCents == 120_000L
            }
        )
    }

    @Test
    fun invoke_returnsNoChangesWhenRequestMatchesState() = runBlocking {
        val useCase = useCase()

        val result = useCase(
            UpdateBudgetSettingsRequest(
                portfolioMonthlyBudgetCents = 100_000L,
                paydayDate = 25,
                budgetChangeMode = BudgetChangeMode.APPLY_NEXT_CYCLE
            )
        )

        assertEquals("No settings changed.", result.summaryMessage)
    }

    private fun useCase(
        settingsStore: FakeUserSettingsStore = FakeUserSettingsStore(
            UserSettings(
                monthlyBudgetCents = 100_000L,
                portfolioMonthlyBudgetCents = 100_000L,
                paydayDate = 25
            )
        ),
        budgetPolicyDao: FakeBudgetPolicyDao = FakeBudgetPolicyDao(
            listOf(budgetPolicyEntity(1L, LocalDate.of(2026, 3, 25), LocalDate.of(2026, 4, 25)))
        ),
        budgetAdjustmentDao: FakeBudgetAdjustmentDao = FakeBudgetAdjustmentDao()
    ): UpdateBudgetSettingsUseCase {
        val budgetCalculationService = BudgetCalculationService()
        return UpdateBudgetSettingsUseCase(
            transactionRunner = FakeTransactionRunner(),
            userSettingsStore = settingsStore,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            budgetBucketDao = FakeBudgetBucketDao(),
            bucketAllocationPolicyDao = FakeBucketAllocationPolicyDao(),
            bucketAllocationAdjustmentDao = FakeBucketAllocationAdjustmentDao(),
            bucketTransferDao = FakeBucketTransferDao(),
            expenseDao = FakeExpenseDao(),
            currentDateProvider = FakeCurrentDateProvider(LocalDate.of(2026, 4, 10)),
            cycleScheduleResolver = CycleScheduleResolver(budgetCalculationService),
            budgetAdjustmentResolver = BudgetAdjustmentResolver(),
            hybridLogicalClockService = HybridLogicalClockService()
        )
    }
}
