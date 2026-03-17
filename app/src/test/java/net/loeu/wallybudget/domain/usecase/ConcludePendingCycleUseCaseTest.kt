package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BudgetAdjustmentResolver
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ConcludePendingCycleUseCaseTest {

    @Test
    fun invoke_archivesPendingCycleAndClearsPreferences() = runBlocking {
        val transactionRunner = FakeTransactionRunner()
        val expenseDao = FakeExpenseDao(
            listOf(
                expenseEntityOn(1L, LocalDate.of(2026, 3, 28), 3_000L),
                expenseEntityOn(2L, LocalDate.of(2026, 4, 4), 4_000L)
            )
        )
        val budgetPolicyDao = FakeBudgetPolicyDao()
        val budgetAdjustmentDao = FakeBudgetAdjustmentDao()
        val historyDao = FakeMonthlyHistoryDao()
        val settingsStore = FakeUserSettingsStore()
        val budgetCalculationService = BudgetCalculationService()
        val useCase = ConcludePendingCycleUseCase(
            transactionRunner = transactionRunner,
            expenseDao = expenseDao,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            monthlyHistoryDao = historyDao,
            userSettingsStore = settingsStore,
            budgetCalculationService = budgetCalculationService,
            cycleScheduleResolver = CycleScheduleResolver(budgetCalculationService),
            budgetAdjustmentResolver = BudgetAdjustmentResolver()
        )
        val settings = UserSettings(
            monthlyBudgetCents = 100_000L,
            paydayDate = 25,
            pendingCycleStartDate = "2026-03-25",
            pendingCycleEndDateExclusive = "2026-04-25"
        )

        useCase(settings)

        assertEquals(1, transactionRunner.transactionCount)
        assertEquals(1, historyDao.currentHistory.size)
        assertNull(settingsStore.currentSettings.pendingCycleStartDate)
        assertEquals(1, settingsStore.clearPendingCount)
    }
}
