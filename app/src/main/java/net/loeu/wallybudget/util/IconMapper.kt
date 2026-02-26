package net.loeu.wallybudget.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import net.loeu.wallybudget.data.model.ExpenseIcon

object IconMapper {

    /**
     * Get Material Icon for expense icon type
     */
    fun getIcon(expenseIcon: ExpenseIcon?): ImageVector {
        return when (expenseIcon) {
            ExpenseIcon.SHOPPING -> Icons.Default.ShoppingCart
            ExpenseIcon.RESTAURANT -> Icons.Default.Restaurant
            ExpenseIcon.TRANSPORT -> Icons.Default.DirectionsCar
            ExpenseIcon.ENTERTAINMENT -> Icons.Default.Movie
            ExpenseIcon.HOME -> Icons.Default.Home
            ExpenseIcon.HEALTH -> Icons.Default.LocalHospital
            ExpenseIcon.OTHER -> Icons.Default.MoreHoriz
            null -> Icons.Default.AttachMoney
        }
    }

    /**
     * Get all available expense icons
     */
    fun getAllIcons(): List<Pair<ExpenseIcon, ImageVector>> {
        return ExpenseIcon.entries.map { it to getIcon(it) }
    }
}

