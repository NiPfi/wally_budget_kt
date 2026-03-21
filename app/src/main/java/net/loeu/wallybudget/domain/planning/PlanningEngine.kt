package net.loeu.wallybudget.domain.planning

import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import java.util.Locale

class PlanningEngine {

    fun normalize(
        context: PlanningContext,
        request: SavePlanningRequest
    ): PlanningState {
        val drafts = resolveDrafts(context = context, request = request)
        val trimmedDrafts = drafts.map { it.copy(name = it.name.trim()) }
        val openDrafts = trimmedDrafts.filterNot { it.closeRequested }
        val resolvedLeftoverReceiverBucketUuid = resolveLeftoverReceiverBucketUuid(
            preferredBucketUuid = request.leftoverReceiverBucketUuid ?: context.config.leftoverReceiverBucketUuid,
            openDrafts = openDrafts
        )
        val namedOpenAllocationTotal = trimmedDrafts
            .filterNot { it.closeRequested || it.bucketUuid == resolvedLeftoverReceiverBucketUuid }
            .sumOf { it.defaultAllocatedAmountCents }
        val normalizedDrafts = trimmedDrafts
            .map { draft ->
                if (draft.bucketUuid == resolvedLeftoverReceiverBucketUuid && !draft.closeRequested) {
                    draft.copy(
                        defaultAllocatedAmountCents = (request.portfolioMonthlyBudgetCents - namedOpenAllocationTotal)
                            .coerceAtLeast(0L)
                    )
                } else {
                    draft
                }
            }
            .sortedBy { it.sortOrder }
        val selectedBucketFallbackUuid = resolveSelectedOpenBucketUuid(
            selectedBucketUuid = context.selectedBucketUuid,
            openDrafts = normalizedDrafts.filterNot { it.closeRequested }
        )
        return PlanningState(
            config = PlanningConfig(
                portfolioMonthlyBudgetCents = request.portfolioMonthlyBudgetCents,
                leftoverReceiverBucketUuid = request.leftoverReceiverBucketUuid
            ),
            resolvedLeftoverReceiverBucketUuid = resolvedLeftoverReceiverBucketUuid,
            buckets = normalizedDrafts.map { draft ->
                PlanningBucketState(
                    draft = draft,
                    isComputedRemainder = !draft.closeRequested &&
                        draft.bucketUuid == resolvedLeftoverReceiverBucketUuid
                )
            },
            validationErrors = validateNormalizedDrafts(
                request = request,
                context = context,
                normalizedDrafts = normalizedDrafts,
                resolvedLeftoverReceiverBucketUuid = resolvedLeftoverReceiverBucketUuid
            ),
            selectedBucketFallbackUuid = selectedBucketFallbackUuid
        )
    }

    fun buildChangeSet(
        context: PlanningContext,
        request: SavePlanningRequest
    ): PlanningChangeSet {
        val normalizedState = normalize(context = context, request = request)
        val currentState = normalize(
            context = context,
            request = SavePlanningRequest(
                portfolioMonthlyBudgetCents = context.config.portfolioMonthlyBudgetCents,
                leftoverReceiverBucketUuid = context.config.leftoverReceiverBucketUuid
            )
        )
        return PlanningChangeSet(
            request = request,
            state = normalizedState,
            budgetChanged = request.portfolioMonthlyBudgetCents != context.config.portfolioMonthlyBudgetCents,
            bucketChanged = hasBucketChanges(
                newDrafts = normalizedState.normalizedDrafts,
                existingBuckets = context.buckets
            ),
            leftoverReceiverChanged = normalizedState.resolvedLeftoverReceiverBucketUuid !=
                currentState.resolvedLeftoverReceiverBucketUuid
        )
    }

    fun resolveDrafts(
        context: PlanningContext,
        request: SavePlanningRequest
    ): List<PlanningDraft> {
        val activeBuckets = context.buckets.filterNot { it.isDeleted }
        return when {
            request.bucketDrafts.isNotEmpty() -> request.bucketDrafts.sortedBy { it.sortOrder }
            activeBuckets.isEmpty() -> listOf(defaultPlanningDraft(request.portfolioMonthlyBudgetCents))
            else -> activeBuckets
                .sortedWith(compareBy<PlanningBucket> { it.sortOrder }.thenBy { it.createdAtEpochMs })
                .map { it.toDraft() }
        }
    }

    fun resolveLeftoverReceiverBucketUuid(
        preferredBucketUuid: String?,
        openDrafts: List<PlanningDraft>
    ): String? {
        val openBucketUuids = openDrafts.map { it.bucketUuid }
        return when {
            preferredBucketUuid in openBucketUuids -> preferredBucketUuid
            openBucketUuids.isNotEmpty() -> openBucketUuids.first()
            DEFAULT_SPENDING_BUCKET_UUID in openBucketUuids -> DEFAULT_SPENDING_BUCKET_UUID
            else -> null
        }
    }

    fun resolveSelectedOpenBucketUuid(
        selectedBucketUuid: String?,
        openDrafts: List<PlanningDraft>
    ): String? {
        val openBucketUuids = openDrafts.map { it.bucketUuid }
        return when {
            selectedBucketUuid in openBucketUuids -> selectedBucketUuid
            openBucketUuids.isNotEmpty() -> openBucketUuids.first()
            else -> null
        }
    }

    fun hasBucketChanges(
        newDrafts: List<PlanningDraft>,
        existingBuckets: List<PlanningBucket>
    ): Boolean {
        val existingByUuid = existingBuckets.associateBy { it.bucketUuid }
        val existingActiveCount = existingBuckets.count { !it.isDeleted }
        if (newDrafts.size != existingActiveCount) {
            return true
        }
        return newDrafts.any { draft ->
            val existing = existingByUuid[draft.bucketUuid] ?: return@any true
            existing.name != draft.name ||
                existing.trackingMode != draft.trackingMode ||
                existing.balanceBehavior != draft.balanceBehavior ||
                existing.defaultAllocatedAmountCents != draft.defaultAllocatedAmountCents ||
                existing.sortOrder != draft.sortOrder ||
                draft.closeRequested != existing.isClosed
        }
    }

    private fun validateNormalizedDrafts(
        request: SavePlanningRequest,
        context: PlanningContext,
        normalizedDrafts: List<PlanningDraft>,
        resolvedLeftoverReceiverBucketUuid: String?
    ): List<String> {
        val errors = mutableListOf<String>()
        if (request.portfolioMonthlyBudgetCents <= 0L) {
            errors += "Portfolio budget must be greater than zero."
        }

        val existingByUuid = context.buckets.associateBy { it.bucketUuid }
        normalizedDrafts.forEach { draft ->
            if (draft.name.isBlank()) {
                errors += "Bucket name cannot be blank."
            }
            if (draft.defaultAllocatedAmountCents < 0L) {
                errors += "Bucket allocation cannot be negative."
            }
            val existing = existingByUuid[draft.bucketUuid]
            val attemptsToReopenClosedBucket = existing != null &&
                !existing.isDeleted &&
                existing.isClosed &&
                !draft.closeRequested
            if (attemptsToReopenClosedBucket) {
                errors += "Closed buckets cannot be reopened."
            }
        }

        val openBuckets = normalizedDrafts.filterNot { it.closeRequested }
        if (openBuckets.isEmpty()) {
            errors += "At least one bucket must remain open."
        }
        val duplicateName = openBuckets
            .groupBy { it.name.trim().lowercase(Locale.getDefault()) }
            .values
            .firstOrNull { it.size > 1 }
        if (duplicateName != null) {
            errors += "Bucket names must be unique."
        }
        if (
            openBuckets
                .filterNot { it.bucketUuid == resolvedLeftoverReceiverBucketUuid }
                .sumOf { it.defaultAllocatedAmountCents } > request.portfolioMonthlyBudgetCents
        ) {
            errors += "Bucket allocations cannot exceed the portfolio budget."
        }
        return errors.distinct()
    }
}
