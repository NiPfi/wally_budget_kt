package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.data.local.entity.BucketTransferEntity
import net.loeu.wallybudget.data.local.entity.BudgetPolicyEntity
import net.loeu.wallybudget.data.local.entity.toDomainModel as bucketBaselineToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel as bucketTransferToDomainModel
import net.loeu.wallybudget.domain.model.BucketBalanceBehavior
import net.loeu.wallybudget.domain.model.BucketTransferReason
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_NAME
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.CurrentCycleBucketAllocationResolver
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class UpdatePortfolioPlanUseCaseScopeToggleTest {

    private data class ScopeToggleFixture(
        val bucketCycleBaselineDao: FakeBucketCycleBaselineDao,
        val bucketTransferDao: FakeBucketTransferDao,
        val useCase: UpdatePortfolioPlanUseCase
    )

    @Test
    fun invoke_monthScopedTogglePreservesRestoredCurrentCycleAllocation() = runBlocking {
        val fixture = createFixture()

        fixture.useCase(scopeToggleRequest())

        val currentBaselines = fixture.bucketCycleBaselineDao.getActiveForCycle("2026-03-25")
            .map { it.bucketBaselineToDomainModel() }
        val currentTransfers = fixture.bucketTransferDao.getForCycle("2026-03-25")
            .map { it.bucketTransferToDomainModel() }
        val resolver = CurrentCycleBucketAllocationResolver()

        assertEquals(
            0L,
            fixture.bucketCycleBaselineDao.findActiveBaselineForCycle("bills", "2026-03-25")?.baselineAmountCents
        )
        assertEquals(1, currentTransfers.size)
        assertEquals(
            3_000_00L,
            resolver.resolve(
                bucketUuid = "bills",
                cycleStart = LocalDate.of(2026, 3, 25),
                fallbackAllocationCents = 3_000_00L,
                baselines = currentBaselines,
                transfers = currentTransfers
            ).effectiveAllocationCents
        )
        assertEquals(
            1_000_00L,
            resolver.resolve(
                bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                cycleStart = LocalDate.of(2026, 3, 25),
                fallbackAllocationCents = 1_000_00L,
                baselines = currentBaselines,
                transfers = currentTransfers
            ).effectiveAllocationCents
        )
    }

    private fun createFixture(): ScopeToggleFixture {
        val bucketCycleBaselineDao = FakeBucketCycleBaselineDao(restoredBaselines())
        val bucketTransferDao = FakeBucketTransferDao(restoredTransfers())
        return ScopeToggleFixture(
            bucketCycleBaselineDao = bucketCycleBaselineDao,
            bucketTransferDao = bucketTransferDao,
            useCase = UpdatePortfolioPlanUseCase(
                transactionRunner = FakeTransactionRunner(),
                userSettingsStore = restoredSettingsStore(),
                budgetPolicyDao = restoredBudgetPolicyDao(),
                budgetBucketDao = restoredBudgetBucketDao(),
                bucketAllocationPolicyDao = restoredBucketPolicyDao(),
                bucketCycleBaselineDao = bucketCycleBaselineDao,
                bucketAllocationAdjustmentDao = FakeBucketAllocationAdjustmentDao(),
                bucketTransferDao = bucketTransferDao,
                expenseDao = FakeExpenseDao(),
                currentDateProvider = FakeCurrentDateProvider(LocalDate.of(2026, 4, 10)),
                cycleScheduleResolver = CycleScheduleResolver(BudgetCalculationService()),
                hybridLogicalClockService = HybridLogicalClockService()
            )
        )
    }

    private fun restoredBaselines() = listOf(
        bucketCycleBaselineEntity(
            baselineUuid = "default-baseline",
            cycleStartDate = "2026-03-25",
            cycleEndDateExclusive = "2026-04-25",
            baselineAmountCents = 4_000_00L
        ),
        bucketCycleBaselineEntity(
            id = 2L,
            baselineUuid = "bills-baseline",
            bucketUuid = "bills",
            cycleStartDate = "2026-03-25",
            cycleEndDateExclusive = "2026-04-25",
            baselineAmountCents = 0L
        )
    )

    private fun restoredTransfers() = listOf(
        BucketTransferEntity(
            id = 1L,
            transferUuid = "restore-bills-transfer",
            fromBucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
            toBucketUuid = "bills",
            amountCents = 3_000_00L,
            reason = BucketTransferReason.MANUAL_REALLOCATION,
            cycleStartDate = "2026-03-25",
            cycleEndDateExclusive = "2026-04-25",
            effectiveDate = "2026-03-25",
            originInstallId = "test-install-id",
            lastModifiedByInstallId = "test-install-id",
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
            modClock = "0000000000001-0000-test-install-id"
        )
    )

    private fun restoredSettingsStore(): FakeUserSettingsStore {
        return FakeUserSettingsStore(
            UserSettings(
                monthlyBudgetCents = 4_000_00L,
                portfolioMonthlyBudgetCents = 4_000_00L,
                paydayDate = 25,
                selectedBucketUuid = DEFAULT_SPENDING_BUCKET_UUID
            )
        )
    }

    private fun restoredBudgetPolicyDao(): FakeBudgetPolicyDao {
        return FakeBudgetPolicyDao(
            listOf(
                BudgetPolicyEntity(
                    id = 1L,
                    policyUuid = "current-policy",
                    cycleStartDate = "2026-03-25",
                    cycleEndDateExclusive = "2026-04-25",
                    budgetAmountCents = 4_000_00L,
                    paydayDayOfMonth = 25,
                    originInstallId = "test-install-id",
                    lastModifiedByInstallId = "test-install-id",
                    createdAtEpochMs = 1L,
                    updatedAtEpochMs = 1L,
                    modClock = "0000000000001-0000-test-install-id"
                )
            )
        )
    }

    private fun restoredBudgetBucketDao(): FakeBudgetBucketDao {
        return FakeBudgetBucketDao(
            listOf(
                bucketEntity(defaultAllocatedAmountCents = 1_000_00L),
                bucketEntity(
                    id = 2L,
                    bucketUuid = "bills",
                    name = "Bills",
                    balanceBehavior = BucketBalanceBehavior.RETAIN_IN_BUCKET,
                    defaultAllocatedAmountCents = 3_000_00L,
                    sortOrder = 1
                )
            )
        )
    }

    private fun restoredBucketPolicyDao(): FakeBucketAllocationPolicyDao {
        return FakeBucketAllocationPolicyDao(
            listOf(
                bucketPolicyEntity(
                    allocationUuid = "default-current",
                    cycleStartDate = "2026-03-25",
                    cycleEndDateExclusive = "2026-04-25",
                    allocatedAmountCents = 1_000_00L
                ),
                bucketPolicyEntity(
                    id = 2L,
                    allocationUuid = "bills-current",
                    bucketUuid = "bills",
                    cycleStartDate = "2026-03-25",
                    cycleEndDateExclusive = "2026-04-25",
                    allocatedAmountCents = 3_000_00L
                )
            )
        )
    }

    private fun scopeToggleRequest(): UpdatePortfolioPlanRequest {
        return UpdatePortfolioPlanRequest(
            portfolioMonthlyBudgetCents = 4_000_00L,
            buckets = listOf(
                BucketDraft(
                    bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                    name = DEFAULT_SPENDING_BUCKET_NAME,
                    defaultAllocatedAmountCents = 1_000_00L,
                    sortOrder = 0
                ),
                BucketDraft(
                    bucketUuid = "bills",
                    name = "Bills",
                    balanceBehavior = BucketBalanceBehavior.RETAIN_IN_BUCKET,
                    defaultAllocatedAmountCents = 3_000_00L,
                    sortOrder = 1,
                    monthScoped = true
                )
            )
        )
    }
}
