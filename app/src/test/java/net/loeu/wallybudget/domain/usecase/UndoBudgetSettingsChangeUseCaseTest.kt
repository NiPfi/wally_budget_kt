package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.domain.model.BudgetChangeMode
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BudgetAdjustmentResolver
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class UndoBudgetSettingsChangeUseCaseTest {

    private val cycleScheduleResolver = CycleScheduleResolver(BudgetCalculationService())

    @Test
    fun invoke_restoresOriginalCurrentCycleAndClearsPendingUndo() = runBlocking {
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
        val saveUseCase = UpdateBudgetSettingsUseCase(
            transactionRunner = FakeTransactionRunner(),
            userSettingsStore = settingsStore,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = FakeBudgetAdjustmentDao(),
            currentDateProvider = FakeCurrentDateProvider(LocalDate.of(2026, 4, 10)),
            cycleScheduleResolver = cycleScheduleResolver,
            budgetAdjustmentResolver = BudgetAdjustmentResolver(),
            hybridLogicalClockService = HybridLogicalClockService()
        )

        saveUseCase(
            UpdateBudgetSettingsRequest(
                monthlyBudgetCents = 120_000L,
                paydayDate = 1,
                budgetChangeMode = BudgetChangeMode.APPLY_NEXT_CYCLE
            )
        )

        val undoUseCase = UndoBudgetSettingsChangeUseCase(
            transactionRunner = FakeTransactionRunner(),
            userSettingsStore = settingsStore,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = FakeBudgetAdjustmentDao(),
            currentDateProvider = FakeCurrentDateProvider(LocalDate.of(2026, 4, 10))
        )

        val result = undoUseCase()

        assertEquals(100_000L, settingsStore.currentSettings.monthlyBudgetCents)
        assertEquals(25, settingsStore.currentSettings.paydayDate)
        val activePolicies = budgetPolicyDao.currentPolicies
            .filter { it.deletedAtEpochMs == null }
            .sortedBy { it.cycleStartDate }
        assertEquals(1, activePolicies.size)
        assertEquals("2026-03-25", activePolicies.single().cycleStartDate)
        assertEquals("2026-04-25", activePolicies.single().cycleEndDateExclusive)
        assertEquals(25, activePolicies.single().paydayDayOfMonth)
        assertTrue(result.summaryMessage.contains("Restored this cycle's default settings"))
        assertNull(settingsStore.pendingSettingsUndo.first())
    }

    @Test
    fun invoke_expiresUndoAtOriginalCurrentCycleEnd() = runBlocking {
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
        val saveUseCase = UpdateBudgetSettingsUseCase(
            transactionRunner = FakeTransactionRunner(),
            userSettingsStore = settingsStore,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            currentDateProvider = FakeCurrentDateProvider(LocalDate.of(2026, 4, 10)),
            cycleScheduleResolver = cycleScheduleResolver,
            budgetAdjustmentResolver = BudgetAdjustmentResolver(),
            hybridLogicalClockService = HybridLogicalClockService()
        )

        saveUseCase(
            UpdateBudgetSettingsRequest(
                monthlyBudgetCents = 120_000L,
                paydayDate = 1,
                budgetChangeMode = BudgetChangeMode.APPLY_NEXT_CYCLE
            )
        )

        val undoUseCase = UndoBudgetSettingsChangeUseCase(
            transactionRunner = FakeTransactionRunner(),
            userSettingsStore = settingsStore,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            currentDateProvider = FakeCurrentDateProvider(LocalDate.of(2026, 4, 25))
        )

        val result = undoUseCase()

        assertEquals("Cycle default restore expired.", result.summaryMessage)
        assertEquals(1, settingsStore.currentSettings.paydayDate)
        assertNull(settingsStore.pendingSettingsUndo.first())
    }

    @Test
    fun invoke_restoresOriginalBaselineAfterReplacementProratedSave() = runBlocking {
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
        val saveUseCase = UpdateBudgetSettingsUseCase(
            transactionRunner = FakeTransactionRunner(),
            userSettingsStore = settingsStore,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            currentDateProvider = FakeCurrentDateProvider(LocalDate.of(2026, 4, 10)),
            cycleScheduleResolver = cycleScheduleResolver,
            budgetAdjustmentResolver = BudgetAdjustmentResolver(),
            hybridLogicalClockService = HybridLogicalClockService()
        )

        saveUseCase(
            UpdateBudgetSettingsRequest(
                monthlyBudgetCents = 120_000L,
                paydayDate = 25,
                budgetChangeMode = BudgetChangeMode.PRORATE_CURRENT_CYCLE
            )
        )
        saveUseCase(
            UpdateBudgetSettingsRequest(
                monthlyBudgetCents = 110_000L,
                paydayDate = 25,
                budgetChangeMode = BudgetChangeMode.PRORATE_CURRENT_CYCLE
            )
        )

        val undoUseCase = UndoBudgetSettingsChangeUseCase(
            transactionRunner = FakeTransactionRunner(),
            userSettingsStore = settingsStore,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            currentDateProvider = FakeCurrentDateProvider(LocalDate.of(2026, 4, 10))
        )

        val result = undoUseCase()

        assertTrue(result.summaryMessage.contains("Restored this cycle's default settings"))
        assertEquals(100_000L, settingsStore.currentSettings.monthlyBudgetCents)
        val activeAdjustments = budgetAdjustmentDao.getActiveForCycle("2026-03-25")
        assertEquals(0, activeAdjustments.size)
    }
}
