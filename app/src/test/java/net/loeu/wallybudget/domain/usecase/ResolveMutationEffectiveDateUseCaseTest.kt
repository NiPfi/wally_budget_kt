package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ResolveMutationEffectiveDateUseCaseTest {

    @Test
    fun invoke_returnsNullWhenRollbackWouldCreateFutureExpenseTimeline() = runBlocking {
        val settingsStore = FakeUserSettingsStore(
            UserSettings(
                paydayDate = 25,
                lastResetTimestamp = LocalDate.of(2026, 4, 25)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                lastSeenDate = "2026-04-25"
            )
        )
        val expenseDao = FakeExpenseDao(
            listOf(
                expenseEntityOn(1L, LocalDate.of(2026, 4, 25), 2_000L)
            )
        )
        val useCase = ResolveMutationEffectiveDateUseCase(
            userSettingsStore = settingsStore,
            expenseDao = expenseDao,
            budgetPolicyDao = FakeBudgetPolicyDao(),
            budgetCalculationService = BudgetCalculationService(),
            cycleScheduleResolver = CycleScheduleResolver(BudgetCalculationService())
        )

        val result = useCase(
            settings = settingsStore.currentSettings,
            observedDate = LocalDate.of(2026, 4, 20)
        )

        assertNull(result)
        assertEquals("2026-04-20", settingsStore.currentSettings.lastSeenDate)
    }

    @Test
    fun invoke_returnsEffectiveDateWhenTimelineRemainsUnlocked() = runBlocking {
        val settingsStore = FakeUserSettingsStore(
            UserSettings(
                paydayDate = 25,
                lastResetTimestamp = LocalDate.of(2026, 3, 25)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                lastSeenDate = "2026-04-25"
            )
        )
        val expenseDao = FakeExpenseDao(
            listOf(
                expenseEntityOn(1L, LocalDate.of(2026, 4, 24), 2_000L)
            )
        )
        val useCase = ResolveMutationEffectiveDateUseCase(
            userSettingsStore = settingsStore,
            expenseDao = expenseDao,
            budgetPolicyDao = FakeBudgetPolicyDao(),
            budgetCalculationService = BudgetCalculationService(),
            cycleScheduleResolver = CycleScheduleResolver(BudgetCalculationService())
        )

        val result = useCase(
            settings = settingsStore.currentSettings,
            observedDate = LocalDate.of(2026, 4, 25)
        )

        assertEquals(LocalDate.of(2026, 4, 25), result)
    }
}
