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
import java.time.ZoneId

class UpdateBudgetSettingsUseCaseTest {

    private val budgetCalculationService = BudgetCalculationService()
    private val cycleScheduleResolver = CycleScheduleResolver(budgetCalculationService)

    @Test
    fun invoke_proratesCurrentCycleAndUpdatesFutureDefaults() = runBlocking {
        val settingsStore = FakeUserSettingsStore(
            UserSettings(
                monthlyBudgetCents = 100_000L,
                paydayDate = 25,
                lastResetTimestamp = LocalDate.of(2026, 3, 25)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            )
        )
        val budgetPolicyDao = FakeBudgetPolicyDao(
            listOf(
                budgetPolicyEntity(
                    id = 1L,
                    cycleStart = LocalDate.of(2026, 3, 25),
                    cycleEndExclusive = LocalDate.of(2026, 4, 25),
                    budgetAmountCents = 100_000L
                )
            )
        )
        val budgetAdjustmentDao = FakeBudgetAdjustmentDao()
        val useCase = UpdateBudgetSettingsUseCase(
            transactionRunner = FakeTransactionRunner(),
            userSettingsStore = settingsStore,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            currentDateProvider = FakeCurrentDateProvider(LocalDate.of(2026, 4, 10)),
            cycleScheduleResolver = cycleScheduleResolver,
            budgetAdjustmentResolver = BudgetAdjustmentResolver(),
            hybridLogicalClockService = HybridLogicalClockService()
        )

        val result = useCase(
            UpdateBudgetSettingsRequest(
                monthlyBudgetCents = 120_000L,
                paydayDate = 25,
                budgetChangeMode = BudgetChangeMode.PRORATE_CURRENT_CYCLE
            )
        )

        assertEquals(120_000L, settingsStore.currentSettings.monthlyBudgetCents)
        assertEquals(1, budgetAdjustmentDao.countAll())
        assertTrue(result.summaryMessage.contains("Budget prorated"))
    }

    @Test
    fun invoke_schedulesBridgeCycleWhenPaydayChanges() = runBlocking {
        val settingsStore = FakeUserSettingsStore(
            UserSettings(
                monthlyBudgetCents = 100_000L,
                paydayDate = 25,
                lastResetTimestamp = LocalDate.of(2026, 3, 25)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            )
        )
        val budgetPolicyDao = FakeBudgetPolicyDao(
            listOf(
                budgetPolicyEntity(
                    id = 1L,
                    cycleStart = LocalDate.of(2026, 3, 25),
                    cycleEndExclusive = LocalDate.of(2026, 4, 25),
                    budgetAmountCents = 100_000L
                )
            )
        )
        val useCase = UpdateBudgetSettingsUseCase(
            transactionRunner = FakeTransactionRunner(),
            userSettingsStore = settingsStore,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = FakeBudgetAdjustmentDao(),
            currentDateProvider = FakeCurrentDateProvider(LocalDate.of(2026, 4, 10)),
            cycleScheduleResolver = cycleScheduleResolver,
            budgetAdjustmentResolver = BudgetAdjustmentResolver(),
            hybridLogicalClockService = HybridLogicalClockService()
        )

        useCase(
            UpdateBudgetSettingsRequest(
                monthlyBudgetCents = 120_000L,
                paydayDate = 1,
                budgetChangeMode = BudgetChangeMode.APPLY_NEXT_CYCLE
            )
        )

        assertEquals(1, settingsStore.currentSettings.paydayDate)
        assertEquals(3, budgetPolicyDao.currentPolicies.size)
        assertEquals("2026-04-25", budgetPolicyDao.currentPolicies[1].cycleStartDate)
        assertEquals("2026-05-01", budgetPolicyDao.currentPolicies[1].cycleEndDateExclusive)
        assertEquals(23_226L, budgetPolicyDao.currentPolicies[1].budgetAmountCents)
        assertEquals("2026-05-01", budgetPolicyDao.currentPolicies[2].cycleStartDate)
    }

    @Test
    fun invoke_usesSyncedEffectiveDateWhenObservedClockMovesBackward() = runBlocking {
        val settingsStore = FakeUserSettingsStore(
            UserSettings(
                monthlyBudgetCents = 100_000L,
                paydayDate = 25,
                lastResetTimestamp = LocalDate.of(2026, 3, 25)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                lastSeenDate = "2026-04-10"
            )
        )
        val budgetPolicyDao = FakeBudgetPolicyDao(
            listOf(
                budgetPolicyEntity(
                    id = 1L,
                    cycleStart = LocalDate.of(2026, 3, 25),
                    cycleEndExclusive = LocalDate.of(2026, 4, 25),
                    budgetAmountCents = 100_000L
                )
            )
        )
        val budgetAdjustmentDao = FakeBudgetAdjustmentDao()
        val useCase = UpdateBudgetSettingsUseCase(
            transactionRunner = FakeTransactionRunner(),
            userSettingsStore = settingsStore,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            currentDateProvider = FakeCurrentDateProvider(LocalDate.of(2026, 4, 9)),
            cycleScheduleResolver = cycleScheduleResolver,
            budgetAdjustmentResolver = BudgetAdjustmentResolver(),
            hybridLogicalClockService = HybridLogicalClockService()
        )

        useCase(
            UpdateBudgetSettingsRequest(
                monthlyBudgetCents = 110_000L,
                paydayDate = 25,
                budgetChangeMode = BudgetChangeMode.PRORATE_CURRENT_CYCLE
            )
        )

        assertEquals("2026-04-10", budgetAdjustmentDao.getAllForSnapshot().single().effectiveDate)
    }
}
