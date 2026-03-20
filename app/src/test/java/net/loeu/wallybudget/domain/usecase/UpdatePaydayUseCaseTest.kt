package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.data.local.entity.BucketAllocationAdjustmentEntity
import net.loeu.wallybudget.data.local.entity.BucketAllocationPolicyEntity
import net.loeu.wallybudget.data.local.entity.BudgetAdjustmentEntity
import net.loeu.wallybudget.data.local.entity.BudgetPolicyEntity
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class UpdatePaydayUseCaseTest {

    @Test
    @Suppress("LongMethod")
    fun invoke_rewritesBucketPoliciesAndAdjustmentsToNewCycleBoundaries() = runBlocking {
        val settingsStore = FakeUserSettingsStore(
            UserSettings(
                monthlyBudgetCents = 100_000L,
                portfolioMonthlyBudgetCents = 100_000L,
                paydayDate = 25
            )
        )
        val budgetPolicyDao = FakeBudgetPolicyDao(
            listOf(
                budgetPolicyEntity("current-policy", "2026-03-25", "2026-04-25"),
                budgetPolicyEntity("future-policy", "2026-04-25", "2026-05-25")
            )
        )
        val bucketPolicyDao = FakeBucketAllocationPolicyDao(
            listOf(
                bucketPolicyEntity("bucket-current", "travel", "2026-03-25", "2026-04-25", 30_000L),
                bucketPolicyEntity("bucket-future", "travel", "2026-04-25", "2026-05-25", 40_000L)
            )
        )
        val bucketAdjustmentDao = FakeBucketAllocationAdjustmentDao(
            listOf(
                bucketAdjustmentEntity("bucket-adjust-current", "travel", "2026-03-25", "2026-04-15", 30_000L, 35_000L),
                bucketAdjustmentEntity("bucket-adjust-future", "travel", "2026-04-25", "2026-04-27", 40_000L, 45_000L)
            )
        )
        val budgetAdjustmentDao = FakeBudgetAdjustmentDao(
            listOf(
                budgetAdjustmentEntity("budget-adjust-current", "2026-03-25", "2026-04-15", 100_000L, 110_000L),
                budgetAdjustmentEntity("budget-adjust-future", "2026-04-25", "2026-04-27", 110_000L, 120_000L)
            )
        )

        val useCase = UpdatePaydayUseCase(
            transactionRunner = FakeTransactionRunner(),
            userSettingsStore = settingsStore,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            bucketAllocationPolicyDao = bucketPolicyDao,
            bucketAllocationAdjustmentDao = bucketAdjustmentDao,
            currentDateProvider = FakeCurrentDateProvider(LocalDate.of(2026, 4, 10)),
            cycleScheduleResolver = CycleScheduleResolver(BudgetCalculationService()),
            hybridLogicalClockService = HybridLogicalClockService()
        )

        useCase(UpdatePaydayRequest(paydayDate = 20))

        val activeBucketPolicies = bucketPolicyDao.getAllForSnapshot().filter { it.deletedAtEpochMs == null }
        assertEquals(
            listOf("2026-03-25:2026-04-20", "2026-04-20:2026-05-20"),
            activeBucketPolicies.map { "${it.cycleStartDate}:${it.cycleEndDateExclusive}" }
        )
        assertEquals(listOf(30_000L, 40_000L), activeBucketPolicies.map { it.allocatedAmountCents })

        val activeBucketAdjustments = bucketAdjustmentDao.getAllForSnapshot().filter { it.deletedAtEpochMs == null }
        assertEquals(listOf("2026-03-25", "2026-04-20"), activeBucketAdjustments.map { it.cycleStartDate })
        assertEquals(listOf("2026-04-15", "2026-04-27"), activeBucketAdjustments.map { it.effectiveDate })

        val activeBudgetAdjustments = budgetAdjustmentDao.getAllForSnapshot().filter { it.deletedAtEpochMs == null }
        assertEquals(listOf("2026-03-25", "2026-04-20"), activeBudgetAdjustments.map { it.cycleStartDate })
        assertEquals(listOf("2026-04-15", "2026-04-27"), activeBudgetAdjustments.map { it.effectiveDate })

        val pendingUndo = settingsStore.pendingSettingsUndo.first()
        assertNotNull(pendingUndo)
        assertEquals(2, pendingUndo?.bucketPoliciesToDeactivate?.size)
        assertEquals(2, pendingUndo?.bucketAdjustmentsToDeactivate?.size)
        assertEquals(2, pendingUndo?.adjustmentsToDeactivate?.size)
        assertTrue(
            bucketPolicyDao.getAllForSnapshot().count { it.deletedAtEpochMs != null && it.bucketUuid == "travel" } >= 2
        )
    }

    @Test
    fun invoke_keepsSparseFutureBucketPoliciesOnTheirLaterCycles() = runBlocking {
        val settingsStore = FakeUserSettingsStore(
            UserSettings(
                monthlyBudgetCents = 100_000L,
                portfolioMonthlyBudgetCents = 100_000L,
                paydayDate = 25
            )
        )
        val budgetPolicyDao = FakeBudgetPolicyDao(
            listOf(
                budgetPolicyEntity("current-policy", "2026-03-25", "2026-04-25"),
                budgetPolicyEntity("future-policy-1", "2026-04-25", "2026-05-25"),
                budgetPolicyEntity("future-policy-2", "2026-05-25", "2026-06-25")
            )
        )
        val bucketPolicyDao = FakeBucketAllocationPolicyDao(
            listOf(
                bucketPolicyEntity("bucket-current", "travel", "2026-03-25", "2026-04-25", 30_000L),
                bucketPolicyEntity("bucket-special", "travel", "2026-05-25", "2026-06-25", 60_000L)
            )
        )
        val useCase = UpdatePaydayUseCase(
            transactionRunner = FakeTransactionRunner(),
            userSettingsStore = settingsStore,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = FakeBudgetAdjustmentDao(),
            bucketAllocationPolicyDao = bucketPolicyDao,
            bucketAllocationAdjustmentDao = FakeBucketAllocationAdjustmentDao(),
            currentDateProvider = FakeCurrentDateProvider(LocalDate.of(2026, 4, 10)),
            cycleScheduleResolver = CycleScheduleResolver(BudgetCalculationService()),
            hybridLogicalClockService = HybridLogicalClockService()
        )

        useCase(UpdatePaydayRequest(paydayDate = 20))

        val activeTravelPolicies = bucketPolicyDao.getAllForSnapshot()
            .filter { it.deletedAtEpochMs == null && it.bucketUuid == "travel" }
            .sortedBy { it.cycleStartDate }

        assertEquals(
            listOf("2026-03-25:2026-04-20", "2026-05-20:2026-06-20"),
            activeTravelPolicies.map { "${it.cycleStartDate}:${it.cycleEndDateExclusive}" }
        )
        assertEquals(listOf(30_000L, 60_000L), activeTravelPolicies.map { it.allocatedAmountCents })
    }

    private fun budgetPolicyEntity(
        policyUuid: String,
        cycleStartDate: String,
        cycleEndDateExclusive: String
    ) = BudgetPolicyEntity(
        id = when (policyUuid) {
            "current-policy" -> 1L
            "future-policy" -> 2L
            "future-policy-1" -> 3L
            else -> 4L
        },
        policyUuid = policyUuid,
        cycleStartDate = cycleStartDate,
        cycleEndDateExclusive = cycleEndDateExclusive,
        budgetAmountCents = 100_000L,
        paydayDayOfMonth = 25,
        originInstallId = "test-install-id",
        lastModifiedByInstallId = "test-install-id",
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
        modClock = "0000000000001-0000-test-install-id"
    )

    private fun bucketPolicyEntity(
        allocationUuid: String,
        bucketUuid: String,
        cycleStartDate: String,
        cycleEndDateExclusive: String,
        allocatedAmountCents: Long
    ) = BucketAllocationPolicyEntity(
        id = when (allocationUuid) {
            "bucket-current" -> 1L
            "bucket-future" -> 2L
            else -> 3L
        },
        allocationUuid = allocationUuid,
        bucketUuid = bucketUuid,
        cycleStartDate = cycleStartDate,
        cycleEndDateExclusive = cycleEndDateExclusive,
        allocatedAmountCents = allocatedAmountCents,
        originInstallId = "test-install-id",
        lastModifiedByInstallId = "test-install-id",
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
        modClock = "0000000000001-0000-test-install-id"
    )

    private fun bucketAdjustmentEntity(
        adjustmentUuid: String,
        bucketUuid: String,
        cycleStartDate: String,
        effectiveDate: String,
        previousAllocatedAmountCents: Long,
        newAllocatedAmountCents: Long
    ) = BucketAllocationAdjustmentEntity(
        id = if (adjustmentUuid == "bucket-adjust-current") 1L else 2L,
        adjustmentUuid = adjustmentUuid,
        bucketUuid = bucketUuid,
        cycleStartDate = cycleStartDate,
        effectiveDate = effectiveDate,
        previousAllocatedAmountCents = previousAllocatedAmountCents,
        newAllocatedAmountCents = newAllocatedAmountCents,
        originInstallId = "test-install-id",
        lastModifiedByInstallId = "test-install-id",
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
        modClock = "0000000000001-0000-test-install-id"
    )

    private fun budgetAdjustmentEntity(
        adjustmentUuid: String,
        cycleStartDate: String,
        effectiveDate: String,
        previousMonthlyBudgetCents: Long,
        newMonthlyBudgetCents: Long
    ) = BudgetAdjustmentEntity(
        id = if (adjustmentUuid == "budget-adjust-current") 1L else 2L,
        adjustmentUuid = adjustmentUuid,
        cycleStartDate = cycleStartDate,
        effectiveDate = effectiveDate,
        previousMonthlyBudgetCents = previousMonthlyBudgetCents,
        newMonthlyBudgetCents = newMonthlyBudgetCents,
        originInstallId = "test-install-id",
        lastModifiedByInstallId = "test-install-id",
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
        modClock = "0000000000001-0000-test-install-id"
    )
}
