package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.preferences.UserSettingsStore

class SelectBucketUseCase(
    private val userSettingsStore: UserSettingsStore
) {
    suspend operator fun invoke(bucketUuid: String) {
        userSettingsStore.ensureIdentity()
        userSettingsStore.updateSelectedBucket(bucketUuid)
    }
}
