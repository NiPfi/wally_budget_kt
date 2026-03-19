package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.preferences.UserSettingsStore

class SelectBucketUseCase(
    private val userSettingsStore: UserSettingsStore
) {
    suspend operator fun invoke(bucketUuid: String) {
        val settings = userSettingsStore.ensureIdentity()
        userSettingsStore.updateBucketSelection(
            primaryBucketUuid = settings.primaryBucketUuid,
            selectedBucketUuid = bucketUuid
        )
    }
}
