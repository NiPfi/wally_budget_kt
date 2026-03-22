package net.loeu.wallybudget.domain.model

const val DEFAULT_FUND_UUID = "00000000-0000-0000-0000-000000000002"
const val DEFAULT_FUND_NAME = "Savings"

data class Fund(
    val uuid: String,
    val name: String,
    val balanceCents: Long,
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
}
