package net.loeu.wallybudget.data.model

import androidx.annotation.DrawableRes
import net.loeu.wallybudget.R

/**
 * Null-safe access to the icon resource.
 * Safe from recursion because 'this?.iconRes' resolves to the member property when not null.
 */
val ExpenseCategory?.iconRes: Int
    @DrawableRes get() = this?.iconRes ?: R.drawable.ic_attach_money

/**
 * Null-safe access to the default description.
 */
val ExpenseCategory?.description: String
    get() = this?.description ?: "Expense"

/**
 * Optional icons for expenses using Material Icons
 */
enum class ExpenseCategory(
    @get:DrawableRes private val _iconRes: Int,
    private val _description: String
) {
    GROCERIES(R.drawable.ic_shopping_cart, "Groceries"),
    RESTAURANT(R.drawable.ic_restaurant, "Food"),
    TRANSPORT(R.drawable.ic_transportation, "Transport"),
    CLOTHING(R.drawable.ic_apparel, "Clothing"),
    SHOPPING(R.drawable.ic_shopping_bag, "Shopping"),
    ENTERTAINMENT(R.drawable.ic_movie, "Entertainment"),
    HOME(R.drawable.ic_home, "Home"),
    HEALTH(R.drawable.ic_health, "Health"),
    OTHER(R.drawable.ic_more_horiz, "Other");

    /**
     * Non-nullable member property.
     */
    @get:DrawableRes
    val iconRes: Int get() = _iconRes

    /**
     * Non-nullable member property.
     */
    val description: String get() = _description

    companion object {
        fun getAllIcons(): List<Pair<ExpenseCategory, Int>> {
            return entries.map { it to it.iconRes }
        }
    }
}
