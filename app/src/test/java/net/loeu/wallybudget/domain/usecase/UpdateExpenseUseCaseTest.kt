package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.data.local.entity.ExpenseEntity
import net.loeu.wallybudget.data.local.entity.toDomainModel
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate

class UpdateExpenseUseCaseTest {

    @Test
    fun invoke_updatesCurrentDayExpense() = runBlocking {
        val currentDate = LocalDate.of(2026, 3, 24)
        val existing = expenseEntity(
            id = 7L,
            recordUuid = "expense-1",
            amountCents = 1_200L,
            description = "Lunch",
            expenseDate = currentDate.toString(),
            modClock = "0000000000001-0000-test-install-id"
        )
        val expenseDao = FakeExpenseDao(listOf(existing))
        val useCase = UpdateExpenseUseCase(
            expenseDao = expenseDao,
            userSettingsStore = FakeUserSettingsStore(),
            currentDateProvider = FakeCurrentDateProvider(currentDate),
            hybridLogicalClockService = HybridLogicalClockService()
        )

        useCase(
            existing.toDomainModel().copy(
                amountCents = 1_800L,
                description = "Team lunch"
            )
        )

        val updated = requireNotNull(expenseDao.findByRecordUuid("expense-1"))
        assertEquals(1_800L, updated.amountCents)
        assertEquals("Team lunch", updated.description)
        assertEquals("test-install-id", updated.lastModifiedByInstallId)
        assertNotEquals(existing.modClock, updated.modClock)
    }

    @Test
    fun invoke_updatesPastDayExpense() = runBlocking {
        val currentDate = LocalDate.of(2026, 3, 24)
        val existing = expenseEntity(
            id = 8L,
            recordUuid = "expense-2",
            amountCents = 900L,
            description = "Coffee",
            expenseDate = currentDate.minusDays(1).toString(),
            modClock = "0000000000001-0000-test-install-id"
        )
        val expenseDao = FakeExpenseDao(listOf(existing))
        val useCase = UpdateExpenseUseCase(
            expenseDao = expenseDao,
            userSettingsStore = FakeUserSettingsStore(),
            currentDateProvider = FakeCurrentDateProvider(currentDate),
            hybridLogicalClockService = HybridLogicalClockService()
        )

        useCase(existing.toDomainModel().copy(description = "Iced coffee"))

        val updated = requireNotNull(expenseDao.findByRecordUuid("expense-2"))
        assertEquals("Iced coffee", updated.description)
        assertEquals("test-install-id", updated.lastModifiedByInstallId)
        assertNotEquals(existing.modClock, updated.modClock)
    }

    @Test
    fun invoke_rejectsFutureDayExpense() = runBlocking {
        val currentDate = LocalDate.of(2026, 3, 24)
        val existing = expenseEntity(
            id = 9L,
            recordUuid = "expense-3",
            amountCents = 3_200L,
            description = "Groceries",
            expenseDate = currentDate.plusDays(1).toString(),
            modClock = "0000000000001-0000-test-install-id"
        )
        val expenseDao = FakeExpenseDao(listOf(existing))
        val useCase = UpdateExpenseUseCase(
            expenseDao = expenseDao,
            userSettingsStore = FakeUserSettingsStore(),
            currentDateProvider = FakeCurrentDateProvider(currentDate),
            hybridLogicalClockService = HybridLogicalClockService()
        )

        val exception = try {
            useCase(existing.toDomainModel().copy(description = "Planned groceries"))
            throw AssertionError("Expected ExpenseEditNotAllowedException but none was thrown")
        } catch (error: ExpenseEditNotAllowedException) {
            error
        }

        assertEquals("Future-dated expenses cannot be edited.", exception.message)
        val unchanged = requireNotNull(expenseDao.findByRecordUuid("expense-3"))
        assertEquals(existing.description, unchanged.description)
        assertEquals(existing.modClock, unchanged.modClock)
    }

    @Test
    fun invoke_rejectsFutureDatedPersistedExpenseEvenIfEditedExpenseIsCurrentDay() = runBlocking {
        val currentDate = LocalDate.of(2026, 3, 24)
        val existing = expenseEntity(
            id = 10L,
            recordUuid = "expense-4",
            amountCents = 2_450L,
            description = "Train ticket",
            expenseDate = currentDate.plusDays(2).toString(),
            modClock = "0000000000001-0000-test-install-id"
        )
        val expenseDao = FakeExpenseDao(listOf(existing))
        val useCase = UpdateExpenseUseCase(
            expenseDao = expenseDao,
            userSettingsStore = FakeUserSettingsStore(),
            currentDateProvider = FakeCurrentDateProvider(currentDate),
            hybridLogicalClockService = HybridLogicalClockService()
        )

        val exception = try {
            useCase(
                existing.toDomainModel().copy(
                    description = "Updated train ticket"
                )
            )
            throw AssertionError("Expected ExpenseEditNotAllowedException but none was thrown")
        } catch (error: ExpenseEditNotAllowedException) {
            error
        }

        assertEquals("Future-dated expenses cannot be edited.", exception.message)
        val unchanged = requireNotNull(expenseDao.findByRecordUuid("expense-4"))
        assertEquals(existing.description, unchanged.description)
        assertEquals(existing.expenseDate, unchanged.expenseDate)
        assertEquals(existing.modClock, unchanged.modClock)
    }

    private fun expenseEntity(
        id: Long,
        recordUuid: String,
        amountCents: Long,
        description: String,
        expenseDate: String,
        modClock: String
    ) = ExpenseEntity(
        id = id,
        recordUuid = recordUuid,
        bucketUuid = "bucket-1",
        amountCents = amountCents,
        description = description,
        timestamp = 1_710_000_000_000L,
        expenseDate = expenseDate,
        originInstallId = "test-install-id",
        lastModifiedByInstallId = "test-install-id",
        createdAtEpochMs = 1_710_000_000_000L,
        updatedAtEpochMs = 1_710_000_000_000L,
        modClock = modClock
    )
}
