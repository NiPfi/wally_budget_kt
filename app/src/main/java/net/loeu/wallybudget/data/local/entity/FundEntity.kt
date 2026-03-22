package net.loeu.wallybudget.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import net.loeu.wallybudget.domain.model.Fund

@Entity(
    tableName = "funds",
    indices = [
        Index(value = ["sortOrder"]),
        Index(value = ["closedAtEpochMs"]),
        Index(value = ["deletedAtEpochMs"])
    ]
)
data class FundEntity(
    @PrimaryKey
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
)

fun FundEntity.toDomainModel(): Fund {
    return Fund(
        uuid = uuid,
        name = name,
        balanceCents = balanceCents,
        allocationPerCycleCents = allocationPerCycleCents,
        targetAmountCents = targetAmountCents,
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

fun Fund.toEntity(): FundEntity {
    return FundEntity(
        uuid = uuid,
        name = name,
        balanceCents = balanceCents,
        allocationPerCycleCents = allocationPerCycleCents,
        targetAmountCents = targetAmountCents,
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
