package net.loeu.wallybudget.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import net.loeu.wallybudget.domain.model.FundTransactionType

@Entity(
    tableName = "fund_transactions",
    foreignKeys = [
        ForeignKey(
            entity = FundEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["fundUuid"]
        )
    ],
    indices = [
        Index(value = ["fundUuid"]),
        Index(value = ["dateEpochMs"])
    ]
)
data class FundTransactionEntity(
    @PrimaryKey
    val uuid: String,
    val fundUuid: String,
    val amountCents: Long,
    val type: FundTransactionType,
    val description: String,
    val dateEpochMs: Long
)
