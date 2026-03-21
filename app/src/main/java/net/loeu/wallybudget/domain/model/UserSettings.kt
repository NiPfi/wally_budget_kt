package net.loeu.wallybudget.domain.model

/**
 * User settings stored in DataStore
 */
data class UserSettings(
    val monthlyBudgetCents: Long = 0L,
    val portfolioMonthlyBudgetCents: Long? = null,
    val leftoverReceiverBucketUuid: String? = null,
    val paydayDate: Int = 1, // Day of month (1-31)
    val lastResetTimestamp: Long = 0L,
    val lastSeenDate: String? = null,
    val isOnboardingCompleted: Boolean = false,
    val pendingCycleStartDate: String? = null,
    val pendingCycleEndDateExclusive: String? = null,
    val pendingCycleDetectedAtTimestamp: Long = 0L,
    val selectedBucketUuid: String? = null,
    val installDeviceId: String = "",
    val settingsRecordUuid: String = "",
    val settingsUpdatedAtEpochMs: Long = 0L,
    val settingsModClock: String = "",
    val settingsLastModifiedByInstallId: String = ""
) {
    @Suppress("UNUSED_PARAMETER")
    constructor(
        monthlyBudgetCents: Long = 0L,
        portfolioMonthlyBudgetCents: Long? = null,
        leftoverReceiverBucketUuid: String? = null,
        paydayDate: Int = 1,
        lastResetTimestamp: Long = 0L,
        lastSeenDate: String? = null,
        isOnboardingCompleted: Boolean = false,
        pendingCycleStartDate: String? = null,
        pendingCycleEndDateExclusive: String? = null,
        pendingCycleDetectedAtTimestamp: Long = 0L,
        primaryBucketUuid: String? = null,
        selectedBucketUuid: String? = null,
        installDeviceId: String = "",
        settingsRecordUuid: String = "",
        settingsUpdatedAtEpochMs: Long = 0L,
        settingsModClock: String = "",
        settingsLastModifiedByInstallId: String = ""
    ) : this(
        monthlyBudgetCents = monthlyBudgetCents,
        portfolioMonthlyBudgetCents = portfolioMonthlyBudgetCents,
        leftoverReceiverBucketUuid = leftoverReceiverBucketUuid ?: primaryBucketUuid,
        paydayDate = paydayDate,
        lastResetTimestamp = lastResetTimestamp,
        lastSeenDate = lastSeenDate,
        isOnboardingCompleted = isOnboardingCompleted,
        pendingCycleStartDate = pendingCycleStartDate,
        pendingCycleEndDateExclusive = pendingCycleEndDateExclusive,
        pendingCycleDetectedAtTimestamp = pendingCycleDetectedAtTimestamp,
        selectedBucketUuid = selectedBucketUuid,
        installDeviceId = installDeviceId,
        settingsRecordUuid = settingsRecordUuid,
        settingsUpdatedAtEpochMs = settingsUpdatedAtEpochMs,
        settingsModClock = settingsModClock,
        settingsLastModifiedByInstallId = settingsLastModifiedByInstallId
    )

    val resolvedPortfolioMonthlyBudgetCents: Long
        get() = portfolioMonthlyBudgetCents ?: monthlyBudgetCents
}
