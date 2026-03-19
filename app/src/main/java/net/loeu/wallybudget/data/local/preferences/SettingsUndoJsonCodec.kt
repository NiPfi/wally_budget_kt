package net.loeu.wallybudget.data.local.preferences

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonIOException
import com.google.gson.JsonParseException
import com.google.gson.JsonSyntaxException
import net.loeu.wallybudget.domain.model.BudgetAdjustment
import net.loeu.wallybudget.domain.model.BucketAllocationAdjustment
import net.loeu.wallybudget.domain.model.BucketAllocationPolicy
import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.BudgetPolicy
import net.loeu.wallybudget.domain.model.PendingSettingsUndo
import net.loeu.wallybudget.domain.model.UserSettings

class SettingsUndoJsonCodec(
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
) {
    fun encode(pendingSettingsUndo: PendingSettingsUndo): String =
        gson.toJson(PendingSettingsUndoPayload.fromDomain(pendingSettingsUndo))

    fun decodeOrNull(input: String): PendingSettingsUndo? {
        return try {
            gson.fromJson(input, PendingSettingsUndoPayload::class.java)?.toDomain()
        } catch (_: JsonSyntaxException) {
            null
        } catch (_: JsonParseException) {
            null
        } catch (_: JsonIOException) {
            null
        } catch (_: IllegalStateException) {
            null
        }
    }
}

private data class PendingSettingsUndoPayload(
    val previousSettings: UserSettingsPayload? = null,
    val bucketsToRestore: List<BudgetBucketPayload> = emptyList(),
    val bucketsToDeactivate: List<BudgetBucketPayload> = emptyList(),
    val policiesToRestore: List<BudgetPolicyPayload> = emptyList(),
    val policiesToDeactivate: List<BudgetPolicyPayload> = emptyList(),
    val adjustmentsToRestore: List<BudgetAdjustmentPayload> = emptyList(),
    val adjustmentsToDeactivate: List<BudgetAdjustmentPayload> = emptyList(),
    val bucketPoliciesToRestore: List<BucketAllocationPolicyPayload> = emptyList(),
    val bucketPoliciesToDeactivate: List<BucketAllocationPolicyPayload> = emptyList(),
    val bucketAdjustmentsToRestore: List<BucketAllocationAdjustmentPayload> = emptyList(),
    val bucketAdjustmentsToDeactivate: List<BucketAllocationAdjustmentPayload> = emptyList(),
    val expiresAtExclusive: String? = null
) {
    fun toDomain(): PendingSettingsUndo {
        val previousSettingsValue = requireNotNull(previousSettings) { "Missing previousSettings" }
        val expiresAtExclusiveValue = requireNotNull(expiresAtExclusive) { "Missing expiresAtExclusive" }
        return PendingSettingsUndo(
            previousSettings = previousSettingsValue.toDomain(),
            bucketsToRestore = bucketsToRestore.map(BudgetBucketPayload::toDomain),
            bucketsToDeactivate = bucketsToDeactivate.map(BudgetBucketPayload::toDomain),
            policiesToRestore = policiesToRestore.map(BudgetPolicyPayload::toDomain),
            policiesToDeactivate = policiesToDeactivate.map(BudgetPolicyPayload::toDomain),
            adjustmentsToRestore = adjustmentsToRestore.map(BudgetAdjustmentPayload::toDomain),
            adjustmentsToDeactivate = adjustmentsToDeactivate.map(BudgetAdjustmentPayload::toDomain),
            bucketPoliciesToRestore = bucketPoliciesToRestore.map(BucketAllocationPolicyPayload::toDomain),
            bucketPoliciesToDeactivate = bucketPoliciesToDeactivate.map(BucketAllocationPolicyPayload::toDomain),
            bucketAdjustmentsToRestore = bucketAdjustmentsToRestore.map(BucketAllocationAdjustmentPayload::toDomain),
            bucketAdjustmentsToDeactivate = bucketAdjustmentsToDeactivate.map(
                BucketAllocationAdjustmentPayload::toDomain
            ),
            expiresAtExclusive = expiresAtExclusiveValue
        )
    }

    companion object {
        fun fromDomain(pendingSettingsUndo: PendingSettingsUndo): PendingSettingsUndoPayload {
            return PendingSettingsUndoPayload(
                previousSettings = UserSettingsPayload.fromDomain(pendingSettingsUndo.previousSettings),
                bucketsToRestore = pendingSettingsUndo.bucketsToRestore.map(BudgetBucketPayload::fromDomain),
                bucketsToDeactivate = pendingSettingsUndo.bucketsToDeactivate.map(BudgetBucketPayload::fromDomain),
                policiesToRestore = pendingSettingsUndo.policiesToRestore.map(BudgetPolicyPayload::fromDomain),
                policiesToDeactivate = pendingSettingsUndo.policiesToDeactivate.map(BudgetPolicyPayload::fromDomain),
                adjustmentsToRestore = pendingSettingsUndo.adjustmentsToRestore
                    .map(BudgetAdjustmentPayload::fromDomain),
                adjustmentsToDeactivate = pendingSettingsUndo.adjustmentsToDeactivate
                    .map(BudgetAdjustmentPayload::fromDomain),
                bucketPoliciesToRestore = pendingSettingsUndo.bucketPoliciesToRestore
                    .map(BucketAllocationPolicyPayload::fromDomain),
                bucketPoliciesToDeactivate = pendingSettingsUndo.bucketPoliciesToDeactivate
                    .map(BucketAllocationPolicyPayload::fromDomain),
                bucketAdjustmentsToRestore = pendingSettingsUndo.bucketAdjustmentsToRestore
                    .map(BucketAllocationAdjustmentPayload::fromDomain),
                bucketAdjustmentsToDeactivate = pendingSettingsUndo.bucketAdjustmentsToDeactivate
                    .map(BucketAllocationAdjustmentPayload::fromDomain),
                expiresAtExclusive = pendingSettingsUndo.expiresAtExclusive
            )
        }
    }
}

private data class UserSettingsPayload(
    val monthlyBudgetCents: Long = 0L,
    val portfolioMonthlyBudgetCents: Long? = null,
    val paydayDate: Int = 1,
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
    fun toDomain(): UserSettings {
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

    companion object {
        fun fromDomain(userSettings: UserSettings): UserSettingsPayload {
            return UserSettingsPayload(
                monthlyBudgetCents = userSettings.monthlyBudgetCents,
                portfolioMonthlyBudgetCents = userSettings.portfolioMonthlyBudgetCents,
                paydayDate = userSettings.paydayDate,
                lastResetTimestamp = userSettings.lastResetTimestamp,
                lastSeenDate = userSettings.lastSeenDate,
                isOnboardingCompleted = userSettings.isOnboardingCompleted,
                pendingCycleStartDate = userSettings.pendingCycleStartDate,
                pendingCycleEndDateExclusive = userSettings.pendingCycleEndDateExclusive,
                pendingCycleDetectedAtTimestamp = userSettings.pendingCycleDetectedAtTimestamp,
                selectedBucketUuid = userSettings.selectedBucketUuid,
                installDeviceId = userSettings.installDeviceId,
                settingsRecordUuid = userSettings.settingsRecordUuid,
                settingsUpdatedAtEpochMs = userSettings.settingsUpdatedAtEpochMs,
                settingsModClock = userSettings.settingsModClock,
                settingsLastModifiedByInstallId = userSettings.settingsLastModifiedByInstallId
            )
        }
    }
}

private data class BudgetBucketPayload(
    val bucketUuid: String? = null,
    val name: String? = null,
    val trackingMode: String? = null,
    val balanceBehavior: String? = null,
    val defaultAllocatedAmountCents: Long = 0L,
    val sortOrder: Int = 0,
    val originInstallId: String = "",
    val lastModifiedByInstallId: String = "",
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
    val closedAtEpochMs: Long? = null,
    val deletedAtEpochMs: Long? = null,
    val modClock: String = ""
) {
    fun toDomain(): BudgetBucket {
        return BudgetBucket(
            bucketUuid = requireNotNull(bucketUuid) { "Missing bucketUuid" },
            name = requireNotNull(name) { "Missing name" },
            trackingMode = net.loeu.wallybudget.domain.model.BucketTrackingMode.valueOf(
                requireNotNull(trackingMode) { "Missing trackingMode" }
            ),
            balanceBehavior = net.loeu.wallybudget.domain.model.BucketBalanceBehavior.valueOf(
                requireNotNull(balanceBehavior) { "Missing balanceBehavior" }
            ),
            defaultAllocatedAmountCents = defaultAllocatedAmountCents,
            sortOrder = sortOrder,
            originInstallId = originInstallId,
            lastModifiedByInstallId = lastModifiedByInstallId,
            createdAtEpochMs = createdAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs,
            closedAtEpochMs = closedAtEpochMs,
            deletedAtEpochMs = deletedAtEpochMs,
            modClock = modClock
        )
    }

    companion object {
        fun fromDomain(bucket: BudgetBucket): BudgetBucketPayload {
            return BudgetBucketPayload(
                bucketUuid = bucket.bucketUuid,
                name = bucket.name,
                trackingMode = bucket.trackingMode.name,
                balanceBehavior = bucket.balanceBehavior.name,
                defaultAllocatedAmountCents = bucket.defaultAllocatedAmountCents,
                sortOrder = bucket.sortOrder,
                originInstallId = bucket.originInstallId,
                lastModifiedByInstallId = bucket.lastModifiedByInstallId,
                createdAtEpochMs = bucket.createdAtEpochMs,
                updatedAtEpochMs = bucket.updatedAtEpochMs,
                closedAtEpochMs = bucket.closedAtEpochMs,
                deletedAtEpochMs = bucket.deletedAtEpochMs,
                modClock = bucket.modClock
            )
        }
    }
}

private data class BudgetPolicyPayload(
    val policyUuid: String? = null,
    val cycleStartDate: String? = null,
    val cycleEndDateExclusive: String? = null,
    val budgetAmountCents: Long = 0L,
    val paydayDayOfMonth: Int = 1,
    val originInstallId: String = "",
    val lastModifiedByInstallId: String = "",
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
    val deletedAtEpochMs: Long? = null,
    val modClock: String = ""
) {
    fun toDomain(): BudgetPolicy {
        return BudgetPolicy(
            policyUuid = requireNotNull(policyUuid) { "Missing policyUuid" },
            cycleStartDate = requireNotNull(cycleStartDate) { "Missing cycleStartDate" },
            cycleEndDateExclusive = requireNotNull(cycleEndDateExclusive) { "Missing cycleEndDateExclusive" },
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

    companion object {
        fun fromDomain(budgetPolicy: BudgetPolicy): BudgetPolicyPayload {
            return BudgetPolicyPayload(
                policyUuid = budgetPolicy.policyUuid,
                cycleStartDate = budgetPolicy.cycleStartDate,
                cycleEndDateExclusive = budgetPolicy.cycleEndDateExclusive,
                budgetAmountCents = budgetPolicy.budgetAmountCents,
                paydayDayOfMonth = budgetPolicy.paydayDayOfMonth,
                originInstallId = budgetPolicy.originInstallId,
                lastModifiedByInstallId = budgetPolicy.lastModifiedByInstallId,
                createdAtEpochMs = budgetPolicy.createdAtEpochMs,
                updatedAtEpochMs = budgetPolicy.updatedAtEpochMs,
                deletedAtEpochMs = budgetPolicy.deletedAtEpochMs,
                modClock = budgetPolicy.modClock
            )
        }
    }
}

private data class BudgetAdjustmentPayload(
    val adjustmentUuid: String? = null,
    val cycleStartDate: String? = null,
    val effectiveDate: String? = null,
    val previousMonthlyBudgetCents: Long = 0L,
    val newMonthlyBudgetCents: Long = 0L,
    val originInstallId: String = "",
    val lastModifiedByInstallId: String = "",
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
    val deletedAtEpochMs: Long? = null,
    val modClock: String = ""
) {
    fun toDomain(): BudgetAdjustment {
        return BudgetAdjustment(
            adjustmentUuid = requireNotNull(adjustmentUuid) { "Missing adjustmentUuid" },
            cycleStartDate = requireNotNull(cycleStartDate) { "Missing cycleStartDate" },
            effectiveDate = requireNotNull(effectiveDate) { "Missing effectiveDate" },
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

    companion object {
        fun fromDomain(budgetAdjustment: BudgetAdjustment): BudgetAdjustmentPayload {
            return BudgetAdjustmentPayload(
                adjustmentUuid = budgetAdjustment.adjustmentUuid,
                cycleStartDate = budgetAdjustment.cycleStartDate,
                effectiveDate = budgetAdjustment.effectiveDate,
                previousMonthlyBudgetCents = budgetAdjustment.previousMonthlyBudgetCents,
                newMonthlyBudgetCents = budgetAdjustment.newMonthlyBudgetCents,
                originInstallId = budgetAdjustment.originInstallId,
                lastModifiedByInstallId = budgetAdjustment.lastModifiedByInstallId,
                createdAtEpochMs = budgetAdjustment.createdAtEpochMs,
                updatedAtEpochMs = budgetAdjustment.updatedAtEpochMs,
                deletedAtEpochMs = budgetAdjustment.deletedAtEpochMs,
                modClock = budgetAdjustment.modClock
            )
        }
    }
}

private data class BucketAllocationPolicyPayload(
    val allocationUuid: String? = null,
    val bucketUuid: String? = null,
    val cycleStartDate: String? = null,
    val cycleEndDateExclusive: String? = null,
    val allocatedAmountCents: Long = 0L,
    val originInstallId: String = "",
    val lastModifiedByInstallId: String = "",
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
    val deletedAtEpochMs: Long? = null,
    val modClock: String = ""
) {
    fun toDomain(): BucketAllocationPolicy {
        return BucketAllocationPolicy(
            allocationUuid = requireNotNull(allocationUuid) { "Missing allocationUuid" },
            bucketUuid = requireNotNull(bucketUuid) { "Missing bucketUuid" },
            cycleStartDate = requireNotNull(cycleStartDate) { "Missing cycleStartDate" },
            cycleEndDateExclusive = requireNotNull(cycleEndDateExclusive) { "Missing cycleEndDateExclusive" },
            allocatedAmountCents = allocatedAmountCents,
            originInstallId = originInstallId,
            lastModifiedByInstallId = lastModifiedByInstallId,
            createdAtEpochMs = createdAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs,
            deletedAtEpochMs = deletedAtEpochMs,
            modClock = modClock
        )
    }

    companion object {
        fun fromDomain(policy: BucketAllocationPolicy): BucketAllocationPolicyPayload {
            return BucketAllocationPolicyPayload(
                allocationUuid = policy.allocationUuid,
                bucketUuid = policy.bucketUuid,
                cycleStartDate = policy.cycleStartDate,
                cycleEndDateExclusive = policy.cycleEndDateExclusive,
                allocatedAmountCents = policy.allocatedAmountCents,
                originInstallId = policy.originInstallId,
                lastModifiedByInstallId = policy.lastModifiedByInstallId,
                createdAtEpochMs = policy.createdAtEpochMs,
                updatedAtEpochMs = policy.updatedAtEpochMs,
                deletedAtEpochMs = policy.deletedAtEpochMs,
                modClock = policy.modClock
            )
        }
    }
}

private data class BucketAllocationAdjustmentPayload(
    val adjustmentUuid: String? = null,
    val bucketUuid: String? = null,
    val cycleStartDate: String? = null,
    val effectiveDate: String? = null,
    val previousAllocatedAmountCents: Long = 0L,
    val newAllocatedAmountCents: Long = 0L,
    val originInstallId: String = "",
    val lastModifiedByInstallId: String = "",
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
    val deletedAtEpochMs: Long? = null,
    val modClock: String = ""
) {
    fun toDomain(): BucketAllocationAdjustment {
        return BucketAllocationAdjustment(
            adjustmentUuid = requireNotNull(adjustmentUuid) { "Missing adjustmentUuid" },
            bucketUuid = requireNotNull(bucketUuid) { "Missing bucketUuid" },
            cycleStartDate = requireNotNull(cycleStartDate) { "Missing cycleStartDate" },
            effectiveDate = requireNotNull(effectiveDate) { "Missing effectiveDate" },
            previousAllocatedAmountCents = previousAllocatedAmountCents,
            newAllocatedAmountCents = newAllocatedAmountCents,
            originInstallId = originInstallId,
            lastModifiedByInstallId = lastModifiedByInstallId,
            createdAtEpochMs = createdAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs,
            deletedAtEpochMs = deletedAtEpochMs,
            modClock = modClock
        )
    }

    companion object {
        fun fromDomain(adjustment: BucketAllocationAdjustment): BucketAllocationAdjustmentPayload {
            return BucketAllocationAdjustmentPayload(
                adjustmentUuid = adjustment.adjustmentUuid,
                bucketUuid = adjustment.bucketUuid,
                cycleStartDate = adjustment.cycleStartDate,
                effectiveDate = adjustment.effectiveDate,
                previousAllocatedAmountCents = adjustment.previousAllocatedAmountCents,
                newAllocatedAmountCents = adjustment.newAllocatedAmountCents,
                originInstallId = adjustment.originInstallId,
                lastModifiedByInstallId = adjustment.lastModifiedByInstallId,
                createdAtEpochMs = adjustment.createdAtEpochMs,
                updatedAtEpochMs = adjustment.updatedAtEpochMs,
                deletedAtEpochMs = adjustment.deletedAtEpochMs,
                modClock = adjustment.modClock
            )
        }
    }
}
