package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BudgetAdjustmentResolver
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class RebuildMonthlyHistoryUseCaseTest {

    @Test
    fun invoke_rebuildsFromApplicablePoliciesAndExpenses() = runBlocking {
        val budgetPolicyDao = FakeBudgetPolicyDao(
            listOf(
                budgetPolicyEntity(
                    id = 1L,
                    cycleStart = LocalDate.of(2026, 2, 25),
                    cycleEndExclusive = LocalDate.of(2026, 3, 25),
                    budgetAmountCents = 100_000L
                ),
                budgetPolicyEntity(
                    id = 2L,
                    cycleStart = LocalDate.of(2026, 3, 25),
                    cycleEndExclusive = LocalDate.of(2026, 4, 25),
                    budgetAmountCents = 120_000L
                ).copy(deletedAtEpochMs = 42L)
            )
        )
        val budgetAdjustmentDao = FakeBudgetAdjustmentDao()
        val expenseDao = FakeExpenseDao(
            listOf(
                expenseEntityOn(1L, LocalDate.of(2026, 2, 26), 10_000L),
                expenseEntityOn(2L, LocalDate.of(2026, 3, 10), 15_000L),
                expenseEntityOn(3L, LocalDate.of(2026, 3, 28), 20_000L)
            )
        )
        val monthlyHistoryDao = FakeMonthlyHistoryDao(
            listOf(
                historyEntity(
                    cycleStart = LocalDate.of(2025, 12, 25),
                    cycleEndExclusive = LocalDate.of(2026, 1, 25),
                    totalSpentCents = 5_000L
                )
            )
        )
        val useCase = RebuildMonthlyHistoryUseCase(
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            expenseDao = expenseDao,
            monthlyHistoryDao = monthlyHistoryDao,
            budgetCalculationService = BudgetCalculationService(),
            budgetAdjustmentResolver = BudgetAdjustmentResolver()
        )

        useCase(
            UserSettings(
                lastResetTimestamp = LocalDate.of(2026, 3, 25)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            )
        )

        assertEquals(1, monthlyHistoryDao.currentHistory.size)
        val rebuilt = monthlyHistoryDao.currentHistory.single()
        assertEquals("2026-02-25", rebuilt.cycleStartDate)
        assertEquals(25_000L, rebuilt.totalSpentCents)
        assertEquals(75_000L, rebuilt.surplusCents)
    }

    @Test
    fun invoke_keepsExistingHistoryWhenNoCompletedCyclesApply() = runBlocking {
        val monthlyHistoryDao = FakeMonthlyHistoryDao(
            listOf(
                historyEntity(
                    cycleStart = LocalDate.of(2026, 1, 25),
                    cycleEndExclusive = LocalDate.of(2026, 2, 25),
                    totalSpentCents = 10_000L
                )
            )
        )
        val useCase = RebuildMonthlyHistoryUseCase(
            budgetPolicyDao = FakeBudgetPolicyDao(),
            budgetAdjustmentDao = FakeBudgetAdjustmentDao(),
            expenseDao = FakeExpenseDao(),
            monthlyHistoryDao = monthlyHistoryDao,
            budgetCalculationService = BudgetCalculationService(),
            budgetAdjustmentResolver = BudgetAdjustmentResolver()
        )

        useCase(UserSettings(lastResetTimestamp = 0L))

        assertEquals(1, monthlyHistoryDao.currentHistory.size)
        assertEquals("2026-01-25", monthlyHistoryDao.currentHistory.single().cycleStartDate)
    }
}
