package net.loeu.wallybudget.domain.usecase.internal

import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID

fun resolveSelectedOpenBucketUuid(
    selectedBucketUuid: String?,
    openBuckets: List<BudgetBucket>
): String? {
    val openBucketUuids = openBuckets.map { it.bucketUuid }
    return when {
        selectedBucketUuid in openBucketUuids -> selectedBucketUuid
        DEFAULT_SPENDING_BUCKET_UUID in openBucketUuids -> DEFAULT_SPENDING_BUCKET_UUID
        else -> openBucketUuids.firstOrNull()
    }
}

fun resolveSelectedOpenBucket(
    selectedBucketUuid: String?,
    openBuckets: List<BudgetBucket>
): BudgetBucket? {
    val resolvedBucketUuid = resolveSelectedOpenBucketUuid(selectedBucketUuid, openBuckets)
    return openBuckets.firstOrNull { it.bucketUuid == resolvedBucketUuid }
}
