package net.loeu.wallybudget.data.local.db

import androidx.room.TypeConverter
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
}
