package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.loeu.wallybudget.data.local.dao.FundDao
import net.loeu.wallybudget.data.local.entity.toDomainModel
import net.loeu.wallybudget.domain.model.FundState

class ObserveFundsUseCase(
    private val fundDao: FundDao
) {
    operator fun invoke(): Flow<List<FundState>> {
        return fundDao.observeAllActive().map { entries ->
            entries.map { entry ->
                val fund = entry.toDomainModel()
                val progressPercent = fund.targetAmountCents?.takeIf { it > 0L }?.let { target ->
                    ((fund.balanceCents.toDouble() / target.toDouble()) * 100.0)
                        .coerceIn(0.0, 100.0)
                        .toFloat()
                }
                FundState(
                    fund = fund,
                    balanceCents = fund.balanceCents,
                    targetAmountCents = fund.targetAmountCents,
                    progressPercent = progressPercent
                )
            }
        }
    }
}
