package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.data.local.entity.BucketAllocationPolicyEntity
import net.loeu.wallybudget.data.local.entity.BudgetBucketEntity
import net.loeu.wallybudget.domain.model.BudgetChangeMode
import net.loeu.wallybudget.domain.model.BucketBalanceBehavior
import net.loeu.wallybudget.domain.model.BucketTrackingMode
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_NAME
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
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

class UpdateBudgetSettingsUseCaseBucketsTest {

    private val cycleScheduleResolver = CycleScheduleResolver(BudgetCalculationService())
    private val hybridLogicalClockService = HybridLogicalClockService()

    private data class BucketSaveFixture(
        val settingsStore: FakeUserSettingsStore,
        val budgetBucketDao: FakeBudgetBucketDao,
        val bucketAllocationPolicyDao: FakeBucketAllocationPolicyDao,
        val bucketAllocationAdjustmentDao: FakeBucketAllocationAdjustmentDao,
        val useCase: UpdateBudgetSettingsUseCase
    )

    @Test
    fun invoke_bucketSaveDoesNotRollbackPreviouslyAddedBuckets() = runBlocking {
        val fixture = createFixture()

        fixture.useCase(requestWithBills())
        fixture.useCase(requestWithBillsAndTravel())

        val activeBuckets = fixture.budgetBucketDao.getAllForSnapshot().filter { it.deletedAtEpochMs == null }
        assertEquals(listOf(DEFAULT_SPENDING_BUCKET_NAME, "Bills", "Travel"), activeBuckets.map { it.name })
        assertNull(activeBuckets.firstOrNull { it.bucketUuid == "bills-bucket" }?.closedAtEpochMs)

        val activePolicies = fixture.bucketAllocationPolicyDao.getAllForSnapshot()
            .filter { it.deletedAtEpochMs == null }
            .associateBy { it.bucketUuid }
        assertEquals(270_000L, activePolicies.getValue(DEFAULT_SPENDING_BUCKET_UUID).allocatedAmountCents)
        assertEquals(150_000L, activePolicies.getValue("bills-bucket").allocatedAmountCents)
        assertEquals(30_000L, activePolicies.getValue("travel-bucket").allocatedAmountCents)

        val billsWasZeroed = fixture.bucketAllocationAdjustmentDao.getAllForSnapshot().any { adjustment ->
            adjustment.bucketUuid == "bills-bucket" &&
                adjustment.newAllocatedAmountCents == 0L &&
                adjustment.deletedAtEpochMs == null
        }
        assertTrue(!billsWasZeroed)
        assertNull(fixture.settingsStore.pendingPaydayUndo.first())
    }

    private fun createFixture(): BucketSaveFixture {
        val settingsStore = migratedSettingsStore()
        val budgetPolicyDao = migratedBudgetPolicyDao()
        val budgetBucketDao = migratedBudgetBucketDao()
        val bucketAllocationPolicyDao = migratedBucketAllocationPolicyDao()
        val bucketAllocationAdjustmentDao = FakeBucketAllocationAdjustmentDao()
        return BucketSaveFixture(
            settingsStore = settingsStore,
            budgetBucketDao = budgetBucketDao,
            bucketAllocationPolicyDao = bucketAllocationPolicyDao,
            bucketAllocationAdjustmentDao = bucketAllocationAdjustmentDao,
            useCase = updateBudgetSettingsUseCase(
                settingsStore = settingsStore,
                budgetPolicyDao = budgetPolicyDao,
                budgetAdjustmentDao = FakeBudgetAdjustmentDao(),
                budgetBucketDao = budgetBucketDao,
                bucketAllocationPolicyDao = bucketAllocationPolicyDao,
                bucketAllocationAdjustmentDao = bucketAllocationAdjustmentDao,
                currentDate = LocalDate.of(2026, 12, 4)
            )
        )
    }

    private fun migratedSettingsStore(): FakeUserSettingsStore {
        return FakeUserSettingsStore(
            UserSettings(
                monthlyBudgetCents = 100_000L,
                portfolioMonthlyBudgetCents = 450_000L,
                paydayDate = 25,
                primaryBucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                selectedBucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                lastResetTimestamp = LocalDate.of(2026, 11, 25)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            )
        )
    }

    private fun migratedBudgetPolicyDao(): FakeBudgetPolicyDao {
        return FakeBudgetPolicyDao(
            listOf(
                budgetPolicyEntity(
                    id = 1L,
                    cycleStart = LocalDate.of(2026, 11, 25),
                    cycleEndExclusive = LocalDate.of(2026, 12, 25),
                    budgetAmountCents = 450_000L
                )
            )
        )
    }

    private fun migratedBudgetBucketDao(): FakeBudgetBucketDao {
        return FakeBudgetBucketDao(
            listOf(
                budgetBucketEntity(
                    id = 1L,
                    bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                    name = DEFAULT_SPENDING_BUCKET_NAME,
                    defaultAllocatedAmountCents = 100_000L,
                    sortOrder = 0
                )
            )
        )
    }

    private fun migratedBucketAllocationPolicyDao(): FakeBucketAllocationPolicyDao {
        return FakeBucketAllocationPolicyDao(
            listOf(
                bucketAllocationPolicyEntity(
                    id = 1L,
                    allocationUuid = "spending-policy",
                    bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                    cycleStart = LocalDate.of(2026, 11, 25),
                    cycleEndExclusive = LocalDate.of(2026, 12, 25),
                    allocatedAmountCents = 100_000L
                )
            )
        )
    }

    private fun requestWithBills(): UpdateBudgetSettingsRequest {
        return requestFor(
            spendingBucketDraft(),
            BucketDraft(
                bucketUuid = "bills-bucket",
                name = "Bills",
                trackingMode = BucketTrackingMode.CYCLE_RESERVE,
                balanceBehavior = BucketBalanceBehavior.RETURN_TO_PORTFOLIO,
                defaultAllocatedAmountCents = 150_000L,
                sortOrder = 1,
                isPrimary = false
            )
        )
    }

    private fun requestWithBillsAndTravel(): UpdateBudgetSettingsRequest {
        return requestFor(
            spendingBucketDraft(),
            BucketDraft(
                bucketUuid = "bills-bucket",
                name = "Bills",
                trackingMode = BucketTrackingMode.CYCLE_RESERVE,
                balanceBehavior = BucketBalanceBehavior.RETURN_TO_PORTFOLIO,
                defaultAllocatedAmountCents = 150_000L,
                sortOrder = 1,
                isPrimary = false
            ),
            BucketDraft(
                bucketUuid = "travel-bucket",
                name = "Travel",
                trackingMode = BucketTrackingMode.CYCLE_RESERVE,
                balanceBehavior = BucketBalanceBehavior.RETAIN_IN_BUCKET,
                defaultAllocatedAmountCents = 30_000L,
                sortOrder = 2,
                isPrimary = false
            )
        )
    }

    private fun spendingBucketDraft(): BucketDraft {
        return BucketDraft(
            bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
            name = DEFAULT_SPENDING_BUCKET_NAME,
            trackingMode = BucketTrackingMode.DAILY_TARGET,
            balanceBehavior = BucketBalanceBehavior.RETURN_TO_PORTFOLIO,
            defaultAllocatedAmountCents = 100_000L,
            sortOrder = 0,
            isPrimary = true
        )
    }

    private fun requestFor(vararg buckets: BucketDraft): UpdateBudgetSettingsRequest {
        return UpdateBudgetSettingsRequest(
            portfolioMonthlyBudgetCents = 450_000L,
            paydayDate = 25,
            buckets = buckets.toList(),
            budgetChangeMode = BudgetChangeMode.APPLY_CURRENT_NOW
        )
    }

    private fun updateBudgetSettingsUseCase(
        settingsStore: FakeUserSettingsStore,
        budgetPolicyDao: FakeBudgetPolicyDao,
        budgetAdjustmentDao: FakeBudgetAdjustmentDao,
        budgetBucketDao: FakeBudgetBucketDao,
        bucketAllocationPolicyDao: FakeBucketAllocationPolicyDao,
        bucketAllocationAdjustmentDao: FakeBucketAllocationAdjustmentDao,
        currentDate: LocalDate
    ): UpdateBudgetSettingsUseCase {
        return UpdateBudgetSettingsUseCase(
            transactionRunner = FakeTransactionRunner(),
            userSettingsStore = settingsStore,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            budgetBucketDao = budgetBucketDao,
            bucketAllocationPolicyDao = bucketAllocationPolicyDao,
            bucketAllocationAdjustmentDao = bucketAllocationAdjustmentDao,
            bucketTransferDao = FakeBucketTransferDao(),
            expenseDao = FakeExpenseDao(),
            currentDateProvider = FakeCurrentDateProvider(currentDate),
            cycleScheduleResolver = cycleScheduleResolver,
            budgetAdjustmentResolver = BudgetAdjustmentResolver(),
            hybridLogicalClockService = hybridLogicalClockService
        )
    }

    private fun budgetBucketEntity(
        id: Long,
        bucketUuid: String,
        name: String,
        defaultAllocatedAmountCents: Long,
        sortOrder: Int
    ): BudgetBucketEntity {
        return BudgetBucketEntity(
            id = id,
            bucketUuid = bucketUuid,
            name = name,
            trackingMode = BucketTrackingMode.DAILY_TARGET,
            balanceBehavior = BucketBalanceBehavior.RETURN_TO_PORTFOLIO,
            defaultAllocatedAmountCents = defaultAllocatedAmountCents,
            sortOrder = sortOrder,
            originInstallId = "test-install-id",
            lastModifiedByInstallId = "test-install-id",
            createdAtEpochMs = id,
            updatedAtEpochMs = id,
            modClock = hybridLogicalClockService.format(id, 0, "test-install-id")
        )
    }

    private fun bucketAllocationPolicyEntity(
        id: Long,
        allocationUuid: String,
        bucketUuid: String,
        cycleStart: LocalDate,
        cycleEndExclusive: LocalDate,
        allocatedAmountCents: Long
    ): BucketAllocationPolicyEntity {
        return BucketAllocationPolicyEntity(
            id = id,
            allocationUuid = allocationUuid,
            bucketUuid = bucketUuid,
            cycleStartDate = cycleStart.toString(),
            cycleEndDateExclusive = cycleEndExclusive.toString(),
            allocatedAmountCents = allocatedAmountCents,
            originInstallId = "test-install-id",
            lastModifiedByInstallId = "test-install-id",
            createdAtEpochMs = id,
            updatedAtEpochMs = id,
            modClock = hybridLogicalClockService.format(id, 0, "test-install-id")
        )
    }
}
