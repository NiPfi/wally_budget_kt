package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.domain.model.BudgetChangeMode
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BudgetAdjustmentResolver
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.BucketAllocationResolver
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class UndoPaydayChangeUseCaseTest {

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
        val saveUseCase = saveUseCase(
            settingsStore = settingsStore,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = FakeBudgetAdjustmentDao(),
            currentDate = LocalDate.of(2026, 4, 10)
        )

        saveUseCase(
            UpdateBudgetSettingsRequest(
                monthlyBudgetCents = 120_000L,
                paydayDate = 1,
                budgetChangeMode = BudgetChangeMode.APPLY_NEXT_CYCLE
            )
        )

        val undoUseCase = undoUseCase(
            settingsStore = settingsStore,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = FakeBudgetAdjustmentDao(),
            currentDate = LocalDate.of(2026, 4, 10)
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
        assertTrue(result.summaryMessage.contains("Restored the previous payday and cycle timing"))
        assertNull(settingsStore.pendingPaydayUndo.first())
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
        val saveUseCase = saveUseCase(
            settingsStore = settingsStore,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            currentDate = LocalDate.of(2026, 4, 10)
        )

        saveUseCase(
            UpdateBudgetSettingsRequest(
                monthlyBudgetCents = 120_000L,
                paydayDate = 1,
                budgetChangeMode = BudgetChangeMode.APPLY_NEXT_CYCLE
            )
        )

        val undoUseCase = undoUseCase(
            settingsStore = settingsStore,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            currentDate = LocalDate.of(2026, 4, 25)
        )

        val result = undoUseCase()

        assertEquals("Payday change undo expired.", result.summaryMessage)
        assertEquals(1, settingsStore.currentSettings.paydayDate)
        assertNull(settingsStore.pendingPaydayUndo.first())
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
        val saveUseCase = saveUseCase(
            settingsStore = settingsStore,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            currentDate = LocalDate.of(2026, 4, 10)
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

        val undoUseCase = undoUseCase(
            settingsStore = settingsStore,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            currentDate = LocalDate.of(2026, 4, 10)
        )

        val result = undoUseCase()

        assertTrue(result.summaryMessage.contains("Restored the previous payday and cycle timing"))
        assertEquals(100_000L, settingsStore.currentSettings.monthlyBudgetCents)
        val activeAdjustments = budgetAdjustmentDao.getActiveForCycle("2026-03-25")
        assertEquals(0, activeAdjustments.size)
    }
}

private fun saveUseCase(
    settingsStore: FakeUserSettingsStore,
    budgetPolicyDao: FakeBudgetPolicyDao,
    budgetAdjustmentDao: FakeBudgetAdjustmentDao,
    currentDate: LocalDate
): UpdateBudgetSettingsUseCase {
    return UpdateBudgetSettingsUseCase(
        transactionRunner = FakeTransactionRunner(),
        userSettingsStore = settingsStore,
        budgetPolicyDao = budgetPolicyDao,
        budgetAdjustmentDao = budgetAdjustmentDao,
        budgetBucketDao = FakeBudgetBucketDao(),
        bucketAllocationPolicyDao = FakeBucketAllocationPolicyDao(),
        bucketAllocationAdjustmentDao = FakeBucketAllocationAdjustmentDao(),
        currentDateProvider = FakeCurrentDateProvider(currentDate),
        cycleScheduleResolver = CycleScheduleResolver(BudgetCalculationService()),
        budgetAdjustmentResolver = BudgetAdjustmentResolver(),
        bucketAllocationResolver = BucketAllocationResolver(),
        hybridLogicalClockService = HybridLogicalClockService()
    )
}

private fun undoUseCase(
    settingsStore: FakeUserSettingsStore,
    budgetPolicyDao: FakeBudgetPolicyDao,
    budgetAdjustmentDao: FakeBudgetAdjustmentDao,
    currentDate: LocalDate
): UndoPaydayChangeUseCase {
    return UndoPaydayChangeUseCase(
        transactionRunner = FakeTransactionRunner(),
        userSettingsStore = settingsStore,
        budgetPolicyDao = budgetPolicyDao,
        budgetAdjustmentDao = budgetAdjustmentDao,
        bucketAllocationPolicyDao = FakeBucketAllocationPolicyDao(),
        bucketAllocationAdjustmentDao = FakeBucketAllocationAdjustmentDao(),
        currentDateProvider = FakeCurrentDateProvider(currentDate)
    )
}
