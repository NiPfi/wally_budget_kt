package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.loeu.wallybudget.data.local.dao.BudgetBucketDao
import net.loeu.wallybudget.data.local.entity.toDomainModel as bucketToDomainModel
import net.loeu.wallybudget.domain.model.BudgetBucket

class ObserveBudgetBucketsUseCase(
    private val budgetBucketDao: BudgetBucketDao
) {
    operator fun invoke(): Flow<List<BudgetBucket>> {
        return budgetBucketDao.observeAll().map { buckets ->
            buckets
                .filter { it.deletedAtEpochMs == null }
                .map { it.bucketToDomainModel() }
        }
    }
}
