package net.loeu.wallybudget.domain.usecase.internal

import net.loeu.wallybudget.domain.model.BudgetBucket

fun resolveSelectedOpenBucketUuid(
    selectedBucketUuid: String?,
    openBuckets: List<BudgetBucket>
): String? {
    val openBucketUuids = openBuckets.map { it.bucketUuid }
    return when {
        selectedBucketUuid in openBucketUuids -> selectedBucketUuid
        openBucketUuids.isNotEmpty() -> openBucketUuids.first()
        else -> null
    }
}

fun resolveSelectedOpenBucket(
    selectedBucketUuid: String?,
    openBuckets: List<BudgetBucket>
): BudgetBucket? {
    val resolvedBucketUuid = resolveSelectedOpenBucketUuid(selectedBucketUuid, openBuckets)
    return openBuckets.firstOrNull { it.bucketUuid == resolvedBucketUuid }
}
