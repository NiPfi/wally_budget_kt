package net.loeu.wallybudget.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import net.loeu.wallybudget.R
import net.loeu.wallybudget.data.model.ExpenseIcon

object IconMapper {
    fun getIconRes(expenseIcon: ExpenseIcon?): Int {
        return when (expenseIcon) {
            ExpenseIcon.GROCERIES -> R.drawable.ic_shopping_cart
            ExpenseIcon.SHOPPING -> R.drawable.ic_shopping_bag
            ExpenseIcon.RESTAURANT -> R.drawable.ic_restaurant
            ExpenseIcon.TRANSPORT -> R.drawable.ic_transportation
            ExpenseIcon.CLOTHING -> R.drawable.ic_apparel
            ExpenseIcon.ENTERTAINMENT -> R.drawable.ic_movie
            ExpenseIcon.HOME -> R.drawable.ic_home
            ExpenseIcon.HEALTH -> R.drawable.ic_health
            ExpenseIcon.OTHER -> R.drawable.ic_more_horiz
            null -> R.drawable.ic_attach_money
        }
    }

    fun getDefaultDescription(icon: ExpenseIcon?): String {
        return when (icon) {
            ExpenseIcon.SHOPPING -> "Shopping"
            ExpenseIcon.RESTAURANT -> "Food"
            ExpenseIcon.TRANSPORT -> "Transport"
            ExpenseIcon.ENTERTAINMENT -> "Entertainment"
            ExpenseIcon.HOME -> "Home"
            ExpenseIcon.HEALTH -> "Health"
            ExpenseIcon.OTHER -> "Other"
            ExpenseIcon.CLOTHING -> "Clothing"
            ExpenseIcon.GROCERIES -> "Groceries"
            null -> "Expense"
        }
    }

    /**
     * Get all available expense icons
     */
    fun getAllIcons(): List<Pair<ExpenseIcon, Int>> {
        return ExpenseIcon.entries.map { it to getIconRes(it) }
    }
}

