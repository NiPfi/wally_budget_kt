package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.data.local.entity.BudgetBucketEntity
import net.loeu.wallybudget.domain.model.BucketBalanceBehavior
import net.loeu.wallybudget.domain.model.BucketTrackingMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveBudgetBucketsUseCaseTest {

    @Test
    fun invoke_excludesDeletedBucketsFromUiBucketList() = runBlocking {
        val useCase = ObserveBudgetBucketsUseCase(
            budgetBucketDao = FakeBudgetBucketDao(
                listOf(
                    bucketEntity(id = 1L, name = "Spending money"),
                    bucketEntity(id = 2L, name = "Bills", deletedAtEpochMs = 2L),
                    bucketEntity(id = 3L, name = "Trip", closedAtEpochMs = 3L)
                )
            )
        )

        val buckets = useCase().first()

        assertEquals(listOf("Spending money", "Trip"), buckets.map { it.name })
    }

    private fun bucketEntity(
        id: Long,
        name: String,
        closedAtEpochMs: Long? = null,
        deletedAtEpochMs: Long? = null
    ): BudgetBucketEntity {
        return BudgetBucketEntity(
            id = id,
            bucketUuid = "bucket-$id",
            name = name,
            trackingMode = BucketTrackingMode.DAILY_TARGET,
            balanceBehavior = BucketBalanceBehavior.RETURN_TO_PORTFOLIO,
            defaultAllocatedAmountCents = 0L,
            sortOrder = id.toInt(),
            isPrimary = id == 1L,
            originInstallId = "test-install-id",
            lastModifiedByInstallId = "test-install-id",
            createdAtEpochMs = id,
            updatedAtEpochMs = id,
            closedAtEpochMs = closedAtEpochMs,
            deletedAtEpochMs = deletedAtEpochMs,
            modClock = "%013d-%04d-%s".format(id, 0, "test-install-id")
        )
    }
}
