package net.loeu.wallybudget.ui.planning

import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.BucketBalanceBehavior
import net.loeu.wallybudget.domain.model.BucketSummaryState
import net.loeu.wallybudget.domain.model.BucketTrackingMode
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.planning.PlanningConfig
import net.loeu.wallybudget.domain.planning.PlanningContext
import net.loeu.wallybudget.domain.planning.PlanningDraft
import net.loeu.wallybudget.domain.planning.PlanningEngine
import net.loeu.wallybudget.domain.planning.SavePlanningRequest
import net.loeu.wallybudget.domain.planning.toPlanningBucket
import net.loeu.wallybudget.util.CurrencyFormatter

data class PlanningBucketEditorRow(
    val bucketUuid: String,
    val name: String,
    val trackingMode: BucketTrackingMode,
    val balanceBehavior: BucketBalanceBehavior,
    val amountText: String,
    val sortOrder: Int,
    val closeRequested: Boolean,
    val existingClosed: Boolean,
    val isComputedRemainder: Boolean = false
)

data class PlanningEditorState(
    val budgetText: String,
    val leftoverReceiverBucketUuid: String?,
    val bucketRows: List<PlanningBucketEditorRow>
)

private val engine = PlanningEngine()

fun buildPlanningContext(
    userSettings: UserSettings,
    allBuckets: List<BudgetBucket>
): PlanningContext {
    return PlanningContext(
        config = PlanningConfig(
            portfolioMonthlyBudgetCents = userSettings.resolvedPortfolioMonthlyBudgetCents,
            leftoverReceiverBucketUuid = userSettings.leftoverReceiverBucketUuid
        ),
        buckets = allBuckets.map { it.toPlanningBucket() },
        selectedBucketUuid = userSettings.selectedBucketUuid
    )
}

fun buildExistingPlanningRequest(
    userSettings: UserSettings,
    allBuckets: List<BudgetBucket>,
    bucketSummaries: List<BucketSummaryState>
): SavePlanningRequest {
    val summaryByBucketUuid = bucketSummaries.associateBy { it.bucket.bucketUuid }
    return SavePlanningRequest(
        portfolioMonthlyBudgetCents = userSettings.resolvedPortfolioMonthlyBudgetCents,
        leftoverReceiverBucketUuid = userSettings.leftoverReceiverBucketUuid,
        buckets = allBuckets
            .sortedWith(compareBy<BudgetBucket> { it.sortOrder }.thenBy { it.createdAtEpochMs })
            .map { bucket ->
                PlanningDraft(
                    bucketUuid = bucket.bucketUuid,
                    name = bucket.name,
                    trackingMode = bucket.trackingMode,
                    balanceBehavior = bucket.balanceBehavior,
                    defaultAllocatedAmountCents = summaryByBucketUuid[bucket.bucketUuid]?.allocatedThisCycleCents
                        ?: bucket.defaultAllocatedAmountCents,
                    sortOrder = bucket.sortOrder,
                    closeRequested = bucket.isClosed
                )
            }
    )
}

fun buildPlanningEditorState(
    userSettings: UserSettings,
    allBuckets: List<BudgetBucket>,
    request: SavePlanningRequest
): PlanningEditorState {
    val state = engine.normalize(
        context = buildPlanningContext(userSettings = userSettings, allBuckets = allBuckets),
        request = request
    )
    val existingClosedByBucketUuid = allBuckets.associate { it.bucketUuid to it.isClosed }
    return PlanningEditorState(
        budgetText = CurrencyFormatter.centsToDecimalString(request.portfolioMonthlyBudgetCents),
        leftoverReceiverBucketUuid = state.resolvedLeftoverReceiverBucketUuid,
        bucketRows = state.buckets.map { bucket ->
            PlanningBucketEditorRow(
                bucketUuid = bucket.draft.bucketUuid,
                name = bucket.draft.name,
                trackingMode = bucket.draft.trackingMode,
                balanceBehavior = bucket.draft.balanceBehavior,
                amountText = CurrencyFormatter.centsToDecimalString(bucket.draft.defaultAllocatedAmountCents),
                sortOrder = bucket.draft.sortOrder,
                closeRequested = bucket.draft.closeRequested,
                existingClosed = existingClosedByBucketUuid[bucket.draft.bucketUuid] == true,
                isComputedRemainder = bucket.isComputedRemainder
            )
        }
    )
}

fun planningEditorRowsToDrafts(rows: List<PlanningBucketEditorRow>): List<PlanningDraft>? {
    val drafts = rows.mapNotNull { bucket ->
        val amount = CurrencyFormatter.parseAmountToCents(bucket.amountText) ?: return null
        PlanningDraft(
            bucketUuid = bucket.bucketUuid,
            name = bucket.name.trim(),
            trackingMode = bucket.trackingMode,
            balanceBehavior = bucket.balanceBehavior,
            defaultAllocatedAmountCents = amount,
            sortOrder = bucket.sortOrder,
            closeRequested = bucket.closeRequested
        )
    }
    return drafts
}

fun planningDraftsMatch(
    current: PlanningEditorState,
    external: PlanningEditorState,
    currentPaydayText: String,
    externalPaydayText: String
): Boolean {
    return current == external && currentPaydayText == externalPaydayText
}
