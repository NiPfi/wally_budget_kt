package net.loeu.wallybudget.domain.planning

import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.BucketBalanceBehavior
import net.loeu.wallybudget.domain.model.BucketTrackingMode
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_NAME
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID

data class PlanningConfig(
    val portfolioMonthlyBudgetCents: Long,
    val leftoverReceiverBucketUuid: String? = null
)

data class PlanningBucket(
    val bucketUuid: String,
    val name: String,
    val trackingMode: BucketTrackingMode,
    val balanceBehavior: BucketBalanceBehavior,
    val defaultAllocatedAmountCents: Long,
    val sortOrder: Int,
    val createdAtEpochMs: Long = 0L,
    val isClosed: Boolean = false,
    val isDeleted: Boolean = false
)

data class PlanningDraft(
    val bucketUuid: String,
    val name: String,
    val trackingMode: BucketTrackingMode,
    val balanceBehavior: BucketBalanceBehavior,
    val defaultAllocatedAmountCents: Long,
    val sortOrder: Int,
    val closeRequested: Boolean = false
) {
    @Suppress("UNUSED_PARAMETER")
    constructor(
        bucketUuid: String,
        name: String,
        trackingMode: BucketTrackingMode,
        balanceBehavior: BucketBalanceBehavior,
        defaultAllocatedAmountCents: Long,
        sortOrder: Int,
        isPrimary: Boolean,
        closeRequested: Boolean = false
    ) : this(
        bucketUuid = bucketUuid,
        name = name,
        trackingMode = trackingMode,
        balanceBehavior = balanceBehavior,
        defaultAllocatedAmountCents = defaultAllocatedAmountCents,
        sortOrder = sortOrder,
        closeRequested = closeRequested
    )
}

data class SavePlanningRequest(
    val portfolioMonthlyBudgetCents: Long,
    val leftoverReceiverBucketUuid: String? = null,
    val buckets: List<PlanningDraft> = emptyList()
) {
    val bucketDrafts: List<PlanningDraft>
        get() = buckets
}

data class PlanningBucketState(
    val draft: PlanningDraft,
    val isComputedRemainder: Boolean
)

data class PlanningState(
    val config: PlanningConfig,
    val resolvedLeftoverReceiverBucketUuid: String?,
    val buckets: List<PlanningBucketState>,
    val validationErrors: List<String>,
    val selectedBucketFallbackUuid: String?
) {
    val normalizedDrafts: List<PlanningDraft>
        get() = buckets.map { it.draft }

    val isValid: Boolean
        get() = validationErrors.isEmpty()
}

data class PlanningContext(
    val config: PlanningConfig,
    val buckets: List<PlanningBucket>,
    val selectedBucketUuid: String?
)

data class PlanningChangeSet(
    val request: SavePlanningRequest,
    val state: PlanningState,
    val budgetChanged: Boolean,
    val bucketChanged: Boolean,
    val leftoverReceiverChanged: Boolean
) {
    val hasChanges: Boolean
        get() = budgetChanged || bucketChanged || leftoverReceiverChanged
}

fun PlanningBucket.toDraft(
    allocatedAmountCents: Long = defaultAllocatedAmountCents
): PlanningDraft {
    return PlanningDraft(
        bucketUuid = bucketUuid,
        name = name,
        trackingMode = trackingMode,
        balanceBehavior = balanceBehavior,
        defaultAllocatedAmountCents = allocatedAmountCents,
        sortOrder = sortOrder,
        closeRequested = isClosed
    )
}

fun BudgetBucket.toPlanningBucket(): PlanningBucket {
    return PlanningBucket(
        bucketUuid = bucketUuid,
        name = name,
        trackingMode = trackingMode,
        balanceBehavior = balanceBehavior,
        defaultAllocatedAmountCents = defaultAllocatedAmountCents,
        sortOrder = sortOrder,
        createdAtEpochMs = createdAtEpochMs,
        isClosed = isClosed,
        isDeleted = deletedAtEpochMs != null
    )
}

fun defaultPlanningDraft(portfolioMonthlyBudgetCents: Long): PlanningDraft {
    return PlanningDraft(
        bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
        name = DEFAULT_SPENDING_BUCKET_NAME,
        trackingMode = BucketTrackingMode.DAILY_TARGET,
        balanceBehavior = BucketBalanceBehavior.RETURN_TO_PORTFOLIO,
        defaultAllocatedAmountCents = portfolioMonthlyBudgetCents,
        sortOrder = 0
    )
}
