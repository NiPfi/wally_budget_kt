@file:Suppress("LongMethod")

package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_NAME
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class EnsureDefaultBucketStateUseCaseTest {

    @Test
    fun invoke_repairsCurrentDefaultBucketBaselineFromCurrentCycleNamedBuckets() = runBlocking {
        val cycleStart = LocalDate.of(2026, 3, 25)
        val cycleEnd = LocalDate.of(2026, 4, 25)
        val budgetBucketDao = migratedBudgetBucketDao()
        val bucketAllocationPolicyDao = staleCurrentBucketPolicyDao(cycleStart, cycleEnd)
        val bucketCycleBaselineDao = staleCurrentBucketBaselineDao(cycleStart, cycleEnd)
        val useCase = createUseCase(cycleStart, budgetBucketDao, bucketCycleBaselineDao)

        useCase(now = LocalDate.of(2026, 4, 10))

        val repairedDefaultCurrentBaseline = bucketCycleBaselineDao.findActiveBaselineForCycle(
            bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
            cycleStartDate = cycleStart.toString()
        )
        val repairedDefaultBucket = budgetBucketDao.findByBucketUuid(DEFAULT_SPENDING_BUCKET_UUID)

        assertEquals(200_000L, repairedDefaultCurrentBaseline?.baselineAmountCents)
        assertEquals(100_000L, repairedDefaultBucket?.defaultAllocatedAmountCents)
    }

    @Test
    fun invoke_ignoresFullyClosedBucketsButKeepsSettledClosingBucketsInCurrentCycleRepair() = runBlocking {
        val cycleStart = LocalDate.of(2026, 3, 25)
        val cycleEnd = LocalDate.of(2026, 4, 25)
        val budgetBucketDao = FakeBudgetBucketDao(
            listOf(
                bucketEntity(
                    id = 1L,
                    bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                    name = DEFAULT_SPENDING_BUCKET_NAME,
                    defaultAllocatedAmountCents = 3_500_00L,
                    sortOrder = 0
                ),
                bucketEntity(
                    id = 2L,
                    bucketUuid = "closing-bills",
                    name = "Bills",
                    defaultAllocatedAmountCents = 2_500_00L,
                    sortOrder = 1,
                    settledCloseCycleEndDateExclusive = cycleEnd.toString()
                ),
                bucketEntity(
                    id = 3L,
                    bucketUuid = "closed-travel",
                    name = "Travel",
                    defaultAllocatedAmountCents = 2_000_00L,
                    sortOrder = 2,
                    closedAtEpochMs = 10L
                )
            )
        )
        val bucketAllocationPolicyDao = FakeBucketAllocationPolicyDao(
            listOf(
                bucketPolicyEntity(
                    allocationUuid = "default-current",
                    cycleStartDate = cycleStart.toString(),
                    cycleEndDateExclusive = cycleEnd.toString(),
                    allocatedAmountCents = 3_500_00L
                ),
                bucketPolicyEntity(
                    id = 2L,
                    allocationUuid = "closing-bills-current",
                    bucketUuid = "closing-bills",
                    cycleStartDate = cycleStart.toString(),
                    cycleEndDateExclusive = cycleEnd.toString(),
                    allocatedAmountCents = 2_500_00L
                )
            )
        )
        val bucketCycleBaselineDao = FakeBucketCycleBaselineDao(
            listOf(
                bucketCycleBaselineEntity(
                    baselineUuid = "default-current",
                    cycleStartDate = cycleStart.toString(),
                    cycleEndDateExclusive = cycleEnd.toString(),
                    baselineAmountCents = 3_500_00L
                ),
                bucketCycleBaselineEntity(
                    id = 2L,
                    baselineUuid = "closing-bills-current",
                    bucketUuid = "closing-bills",
                    cycleStartDate = cycleStart.toString(),
                    cycleEndDateExclusive = cycleEnd.toString(),
                    baselineAmountCents = 2_500_00L
                )
            )
        )

        createUseCase(cycleStart, budgetBucketDao, bucketCycleBaselineDao)(now = LocalDate.of(2026, 4, 10))

        val repairedDefaultCurrentBaseline = bucketCycleBaselineDao.findActiveBaselineForCycle(
            bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
            cycleStartDate = cycleStart.toString()
        )

        assertEquals(1_000_00L, repairedDefaultCurrentBaseline?.baselineAmountCents)
    }

    private fun createUseCase(
        cycleStart: LocalDate,
        budgetBucketDao: FakeBudgetBucketDao,
        bucketCycleBaselineDao: FakeBucketCycleBaselineDao = FakeBucketCycleBaselineDao()
    ): EnsureDefaultBucketStateUseCase {
        return EnsureDefaultBucketStateUseCase(
            transactionRunner = FakeTransactionRunner(),
            userSettingsStore = FakeUserSettingsStore(
                UserSettings(
                    monthlyBudgetCents = 100_000L,
                    portfolioMonthlyBudgetCents = 350_000L,
                    paydayDate = 25,
                    selectedBucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                    lastResetTimestamp = cycleStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                )
            ),
            budgetBucketDao = budgetBucketDao,
            bucketCycleBaselineDao = bucketCycleBaselineDao,
            bucketAllocationAdjustmentDao = FakeBucketAllocationAdjustmentDao(),
            budgetCalculationService = BudgetCalculationService(),
            hybridLogicalClockService = HybridLogicalClockService()
        )
    }

    private fun migratedBudgetBucketDao(): FakeBudgetBucketDao {
        return FakeBudgetBucketDao(
            listOf(
                bucketEntity(
                    id = 1L,
                    bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                    name = DEFAULT_SPENDING_BUCKET_NAME,
                    defaultAllocatedAmountCents = 350_000L,
                    sortOrder = 0
                ),
                bucketEntity(
                    id = 2L,
                    bucketUuid = "bills",
                    name = "Bills",
                    defaultAllocatedAmountCents = 250_000L,
                    sortOrder = 1
                )
            )
        )
    }

    private fun staleCurrentBucketPolicyDao(
        cycleStart: LocalDate,
        cycleEnd: LocalDate
    ): FakeBucketAllocationPolicyDao {
        return FakeBucketAllocationPolicyDao(
            listOf(
                bucketPolicyEntity(
                    allocationUuid = "default-current",
                    cycleStartDate = cycleStart.toString(),
                    cycleEndDateExclusive = cycleEnd.toString(),
                    allocatedAmountCents = 350_000L
                ),
                bucketPolicyEntity(
                    id = 2L,
                    allocationUuid = "bills-current",
                    bucketUuid = "bills",
                    cycleStartDate = cycleStart.toString(),
                    cycleEndDateExclusive = cycleEnd.toString(),
                    allocatedAmountCents = 150_000L
                )
            )
        )
    }

    private fun staleCurrentBucketBaselineDao(
        cycleStart: LocalDate,
        cycleEnd: LocalDate
    ): FakeBucketCycleBaselineDao {
        return FakeBucketCycleBaselineDao(
            listOf(
                bucketCycleBaselineEntity(
                    baselineUuid = "default-current",
                    cycleStartDate = cycleStart.toString(),
                    cycleEndDateExclusive = cycleEnd.toString(),
                    baselineAmountCents = 350_000L
                ),
                bucketCycleBaselineEntity(
                    id = 2L,
                    baselineUuid = "bills-current",
                    bucketUuid = "bills",
                    cycleStartDate = cycleStart.toString(),
                    cycleEndDateExclusive = cycleEnd.toString(),
                    baselineAmountCents = 150_000L
                )
            )
        )
    }
}
