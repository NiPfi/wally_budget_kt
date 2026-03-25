package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class UpdatePaydayUseCaseTest {

    @Test
    fun invoke_updatesSettingAndReplacesNextCyclePolicyOnly() = runBlocking {
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
        val useCase = UpdatePaydayUseCase(
            transactionRunner = FakeTransactionRunner(),
            userSettingsStore = settingsStore,
            budgetPolicyDao = budgetPolicyDao,
            currentDateProvider = FakeCurrentDateProvider(LocalDate.of(2026, 4, 10)),
            cycleScheduleResolver = CycleScheduleResolver(BudgetCalculationService()),
            hybridLogicalClockService = HybridLogicalClockService()
        )

        val result = useCase(UpdatePaydayRequest(paydayDate = 20))

        assertEquals(20, settingsStore.currentSettings.paydayDate)
        assertTrue(result.summaryMessage.contains("2026-04-25"))
        val current = budgetPolicyDao.getAllForSnapshot().first { it.policyUuid == "policy-1" }
        val oldNext = budgetPolicyDao.getAllForSnapshot().first { it.policyUuid == "policy-2" }
        val newNext = budgetPolicyDao.getAllForSnapshot()
            .first { it.deletedAtEpochMs == null && it.policyUuid !in setOf("policy-1", "policy-2") }
        assertEquals("2026-03-25", current.cycleStartDate)
        assertTrue(oldNext.deletedAtEpochMs != null)
        assertEquals("2026-04-25", newNext.cycleStartDate)
        assertEquals(20, newNext.paydayDayOfMonth)
    }

    @Test
    fun invoke_returnsNoOpWhenPaydayIsUnchanged() = runBlocking {
        val useCase = UpdatePaydayUseCase(
            transactionRunner = FakeTransactionRunner(),
            userSettingsStore = FakeUserSettingsStore(UserSettings(paydayDate = 25)),
            budgetPolicyDao = FakeBudgetPolicyDao(),
            currentDateProvider = FakeCurrentDateProvider(LocalDate.of(2026, 4, 10)),
            cycleScheduleResolver = CycleScheduleResolver(BudgetCalculationService()),
            hybridLogicalClockService = HybridLogicalClockService()
        )

        val result = useCase(UpdatePaydayRequest(paydayDate = 25))

        assertEquals("No payday changes.", result.summaryMessage)
    }
}
