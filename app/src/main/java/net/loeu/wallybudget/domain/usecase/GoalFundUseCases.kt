package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.dao.FundDao
import net.loeu.wallybudget.data.local.entity.FundEntity
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.data.time.WallyTime
import net.loeu.wallybudget.domain.model.FundType
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import java.util.UUID

data class CreateGoalFundRequest(
    val name: String,
    val targetAmountCents: Long
)

data class UpdateGoalFundRequest(
    val fundUuid: String,
    val name: String,
    val targetAmountCents: Long
)

private fun validateGoalFundRequest(name: String, targetAmountCents: Long) {
    require(name.isNotBlank()) { "Goal name cannot be blank." }
    require(targetAmountCents > 0L) { "Goal target must be greater than zero." }
}

private fun FundEntity.requireEditableGoal(): FundEntity {
    require(fundType == FundType.GOAL) { "Only goal funds can be edited." }
    require(deletedAtEpochMs == null && closedAtEpochMs == null) { "Only active goal funds can be edited." }
    return this
}

class CreateGoalFundUseCase(
    private val fundDao: FundDao,
    private val userSettingsStore: UserSettingsStore,
    private val hybridLogicalClockService: HybridLogicalClockService
) {
    suspend operator fun invoke(request: CreateGoalFundRequest): String {
        val trimmedName = request.name.trim()
        validateGoalFundRequest(trimmedName, request.targetAmountCents)

        val settings = userSettingsStore.ensureIdentity()
        val installId = settings.installDeviceId
        val now = WallyTime.currentEpochTimeMs()
        val nextSortOrder = (fundDao.getAllActive().maxOfOrNull { it.sortOrder } ?: -1) + 1
        val fundUuid = UUID.randomUUID().toString()
        fundDao.insert(
            FundEntity(
                uuid = fundUuid,
                name = trimmedName,
                fundType = FundType.GOAL,
                balanceCents = 0L,
                allocationPerCycleCents = 0L,
                targetAmountCents = request.targetAmountCents,
                sortOrder = nextSortOrder,
                originInstallId = installId,
                lastModifiedByInstallId = installId,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                modClock = hybridLogicalClockService.next(null, now, installId)
            )
        )
        return fundUuid
    }
}

class UpdateGoalFundUseCase(
    private val fundDao: FundDao,
    private val userSettingsStore: UserSettingsStore,
    private val hybridLogicalClockService: HybridLogicalClockService
) {
    suspend operator fun invoke(request: UpdateGoalFundRequest) {
        val trimmedName = request.name.trim()
        validateGoalFundRequest(trimmedName, request.targetAmountCents)

        val existing = fundDao.findByUuid(request.fundUuid)?.requireEditableGoal()
            ?: throw IllegalArgumentException("Goal fund not found.")

        val settings = userSettingsStore.ensureIdentity()
        val installId = settings.installDeviceId
        val now = WallyTime.currentEpochTimeMs()
        fundDao.update(
            existing.copy(
                name = trimmedName,
                targetAmountCents = request.targetAmountCents,
                updatedAtEpochMs = now,
                lastModifiedByInstallId = installId,
                modClock = hybridLogicalClockService.next(existing.modClock, now, installId)
            )
        )
    }
}
