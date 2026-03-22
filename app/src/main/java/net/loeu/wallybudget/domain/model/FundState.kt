package net.loeu.wallybudget.domain.model

data class FundState(
    val fund: Fund,
    val balanceCents: Long,
    val targetAmountCents: Long?,
    val progressPercent: Float?
)
