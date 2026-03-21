package net.loeu.wallybudget.domain.planning

interface PlanningRepository {
    suspend fun persistPlanningSettings(
        leftoverReceiverBucketUuid: String?,
        selectedBucketUuid: String?
    )
}
