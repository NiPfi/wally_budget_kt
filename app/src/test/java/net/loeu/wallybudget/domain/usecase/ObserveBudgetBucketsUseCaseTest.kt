package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveBudgetBucketsUseCaseTest {

    @Test
    fun invoke_excludesDeletedBucketsFromUiBucketList() = runBlocking {
        val useCase = ObserveBudgetBucketsUseCase(
            budgetBucketDao = FakeBudgetBucketDao(
                listOf(
                    bucketEntity(
                        id = 1L, bucketUuid = "bucket-1",
                        name = "Spending money", createdAtEpochMs = 1L
                    ),
                    bucketEntity(
                        id = 2L, bucketUuid = "bucket-2",
                        name = "Bills", createdAtEpochMs = 2L, deletedAtEpochMs = 2L
                    ),
                    bucketEntity(
                        id = 3L, bucketUuid = "bucket-3",
                        name = "Trip", createdAtEpochMs = 3L, closedAtEpochMs = 3L
                    )
                )
            )
        )

        val buckets = useCase().first()

        assertEquals(listOf("Spending money", "Trip"), buckets.map { it.name })
    }

}
