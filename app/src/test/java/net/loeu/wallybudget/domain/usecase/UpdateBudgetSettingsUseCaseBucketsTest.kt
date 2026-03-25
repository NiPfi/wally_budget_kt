@file:Suppress("LongMethod", "MaxLineLength")

package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.data.local.entity.BucketAllocationPolicyEntity
import net.loeu.wallybudget.data.local.entity.BudgetBucketEntity
import net.loeu.wallybudget.data.local.entity.toDomainModel as bucketBaselineToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel as bucketTransferToDomainModel
import net.loeu.wallybudget.domain.model.BudgetChangeMode
import net.loeu.wallybudget.domain.model.BucketBalanceBehavior
import net.loeu.wallybudget.domain.model.BucketTrackingMode
import net.loeu.wallybudget.domain.model.BucketTransferReason
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_NAME
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.CurrentCycleBucketAllocationResolver
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
        val bucketCycleBaselineDao: FakeBucketCycleBaselineDao,
        val bucketAllocationPolicyDao: FakeBucketAllocationPolicyDao,
        val bucketAllocationAdjustmentDao: FakeBucketAllocationAdjustmentDao,
        val bucketTransferDao: FakeBucketTransferDao,
        val useCase: UpdateBudgetSettingsUseCase
    )

    private data class ReallocationFixture(
        val budgetBucketDao: FakeBudgetBucketDao,
        val bucketCycleBaselineDao: FakeBucketCycleBaselineDao,
        val bucketAllocationPolicyDao: FakeBucketAllocationPolicyDao,
        val bucketAllocationAdjustmentDao: FakeBucketAllocationAdjustmentDao,
        val bucketTransferDao: FakeBucketTransferDao,
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

        val billsWasZeroed = fixture.bucketAllocationAdjustmentDao.getAllForSnapshot().any { adjustment ->
            adjustment.bucketUuid == "bills-bucket" &&
                adjustment.newAllocatedAmountCents == 0L &&
                adjustment.deletedAtEpochMs == null
        }
        assertTrue(!billsWasZeroed)
        assertNull(fixture.settingsStore.pendingPaydayUndo.first())
    }

    @Test
    fun invoke_midCycleReallocationRewritesPolicies_andCreatesTransferWithoutAdjustment() = runBlocking {
        val fixture = createReallocationFixture()

        fixture.useCase(
            requestFor(
                spendingBucketDraft().copy(defaultAllocatedAmountCents = 250_000L),
                BucketDraft(
                    bucketUuid = "bills-bucket",
                    name = "Bills",
                    trackingMode = BucketTrackingMode.CYCLE_RESERVE,
                    balanceBehavior = BucketBalanceBehavior.RETURN_TO_PORTFOLIO,
                    defaultAllocatedAmountCents = 200_000L,
                    sortOrder = 1,
                    isPrimary = false
                )
            )
        )

        val resolver = CurrentCycleBucketAllocationResolver()
        val baselines = fixture.bucketCycleBaselineDao.getActiveForCycle("2026-11-25").map { it.bucketBaselineToDomainModel() }
        val bucketsByUuid = fixture.budgetBucketDao.getAllForSnapshot()
            .filter { it.deletedAtEpochMs == null }
            .associateBy { it.bucketUuid }
        val transfer = fixture.bucketTransferDao.getForCycle("2026-11-25").single()

        assertEquals(
            300_000L,
            fixture.bucketCycleBaselineDao.findActiveBaselineForCycle(DEFAULT_SPENDING_BUCKET_UUID, "2026-11-25")?.baselineAmountCents
        )
        assertEquals(
            150_000L,
            fixture.bucketCycleBaselineDao.findActiveBaselineForCycle("bills-bucket", "2026-11-25")?.baselineAmountCents
        )
        assertEquals(
            250_000L,
            resolver.resolve(
                bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                cycleStart = LocalDate.of(2026, 11, 25),
                fallbackAllocationCents = bucketsByUuid.getValue(DEFAULT_SPENDING_BUCKET_UUID)!!.defaultAllocatedAmountCents,
                baselines = baselines,
                transfers = listOf(transfer.bucketTransferToDomainModel())
            ).effectiveAllocationCents
        )
        assertEquals(
            200_000L,
            resolver.resolve(
                bucketUuid = "bills-bucket",
                cycleStart = LocalDate.of(2026, 11, 25),
                fallbackAllocationCents = bucketsByUuid.getValue("bills-bucket")!!.defaultAllocatedAmountCents,
                baselines = baselines,
                transfers = listOf(transfer.bucketTransferToDomainModel())
            ).effectiveAllocationCents
        )
        assertTrue(fixture.bucketAllocationAdjustmentDao.getAllForSnapshot().isEmpty())
        assertEquals(BucketTransferReason.MANUAL_REALLOCATION, transfer.reason)
        assertEquals(DEFAULT_SPENDING_BUCKET_UUID, transfer.fromBucketUuid)
        assertEquals("bills-bucket", transfer.toBucketUuid)
        assertEquals(50_000L, transfer.amountCents)
    }

    private fun createFixture(): BucketSaveFixture {
        val settingsStore = migratedSettingsStore()
        val budgetPolicyDao = migratedBudgetPolicyDao()
        val budgetBucketDao = migratedBudgetBucketDao()
        val bucketCycleBaselineDao = migratedBucketCycleBaselineDao()
        val bucketAllocationPolicyDao = migratedBucketAllocationPolicyDao()
        val bucketAllocationAdjustmentDao = FakeBucketAllocationAdjustmentDao()
        val bucketTransferDao = FakeBucketTransferDao()
        return BucketSaveFixture(
            settingsStore = settingsStore,
            budgetBucketDao = budgetBucketDao,
            bucketCycleBaselineDao = bucketCycleBaselineDao,
            bucketAllocationPolicyDao = bucketAllocationPolicyDao,
            bucketAllocationAdjustmentDao = bucketAllocationAdjustmentDao,
            bucketTransferDao = bucketTransferDao,
            useCase = updateBudgetSettingsUseCase(
                settingsStore = settingsStore,
                budgetPolicyDao = budgetPolicyDao,
                budgetAdjustmentDao = FakeBudgetAdjustmentDao(),
                budgetBucketDao = budgetBucketDao,
                bucketAllocationPolicyDao = bucketAllocationPolicyDao,
                bucketCycleBaselineDao = bucketCycleBaselineDao,
                bucketAllocationAdjustmentDao = bucketAllocationAdjustmentDao,
                bucketTransferDao = bucketTransferDao,
                currentDate = LocalDate.of(2026, 12, 4)
            )
        )
    }

    private fun createReallocationFixture(): ReallocationFixture {
        val bucketTransferDao = FakeBucketTransferDao()
        val bucketAllocationAdjustmentDao = FakeBucketAllocationAdjustmentDao()
        val budgetBucketDao = FakeBudgetBucketDao(
            listOf(
                budgetBucketEntity(1L, DEFAULT_SPENDING_BUCKET_UUID, DEFAULT_SPENDING_BUCKET_NAME, 100_000L, 0),
                budgetBucketEntity(2L, "bills-bucket", "Bills", 150_000L, 1)
            )
        )
        val bucketCycleBaselineDao = FakeBucketCycleBaselineDao(
            listOf(
                bucketCycleBaselineEntity(
                    baselineUuid = "default-current",
                    cycleStartDate = "2026-11-25",
                    cycleEndDateExclusive = "2026-12-25",
                    baselineAmountCents = 300_000L
                ),
                bucketCycleBaselineEntity(
                    id = 2L,
                    baselineUuid = "bills-current",
                    bucketUuid = "bills-bucket",
                    cycleStartDate = "2026-11-25",
                    cycleEndDateExclusive = "2026-12-25",
                    baselineAmountCents = 150_000L
                )
            )
        )
        val bucketAllocationPolicyDao = FakeBucketAllocationPolicyDao(
            listOf(
                bucketAllocationPolicyEntity(
                    id = 1L,
                    allocationUuid = "default-current",
                    bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                    cycleStart = LocalDate.of(2026, 11, 25),
                    cycleEndExclusive = LocalDate.of(2026, 12, 25),
                    allocatedAmountCents = 300_000L
                ),
                bucketAllocationPolicyEntity(
                    id = 2L,
                    allocationUuid = "bills-current",
                    bucketUuid = "bills-bucket",
                    cycleStart = LocalDate.of(2026, 11, 25),
                    cycleEndExclusive = LocalDate.of(2026, 12, 25),
                    allocatedAmountCents = 150_000L
                )
            )
        )
        return ReallocationFixture(
            budgetBucketDao = budgetBucketDao,
            bucketCycleBaselineDao = bucketCycleBaselineDao,
            bucketAllocationPolicyDao = bucketAllocationPolicyDao,
            bucketAllocationAdjustmentDao = bucketAllocationAdjustmentDao,
            bucketTransferDao = bucketTransferDao,
            useCase = updateBudgetSettingsUseCase(
                settingsStore = migratedSettingsStore(),
                budgetPolicyDao = migratedBudgetPolicyDao(),
                budgetAdjustmentDao = FakeBudgetAdjustmentDao(),
                budgetBucketDao = budgetBucketDao,
                bucketAllocationPolicyDao = bucketAllocationPolicyDao,
                bucketCycleBaselineDao = bucketCycleBaselineDao,
                bucketAllocationAdjustmentDao = bucketAllocationAdjustmentDao,
                bucketTransferDao = bucketTransferDao,
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

    private fun migratedBucketCycleBaselineDao(): FakeBucketCycleBaselineDao {
        return FakeBucketCycleBaselineDao(
            listOf(
                bucketCycleBaselineEntity(
                    baselineUuid = "spending-baseline",
                    cycleStartDate = "2026-11-25",
                    cycleEndDateExclusive = "2026-12-25",
                    baselineAmountCents = 450_000L
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
        bucketCycleBaselineDao: FakeBucketCycleBaselineDao = FakeBucketCycleBaselineDao(),
        bucketAllocationAdjustmentDao: FakeBucketAllocationAdjustmentDao,
        bucketTransferDao: FakeBucketTransferDao = FakeBucketTransferDao(),
        currentDate: LocalDate
    ): UpdateBudgetSettingsUseCase {
        return UpdateBudgetSettingsUseCase(
            transactionRunner = FakeTransactionRunner(),
            userSettingsStore = settingsStore,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            budgetBucketDao = budgetBucketDao,
            bucketAllocationPolicyDao = bucketAllocationPolicyDao,
            bucketCycleBaselineDao = bucketCycleBaselineDao,
            bucketAllocationAdjustmentDao = bucketAllocationAdjustmentDao,
            bucketTransferDao = bucketTransferDao,
            expenseDao = FakeExpenseDao(),
            currentDateProvider = FakeCurrentDateProvider(currentDate),
            cycleScheduleResolver = cycleScheduleResolver,
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
