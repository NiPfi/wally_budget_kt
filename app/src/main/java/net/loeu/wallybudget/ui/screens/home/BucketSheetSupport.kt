package net.loeu.wallybudget.ui.screens.home

import net.loeu.wallybudget.domain.model.BucketSummaryState
import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import net.loeu.wallybudget.domain.usecase.BucketDraft

internal data class HomeBucketEditorState(
    val bucketUuid: String,
    val name: String,
    val amountText: String,
    val isSystemDefault: Boolean,
    val monthScoped: Boolean = false
)

internal const val BUCKET_CHANGED_SNACKBAR_MESSAGE = "This bucket changed before your update could be saved."

internal sealed interface BucketDraftBuildResult {
    data class Success(val drafts: List<BucketDraft>) : BucketDraftBuildResult
    data object BucketChanged : BucketDraftBuildResult
}

internal fun buildExistingHomeBucketDrafts(
    allBuckets: List<BudgetBucket>,
    bucketSummaries: List<BucketSummaryState>
): List<BucketDraft> {
    val summaryByBucketUuid = bucketSummaries.associateBy { it.bucket.bucketUuid }
    return allBuckets
        .sortedWith(compareBucketsClosedLast())
        .map { bucket ->
            val effectiveAllocation = summaryByBucketUuid[bucket.bucketUuid]?.allocatedThisCycleCents
                ?: bucket.defaultAllocatedAmountCents
            BucketDraft(
                bucketUuid = bucket.bucketUuid,
                name = bucket.name,
                trackingMode = bucket.trackingMode,
                balanceBehavior = bucket.balanceBehavior,
                defaultAllocatedAmountCents = effectiveAllocation,
                sortOrder = bucket.sortOrder,
                closeRequested = bucket.isClosed,
                monthScoped = bucket.monthScoped
            )
        }
}

internal fun sumOtherNamedBucketAllocationsForValidation(
    allBuckets: List<BudgetBucket>,
    bucketSummaries: List<BucketSummaryState>,
    editedBucketUuid: String
): Long {
    return buildExistingHomeBucketDrafts(
        allBuckets = allBuckets,
        bucketSummaries = bucketSummaries
    )
        .filterNot { draft ->
            draft.bucketUuid == editedBucketUuid ||
                draft.bucketUuid == DEFAULT_SPENDING_BUCKET_UUID ||
                draft.closeRequested
        }
        .sumOf { it.defaultAllocatedAmountCents }
}

internal fun buildHomeBucketDrafts(
    allBuckets: List<BudgetBucket>,
    bucketSummaries: List<BucketSummaryState>,
    newBucketDraft: BucketDraft
): List<BucketDraft> {
    return buildExistingHomeBucketDrafts(
        allBuckets = allBuckets,
        bucketSummaries = bucketSummaries
    ) + newBucketDraft
}

internal fun buildUpdatedHomeBucketDrafts(
    allBuckets: List<BudgetBucket>,
    bucketSummaries: List<BucketSummaryState>,
    updatedBucketDraft: BucketDraft
): List<BucketDraft> {
    return buildExistingHomeBucketDrafts(
        allBuckets = allBuckets,
        bucketSummaries = bucketSummaries
    ).map { draft ->
        if (draft.bucketUuid == updatedBucketDraft.bucketUuid) updatedBucketDraft else draft
    }
}

internal fun buildFreshUpdatedBucketDrafts(
    allBuckets: List<BudgetBucket>,
    bucketSummaries: List<BucketSummaryState>,
    updatedBucketDraft: BucketDraft
): BucketDraftBuildResult {
    val existingBucket = allBuckets.firstOrNull { it.bucketUuid == updatedBucketDraft.bucketUuid }
        ?.takeIf { !it.isClosed }
        ?: return BucketDraftBuildResult.BucketChanged
    val normalizedDraft = updatedBucketDraft.copy(
        trackingMode = existingBucket.trackingMode,
        balanceBehavior = existingBucket.balanceBehavior,
        defaultAllocatedAmountCents = if (existingBucket.bucketUuid == DEFAULT_SPENDING_BUCKET_UUID) {
            0L
        } else {
            updatedBucketDraft.defaultAllocatedAmountCents
        },
        sortOrder = existingBucket.sortOrder
    )
    return BucketDraftBuildResult.Success(
        buildUpdatedHomeBucketDrafts(
            allBuckets = allBuckets,
            bucketSummaries = bucketSummaries,
            updatedBucketDraft = normalizedDraft
        )
    )
}
