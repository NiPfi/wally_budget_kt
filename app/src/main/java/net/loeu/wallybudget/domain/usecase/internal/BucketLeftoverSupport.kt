package net.loeu.wallybudget.domain.usecase.internal

import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import net.loeu.wallybudget.domain.usecase.BucketDraft

fun resolveLeftoverReceiverBucketUuid(
    preferredBucketUuid: String?,
    openBuckets: List<BudgetBucket>
): String? {
    val openBucketUuids = openBuckets.map { it.bucketUuid }
    return when {
        preferredBucketUuid in openBucketUuids -> preferredBucketUuid
        openBucketUuids.isNotEmpty() -> openBucketUuids.first()
        DEFAULT_SPENDING_BUCKET_UUID in openBucketUuids -> DEFAULT_SPENDING_BUCKET_UUID
        else -> null
    }
}

fun resolveLeftoverReceiverDraftUuid(
    preferredBucketUuid: String?,
    openDrafts: List<BucketDraft>,
    existingBucketsByUuid: Map<String, BudgetBucket> = emptyMap()
): String? {
    val openBuckets = openDrafts.map { draft ->
        existingBucketsByUuid[draft.bucketUuid]?.copy(
            name = draft.name.trim(),
            trackingMode = draft.trackingMode,
            balanceBehavior = draft.balanceBehavior,
            defaultAllocatedAmountCents = draft.defaultAllocatedAmountCents,
            sortOrder = draft.sortOrder,
            closedAtEpochMs = null,
            deletedAtEpochMs = null
        ) ?: BudgetBucket(
            bucketUuid = draft.bucketUuid,
            name = draft.name.trim(),
            trackingMode = draft.trackingMode,
            balanceBehavior = draft.balanceBehavior,
            defaultAllocatedAmountCents = draft.defaultAllocatedAmountCents,
            sortOrder = draft.sortOrder,
            originInstallId = "",
            lastModifiedByInstallId = "",
            createdAtEpochMs = 0L,
            updatedAtEpochMs = 0L,
            modClock = ""
        )
    }
    return resolveLeftoverReceiverBucketUuid(preferredBucketUuid, openBuckets)
}
