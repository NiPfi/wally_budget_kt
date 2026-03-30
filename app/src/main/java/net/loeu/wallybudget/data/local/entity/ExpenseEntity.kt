package net.loeu.wallybudget.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import net.loeu.wallybudget.data.time.WallyTime
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.ExpenseCategory
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID

/**
 * Represents a single expense entry
 */
@Entity(
    tableName = "expenses",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["expenseDate"]),
        Index(value = ["bucketUuid"]),
        Index(value = ["recordUuid"], unique = true),
        Index(value = ["deletedAtEpochMs"])
    ]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recordUuid: String,
    @ColumnInfo(defaultValue = "'$DEFAULT_SPENDING_BUCKET_UUID'")
    val bucketUuid: String = DEFAULT_SPENDING_BUCKET_UUID,
    val amountCents: Long,
    val description: String,
    val timestamp: Long = WallyTime.currentEpochTimeMs(),
    val expenseDate: String = WallyTime.localDateAtEpochTimeMs(timestamp).toString(),
    val icon: ExpenseCategory? = null,
    val originInstallId: String,
    val lastModifiedByInstallId: String,
    val createdAtEpochMs: Long = timestamp,
    val updatedAtEpochMs: Long = timestamp,
    val deletedAtEpochMs: Long? = null,
    val modClock: String
)

fun ExpenseEntity.toDomainModel(): Expense {
    return Expense(
        id = id,
        recordUuid = recordUuid,
        bucketUuid = bucketUuid,
        amountCents = amountCents,
        description = description,
        timestamp = timestamp,
        expenseDate = expenseDate,
        icon = icon,
        originInstallId = originInstallId,
        lastModifiedByInstallId = lastModifiedByInstallId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        deletedAtEpochMs = deletedAtEpochMs,
        modClock = modClock
    )
}

fun Expense.toEntity(): ExpenseEntity {
    return ExpenseEntity(
        id = id,
        recordUuid = recordUuid,
        bucketUuid = bucketUuid,
        amountCents = amountCents,
        description = description,
        timestamp = timestamp,
        expenseDate = expenseDate,
        icon = icon,
        originInstallId = originInstallId,
        lastModifiedByInstallId = lastModifiedByInstallId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        deletedAtEpochMs = deletedAtEpochMs,
        modClock = modClock
    )
}
