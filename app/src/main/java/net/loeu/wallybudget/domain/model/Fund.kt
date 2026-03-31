package net.loeu.wallybudget.domain.model

const val DEFAULT_FUND_UUID = "00000000-0000-0000-0000-000000000002"
const val DEFAULT_FUND_NAME = "Savings"

data class Fund(
    val uuid: String,
    val name: String,
    val fundType: FundType,
    val balanceCents: Long,
    // Legacy field used only as the cycle-closeout deposit weight/base contribution.
    // It is not part of current-cycle planned budget totals.
    val allocationPerCycleCents: Long,
    val targetAmountCents: Long?,
    val sortOrder: Int,
    val originInstallId: String,
    val lastModifiedByInstallId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val closedAtEpochMs: Long? = null,
    val deletedAtEpochMs: Long? = null,
    val modClock: String
) {
    val isClosed: Boolean
        get() = closedAtEpochMs != null || deletedAtEpochMs != null

    val progressPercent: Float?
        get() = targetAmountCents?.takeIf { it > 0L }?.let { target ->
            ((balanceCents.toDouble() / target.toDouble()) * 100.0).coerceIn(0.0, 100.0).toFloat()
        }
}
