package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.dao.FundDao
import net.loeu.wallybudget.data.local.db.TransactionRunner
import net.loeu.wallybudget.data.local.entity.FundEntity
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.data.time.WallyTime
import net.loeu.wallybudget.domain.model.DEFAULT_FUND_NAME
import net.loeu.wallybudget.domain.model.DEFAULT_FUND_UUID
import net.loeu.wallybudget.domain.model.Fund
import net.loeu.wallybudget.domain.model.FundType
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import java.time.LocalDate

class EnsureDefaultFundUseCase(
    private val transactionRunner: TransactionRunner,
    private val userSettingsStore: UserSettingsStore,
    private val fundDao: FundDao,
    private val hybridLogicalClockService: HybridLogicalClockService
) {
    suspend operator fun invoke(now: LocalDate) {
        val settings = userSettingsStore.ensureIdentity()
        val installId = settings.installDeviceId
        val nowEpochMs = WallyTime.startOfDayEpochTimeMs(now)
        transactionRunner.inTransaction {
            val existing = fundDao.findByUuid(DEFAULT_FUND_UUID)
            when {
                existing == null -> {
                    fundDao.insert(
                        Fund(
                            uuid = DEFAULT_FUND_UUID,
                            name = DEFAULT_FUND_NAME,
                            fundType = FundType.DEFAULT_RESERVE,
                            balanceCents = 0L,
                            allocationPerCycleCents = 0L,
                            targetAmountCents = null,
                            sortOrder = 0,
                            originInstallId = installId,
                            lastModifiedByInstallId = installId,
                            createdAtEpochMs = nowEpochMs,
                            updatedAtEpochMs = nowEpochMs,
                            modClock = hybridLogicalClockService.format(nowEpochMs, 0, installId)
                        ).toEntity()
                    )
                }

                shouldNormalize(existing) -> {
                    val normalizedClock = if (existing.modClock.isBlank()) {
                        hybridLogicalClockService.format(nowEpochMs, 0, installId)
                    } else {
                        hybridLogicalClockService.next(existing.modClock, nowEpochMs, installId)
                    }
                    fundDao.update(
                        existing.copy(
                            name = DEFAULT_FUND_NAME,
                            fundType = FundType.DEFAULT_RESERVE,
                            closedAtEpochMs = null,
                            deletedAtEpochMs = null,
                            sortOrder = 0,
                            originInstallId = existing.originInstallId.ifBlank { installId },
                            updatedAtEpochMs = nowEpochMs,
                            lastModifiedByInstallId = installId,
                            modClock = normalizedClock
                        )
                    )
                }
            }
        }
    }

    private fun shouldNormalize(existing: FundEntity): Boolean {
        return existing.closedAtEpochMs != null ||
            existing.deletedAtEpochMs != null ||
            existing.name != DEFAULT_FUND_NAME ||
            existing.fundType != FundType.DEFAULT_RESERVE ||
            existing.sortOrder != 0 ||
            existing.originInstallId.isBlank() ||
            existing.lastModifiedByInstallId.isBlank() ||
            existing.modClock.isBlank()
    }
}
