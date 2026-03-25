package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.domain.model.PendingPaydayUndo
import net.loeu.wallybudget.domain.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class UndoPaydayChangeUseCaseTest {

    @Test
    fun invoke_returnsNoOpWhenNoPendingUndoExists() = runBlocking {
        val useCase = UndoPaydayChangeUseCase(
            transactionRunner = FakeTransactionRunner(),
            userSettingsStore = FakeUserSettingsStore(),
            budgetPolicyDao = FakeBudgetPolicyDao(),
            budgetAdjustmentDao = FakeBudgetAdjustmentDao(),
            bucketAllocationPolicyDao = FakeBucketAllocationPolicyDao(),
            bucketAllocationAdjustmentDao = FakeBucketAllocationAdjustmentDao(),
            currentDateProvider = FakeCurrentDateProvider(LocalDate.of(2026, 4, 10))
        )

        val result = useCase()

        assertEquals("No payday change to undo.", result.summaryMessage)
    }

    @Test
    fun invoke_expiresPendingUndoAfterExpiryDate() = runBlocking {
        val settingsStore = FakeUserSettingsStore(UserSettings())
        settingsStore.savePendingPaydayUndo(
            PendingPaydayUndo(
                previousSettings = settingsStore.currentSettings,
                policiesToRestore = emptyList(),
                policiesToDeactivate = emptyList(),
                adjustmentsToRestore = emptyList(),
                adjustmentsToDeactivate = emptyList(),
                bucketPoliciesToRestore = emptyList(),
                bucketPoliciesToDeactivate = emptyList(),
                bucketAdjustmentsToRestore = emptyList(),
                bucketAdjustmentsToDeactivate = emptyList(),
                expiresAtExclusive = "2026-04-10"
            )
        )
        val useCase = UndoPaydayChangeUseCase(
            transactionRunner = FakeTransactionRunner(),
            userSettingsStore = settingsStore,
            budgetPolicyDao = FakeBudgetPolicyDao(),
            budgetAdjustmentDao = FakeBudgetAdjustmentDao(),
            bucketAllocationPolicyDao = FakeBucketAllocationPolicyDao(),
            bucketAllocationAdjustmentDao = FakeBucketAllocationAdjustmentDao(),
            currentDateProvider = FakeCurrentDateProvider(LocalDate.of(2026, 4, 10))
        )

        val result = useCase()

        assertEquals("Payday change undo expired.", result.summaryMessage)
        assertNull(settingsStore.pendingPaydayUndo.first())
    }
}
