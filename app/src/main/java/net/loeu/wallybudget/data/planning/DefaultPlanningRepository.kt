package net.loeu.wallybudget.data.planning

import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.domain.planning.PlanningRepository

class DefaultPlanningRepository(
    private val userSettingsStore: UserSettingsStore
) : PlanningRepository {

    override suspend fun persistPlanningSettings(
        leftoverReceiverBucketUuid: String?,
        selectedBucketUuid: String?
    ) {
        userSettingsStore.updateLeftoverReceiverBucket(leftoverReceiverBucketUuid)
        userSettingsStore.updateSelectedBucket(selectedBucketUuid)
    }
}
