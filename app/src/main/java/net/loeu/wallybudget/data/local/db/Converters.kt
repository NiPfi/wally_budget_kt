package net.loeu.wallybudget.data.local.db

import androidx.room.TypeConverter
import net.loeu.wallybudget.domain.model.BucketTransferReason
import net.loeu.wallybudget.domain.model.FundTransactionType
import net.loeu.wallybudget.domain.model.ExpenseCategory

enum class LegacyBucketTrackingMode {
    DAILY_TARGET,
    CYCLE_RESERVE
}

enum class LegacyBucketBalanceBehavior {
    RETURN_TO_PORTFOLIO,
    RETAIN_IN_BUCKET
}

class Converters {
    @TypeConverter
    fun fromExpenseIcon(icon: ExpenseCategory?): String? {
        return icon?.name
    }

    @TypeConverter
    fun toExpenseIcon(value: String?): ExpenseCategory? {
        return value?.let { ExpenseCategory.valueOf(it) }
    }

    @TypeConverter
    fun fromBucketTrackingMode(value: LegacyBucketTrackingMode?): String? {
        return value?.name
    }

    @TypeConverter
    fun toBucketTrackingMode(value: String?): LegacyBucketTrackingMode? {
        return value?.let { LegacyBucketTrackingMode.valueOf(it) }
    }

    @TypeConverter
    fun fromBucketBalanceBehavior(value: LegacyBucketBalanceBehavior?): String? {
        return value?.name
    }

    @TypeConverter
    fun toBucketBalanceBehavior(value: String?): LegacyBucketBalanceBehavior? {
        return value?.let { LegacyBucketBalanceBehavior.valueOf(it) }
    }

    @TypeConverter
    fun fromFundTransactionType(value: FundTransactionType?): String? {
        return value?.name
    }

    @TypeConverter
    fun toFundTransactionType(value: String?): FundTransactionType? {
        return value?.let { FundTransactionType.valueOf(it) }
    }

    @TypeConverter
    fun fromBucketTransferReason(value: BucketTransferReason?): String? {
        return value?.name
    }

    @TypeConverter
    fun toBucketTransferReason(value: String?): BucketTransferReason? {
        return value?.let { BucketTransferReason.valueOf(it) }
    }
}
