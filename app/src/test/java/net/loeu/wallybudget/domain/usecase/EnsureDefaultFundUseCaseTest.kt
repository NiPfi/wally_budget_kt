package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.domain.model.DEFAULT_FUND_UUID
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class EnsureDefaultFundUseCaseTest {

    @Test
    fun invoke_createsDefaultFundWhenMissing() = runBlocking {
        val fundDao = FakeFundDao()
        val useCase = EnsureDefaultFundUseCase(
            transactionRunner = FakeTransactionRunner(),
            userSettingsStore = FakeUserSettingsStore(),
            fundDao = fundDao,
            hybridLogicalClockService = HybridLogicalClockService()
        )

        useCase(LocalDate.of(2026, 3, 22))

        val fund = fundDao.findByUuid(DEFAULT_FUND_UUID)
        assertNotNull(fund)
        assertEquals("test-install-id", fund?.originInstallId)
        assertEquals("test-install-id", fund?.lastModifiedByInstallId)
    }

    @Test
    fun invoke_repairsInvalidDefaultFundMetadata() = runBlocking {
        val now = LocalDate.of(2026, 3, 22)
        val fundDao = FakeFundDao(
            listOf(
                fundEntity(
                    uuid = DEFAULT_FUND_UUID,
                    originInstallId = "",
                    lastModifiedByInstallId = "",
                    modClock = "",
                    closedAtEpochMs = 10L,
                    sortOrder = 5
                )
            )
        )
        val useCase = EnsureDefaultFundUseCase(
            transactionRunner = FakeTransactionRunner(),
            userSettingsStore = FakeUserSettingsStore(),
            fundDao = fundDao,
            hybridLogicalClockService = HybridLogicalClockService()
        )

        useCase(now)

        val repaired = fundDao.findByUuid(DEFAULT_FUND_UUID)
        assertEquals(0, repaired?.sortOrder)
        assertNull(repaired?.closedAtEpochMs)
        assertEquals("test-install-id", repaired?.originInstallId)
        assertEquals("test-install-id", repaired?.lastModifiedByInstallId)
        val expectedEpochMs = now.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expectedEpochMs, repaired?.updatedAtEpochMs)
        assertTrue(repaired?.modClock?.endsWith("-test-install-id") == true)
    }
}
