package net.loeu.wallybudget.data.local

import androidx.room.TypeConverter
import net.loeu.wallybudget.data.model.ExpenseIcon

class Converters {
    @TypeConverter
    fun fromExpenseIcon(icon: ExpenseIcon?): String? {
        return icon?.name
    }

    @TypeConverter
    fun toExpenseIcon(value: String?): ExpenseIcon? {
        return value?.let { ExpenseIcon.valueOf(it) }
    }
}

