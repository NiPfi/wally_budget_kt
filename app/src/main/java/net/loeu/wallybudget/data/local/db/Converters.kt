package net.loeu.wallybudget.data.local.db

import androidx.room.TypeConverter
import net.loeu.wallybudget.domain.model.BucketBalanceBehavior
import net.loeu.wallybudget.domain.model.BucketTrackingMode
import net.loeu.wallybudget.domain.model.ExpenseCategory

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
    fun fromBucketTrackingMode(value: BucketTrackingMode?): String? {
        return value?.name
    }

    @TypeConverter
    fun toBucketTrackingMode(value: String?): BucketTrackingMode? {
        return value?.let { BucketTrackingMode.valueOf(it) }
    }

    @TypeConverter
    fun fromBucketBalanceBehavior(value: BucketBalanceBehavior?): String? {
        return value?.name
    }

    @TypeConverter
    fun toBucketBalanceBehavior(value: String?): BucketBalanceBehavior? {
        return value?.let { BucketBalanceBehavior.valueOf(it) }
    }
}
