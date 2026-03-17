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
import org.junit.Assert.assertNotNull
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
        val useCase = updateBudgetSettingsUseCase(
            settingsStore = settingsStore,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            currentDate = LocalDate.of(2026, 4, 10)
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
    fun invoke_extendsCurrentCycleImmediatelyWhenLaterPaydayChanges() = runBlocking {
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
        val useCase = updateBudgetSettingsUseCase(
            settingsStore = settingsStore,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = FakeBudgetAdjustmentDao(),
            currentDate = LocalDate.of(2026, 4, 10)
        )

        val result = useCase(
            UpdateBudgetSettingsRequest(
                monthlyBudgetCents = 120_000L,
                paydayDate = 1,
                budgetChangeMode = BudgetChangeMode.APPLY_NEXT_CYCLE
            )
        )

        assertEquals(1, settingsStore.currentSettings.paydayDate)
        val activePolicies = budgetPolicyDao.currentPolicies
            .filter { it.deletedAtEpochMs == null }
            .sortedWith(compareBy({ it.cycleStartDate }, { it.cycleEndDateExclusive }))
        assertEquals(2, activePolicies.size)
        assertEquals("2026-03-25", activePolicies[0].cycleStartDate)
        assertEquals("2026-05-01", activePolicies[0].cycleEndDateExclusive)
        assertEquals(100_000L, activePolicies[0].budgetAmountCents)
        assertEquals(1, activePolicies[0].paydayDayOfMonth)
        assertEquals("2026-05-01", activePolicies[1].cycleStartDate)
        assertEquals("2026-06-01", activePolicies[1].cycleEndDateExclusive)
        assertEquals(120_000L, activePolicies[1].budgetAmountCents)
        assertTrue(result.summaryMessage.contains("Budget changes on 2026-05-01"))
        assertTrue(result.summaryMessage.contains("Payday switches from 25 to 1 now."))
        assertTrue(result.summaryMessage.contains("This cycle extends to 2026-05-01."))
    }

    @Test
    fun invoke_shortensCurrentCycleWhenEarlierPaydayIsStillAhead() = runBlocking {
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
        val useCase = updateBudgetSettingsUseCase(
            settingsStore = settingsStore,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = FakeBudgetAdjustmentDao(),
            currentDate = LocalDate.of(2026, 4, 10)
        )

        val result = useCase(
            UpdateBudgetSettingsRequest(
                monthlyBudgetCents = 120_000L,
                paydayDate = 20,
                budgetChangeMode = BudgetChangeMode.APPLY_NEXT_CYCLE
            )
        )

        assertEquals(20, settingsStore.currentSettings.paydayDate)
        val activePolicies = budgetPolicyDao.currentPolicies
            .filter { it.deletedAtEpochMs == null }
            .sortedWith(compareBy({ it.cycleStartDate }, { it.cycleEndDateExclusive }))
        assertEquals(2, activePolicies.size)
        assertEquals("2026-03-25", activePolicies[0].cycleStartDate)
        assertEquals("2026-04-20", activePolicies[0].cycleEndDateExclusive)
        assertEquals(100_000L, activePolicies[0].budgetAmountCents)
        assertEquals(20, activePolicies[0].paydayDayOfMonth)
        assertEquals("2026-04-20", activePolicies[1].cycleStartDate)
        assertEquals("2026-05-20", activePolicies[1].cycleEndDateExclusive)
        assertEquals(120_000L, activePolicies[1].budgetAmountCents)
        assertTrue(result.summaryMessage.contains("Budget changes on 2026-04-20"))
        assertTrue(result.summaryMessage.contains("Payday switches from 25 to 20 now."))
        assertTrue(result.summaryMessage.contains("This cycle now ends on 2026-04-20."))
        assertEquals("2026-04-20", settingsStore.pendingSettingsUndo.first()?.expiresAtExclusive)
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
        val useCase = updateBudgetSettingsUseCase(
            settingsStore = settingsStore,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            currentDate = LocalDate.of(2026, 4, 9)
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

    @Test
    fun invoke_replacementBudgetChangeLeavesSingleActiveAdjustmentFromBaseline() = runBlocking {
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
        val useCase = updateBudgetSettingsUseCase(
            settingsStore = settingsStore,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            currentDate = LocalDate.of(2026, 4, 10)
        )

        useCase(
            UpdateBudgetSettingsRequest(
                monthlyBudgetCents = 120_000L,
                paydayDate = 25,
                budgetChangeMode = BudgetChangeMode.PRORATE_CURRENT_CYCLE
            )
        )
        useCase(
            UpdateBudgetSettingsRequest(
                monthlyBudgetCents = 110_000L,
                paydayDate = 25,
                budgetChangeMode = BudgetChangeMode.PRORATE_CURRENT_CYCLE
            )
        )

        val activeAdjustments = budgetAdjustmentDao.getActiveForCycle("2026-03-25")
        assertEquals(1, activeAdjustments.size)
        assertEquals(100_000L, activeAdjustments.single().previousMonthlyBudgetCents)
        assertEquals(110_000L, activeAdjustments.single().newMonthlyBudgetCents)
        assertEquals("2026-04-10", activeAdjustments.single().effectiveDate)
    }

    @Test
    fun invoke_replacementChangeMatchingBaselineIsIgnoredAfterPendingUndoRestore() = runBlocking {
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
        val useCase = updateBudgetSettingsUseCase(
            settingsStore = settingsStore,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            currentDate = LocalDate.of(2026, 4, 10)
        )

        useCase(
            UpdateBudgetSettingsRequest(
                monthlyBudgetCents = 120_000L,
                paydayDate = 25,
                budgetChangeMode = BudgetChangeMode.PRORATE_CURRENT_CYCLE
            )
        )
        val result = useCase(
            UpdateBudgetSettingsRequest(
                monthlyBudgetCents = 100_000L,
                paydayDate = 25,
                budgetChangeMode = BudgetChangeMode.PRORATE_CURRENT_CYCLE
            )
        )

        assertEquals("No settings changed.", result.summaryMessage)
        assertEquals(100_000L, settingsStore.currentSettings.monthlyBudgetCents)
        assertEquals(0, budgetAdjustmentDao.getActiveForCycle("2026-03-25").size)
        assertEquals(null, settingsStore.pendingSettingsUndo.first())
    }

    @Test
    fun invoke_revertsPendingUndoBeforeApplyingReplacementChange() = runBlocking {
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
        val useCase = updateBudgetSettingsUseCase(
            settingsStore = settingsStore,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            currentDate = LocalDate.of(2026, 4, 10)
        )

        useCase(
            UpdateBudgetSettingsRequest(
                monthlyBudgetCents = 120_000L,
                paydayDate = 25,
                budgetChangeMode = BudgetChangeMode.PRORATE_CURRENT_CYCLE
            )
        )

        useCase(
            UpdateBudgetSettingsRequest(
                monthlyBudgetCents = 110_000L,
                paydayDate = 25,
                budgetChangeMode = BudgetChangeMode.PRORATE_CURRENT_CYCLE
            )
        )

        assertEquals(110_000L, settingsStore.currentSettings.monthlyBudgetCents)
        val activeAdjustments = budgetAdjustmentDao.getActiveForCycle("2026-03-25")
        assertEquals(1, activeAdjustments.size)
        assertEquals(100_000L, activeAdjustments.single().previousMonthlyBudgetCents)
        assertEquals(110_000L, activeAdjustments.single().newMonthlyBudgetCents)

        val pendingUndo = settingsStore.pendingSettingsUndo.first()
        assertNotNull(pendingUndo)
        assertEquals(100_000L, pendingUndo?.previousSettings?.monthlyBudgetCents)
        assertEquals(25, pendingUndo?.previousSettings?.paydayDate)
    }

    @Test
    fun invoke_replacementBudgetChangeTombstonesPriorAdjustmentWithFreshMetadata() = runBlocking {
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
        val useCase = updateBudgetSettingsUseCase(
            settingsStore = settingsStore,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            currentDate = LocalDate.of(2026, 4, 10)
        )

        useCase(
            UpdateBudgetSettingsRequest(
                monthlyBudgetCents = 120_000L,
                paydayDate = 25,
                budgetChangeMode = BudgetChangeMode.PRORATE_CURRENT_CYCLE
            )
        )
        val originalAdjustment = budgetAdjustmentDao.getAllForSnapshot().single()

        useCase(
            UpdateBudgetSettingsRequest(
                monthlyBudgetCents = 110_000L,
                paydayDate = 25,
                budgetChangeMode = BudgetChangeMode.PRORATE_CURRENT_CYCLE
            )
        )

        val tombstonedAdjustment = budgetAdjustmentDao.getAllForSnapshot()
            .first { it.adjustmentUuid == originalAdjustment.adjustmentUuid }
        assertNotNull(tombstonedAdjustment.deletedAtEpochMs)
        assertEquals(tombstonedAdjustment.deletedAtEpochMs, tombstonedAdjustment.updatedAtEpochMs)
        assertTrue(tombstonedAdjustment.updatedAtEpochMs > originalAdjustment.updatedAtEpochMs)
        assertEquals(settingsStore.currentSettings.installDeviceId, tombstonedAdjustment.lastModifiedByInstallId)
        assertTrue(tombstonedAdjustment.modClock != originalAdjustment.modClock)
    }

    @Test
    fun invoke_replacementPaydayChangeTombstonesPriorPoliciesWithFreshMetadata() = runBlocking {
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
        val useCase = updateBudgetSettingsUseCase(
            settingsStore = settingsStore,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = FakeBudgetAdjustmentDao(),
            currentDate = LocalDate.of(2026, 4, 10)
        )

        useCase(
            UpdateBudgetSettingsRequest(
                monthlyBudgetCents = 100_000L,
                paydayDate = 20,
                budgetChangeMode = BudgetChangeMode.PRORATE_CURRENT_CYCLE
            )
        )
        val firstRewritePolicy = budgetPolicyDao.currentPolicies
            .first { it.deletedAtEpochMs == null && it.cycleEndDateExclusive == "2026-04-20" }

        useCase(
            UpdateBudgetSettingsRequest(
                monthlyBudgetCents = 100_000L,
                paydayDate = 21,
                budgetChangeMode = BudgetChangeMode.PRORATE_CURRENT_CYCLE
            )
        )

        val tombstonedPolicy = budgetPolicyDao.currentPolicies
            .first { it.policyUuid == firstRewritePolicy.policyUuid }
        assertNotNull(tombstonedPolicy.deletedAtEpochMs)
        assertEquals(tombstonedPolicy.deletedAtEpochMs, tombstonedPolicy.updatedAtEpochMs)
        assertTrue(tombstonedPolicy.updatedAtEpochMs > firstRewritePolicy.updatedAtEpochMs)
        assertEquals(settingsStore.currentSettings.installDeviceId, tombstonedPolicy.lastModifiedByInstallId)
        assertTrue(tombstonedPolicy.modClock != firstRewritePolicy.modClock)
    }

    private fun updateBudgetSettingsUseCase(
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
            currentDateProvider = FakeCurrentDateProvider(currentDate),
            cycleScheduleResolver = cycleScheduleResolver,
            budgetAdjustmentResolver = BudgetAdjustmentResolver(),
            hybridLogicalClockService = HybridLogicalClockService()
        )
    }
}
