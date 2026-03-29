package net.loeu.wallybudget.data.local.preferences

import net.loeu.wallybudget.domain.model.BudgetAdjustment
import net.loeu.wallybudget.domain.model.BudgetPolicy
import net.loeu.wallybudget.domain.model.PendingPaydayUndo
import net.loeu.wallybudget.domain.model.UserSettings

internal fun UserPreferencesState.toDomainUserSettings(): UserSettings = settings.toDomain()

internal fun UserSettings.toStoredState(): StoredUserSettingsState {
    return StoredUserSettingsState(
        monthlyBudgetCents = monthlyBudgetCents,
        portfolioMonthlyBudgetCents = portfolioMonthlyBudgetCents,
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
}

internal fun StoredUserSettingsState.toDomain(): UserSettings {
    return UserSettings(
        monthlyBudgetCents = monthlyBudgetCents,
        portfolioMonthlyBudgetCents = portfolioMonthlyBudgetCents,
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
}

internal fun PendingPaydayUndo.toState(): PendingPaydayUndoState {
    return PendingPaydayUndoState(
        previousSettings = previousSettings.toStoredState(),
        policiesToRestore = policiesToRestore.map(BudgetPolicy::toState),
        policiesToDeactivate = policiesToDeactivate.map(BudgetPolicy::toState),
        adjustmentsToRestore = adjustmentsToRestore.map(BudgetAdjustment::toState),
        adjustmentsToDeactivate = adjustmentsToDeactivate.map(BudgetAdjustment::toState),
        bucketPoliciesToRestore = bucketPoliciesToRestore.map { it.toState() },
        bucketPoliciesToDeactivate = bucketPoliciesToDeactivate.map { it.toState() },
        bucketAdjustmentsToRestore = bucketAdjustmentsToRestore.map { it.toState() },
        bucketAdjustmentsToDeactivate = bucketAdjustmentsToDeactivate.map { it.toState() },
        expiresAtExclusive = expiresAtExclusive
    )
}

internal fun PendingPaydayUndoState.toDomain(): PendingPaydayUndo {
    return PendingPaydayUndo(
        previousSettings = previousSettings.toDomain(),
        policiesToRestore = policiesToRestore.map(BudgetPolicyState::toDomain),
        policiesToDeactivate = policiesToDeactivate.map(BudgetPolicyState::toDomain),
        adjustmentsToRestore = adjustmentsToRestore.map(BudgetAdjustmentState::toDomain),
        adjustmentsToDeactivate = adjustmentsToDeactivate.map(BudgetAdjustmentState::toDomain),
        bucketPoliciesToRestore = bucketPoliciesToRestore.map { it.toDomain() },
        bucketPoliciesToDeactivate = bucketPoliciesToDeactivate.map { it.toDomain() },
        bucketAdjustmentsToRestore = bucketAdjustmentsToRestore.map { it.toDomain() },
        bucketAdjustmentsToDeactivate = bucketAdjustmentsToDeactivate.map { it.toDomain() },
        expiresAtExclusive = expiresAtExclusive
    )
}

private fun BudgetPolicy.toState(): BudgetPolicyState {
    return BudgetPolicyState(
        policyUuid = policyUuid,
        cycleStartDate = cycleStartDate,
        cycleEndDateExclusive = cycleEndDateExclusive,
        budgetAmountCents = budgetAmountCents,
        paydayDayOfMonth = paydayDayOfMonth,
        originInstallId = originInstallId,
        lastModifiedByInstallId = lastModifiedByInstallId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        deletedAtEpochMs = deletedAtEpochMs,
        modClock = modClock
    )
}

private fun BudgetPolicyState.toDomain(): BudgetPolicy {
    return BudgetPolicy(
        policyUuid = policyUuid,
        cycleStartDate = cycleStartDate,
        cycleEndDateExclusive = cycleEndDateExclusive,
        budgetAmountCents = budgetAmountCents,
        paydayDayOfMonth = paydayDayOfMonth,
        originInstallId = originInstallId,
        lastModifiedByInstallId = lastModifiedByInstallId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        deletedAtEpochMs = deletedAtEpochMs,
        modClock = modClock
    )
}

private fun BudgetAdjustment.toState(): BudgetAdjustmentState {
    return BudgetAdjustmentState(
        adjustmentUuid = adjustmentUuid,
        cycleStartDate = cycleStartDate,
        effectiveDate = effectiveDate,
        previousMonthlyBudgetCents = previousMonthlyBudgetCents,
        newMonthlyBudgetCents = newMonthlyBudgetCents,
        originInstallId = originInstallId,
        lastModifiedByInstallId = lastModifiedByInstallId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        deletedAtEpochMs = deletedAtEpochMs,
        modClock = modClock
    )
}

private fun BudgetAdjustmentState.toDomain(): BudgetAdjustment {
    return BudgetAdjustment(
        adjustmentUuid = adjustmentUuid,
        cycleStartDate = cycleStartDate,
        effectiveDate = effectiveDate,
        previousMonthlyBudgetCents = previousMonthlyBudgetCents,
        newMonthlyBudgetCents = newMonthlyBudgetCents,
        originInstallId = originInstallId,
        lastModifiedByInstallId = lastModifiedByInstallId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        deletedAtEpochMs = deletedAtEpochMs,
        modClock = modClock
    )
}
