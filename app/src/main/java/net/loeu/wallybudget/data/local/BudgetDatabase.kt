package net.loeu.wallybudget.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.loeu.wallybudget.data.model.Expense
import net.loeu.wallybudget.data.model.MonthlyHistory

@Database(
    entities = [Expense::class, MonthlyHistory::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class BudgetDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun monthlyHistoryDao(): MonthlyHistoryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `expenses_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `amountCents` INTEGER NOT NULL,
                        `description` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `icon` TEXT
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO `expenses_new` (`id`, `amountCents`, `description`, `timestamp`, `icon`)
                    SELECT `id`, CAST(ROUND(`amount` * 100.0) AS INTEGER), `description`, `timestamp`, `icon`
                    FROM `expenses`
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE `expenses`")
                db.execSQL("ALTER TABLE `expenses_new` RENAME TO `expenses`")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `monthly_history_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `year` INTEGER NOT NULL,
                        `month` INTEGER NOT NULL,
                        `budgetAmountCents` INTEGER NOT NULL,
                        `totalSpentCents` INTEGER NOT NULL,
                        `surplusCents` INTEGER NOT NULL,
                        `endTimestamp` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO `monthly_history_new` (
                        `id`, `year`, `month`, `budgetAmountCents`, `totalSpentCents`, `surplusCents`, `endTimestamp`
                    )
                    SELECT
                        `id`,
                        `year`,
                        `month`,
                        CAST(ROUND(`budgetAmount` * 100.0) AS INTEGER),
                        CAST(ROUND(`totalSpent` * 100.0) AS INTEGER),
                        CAST(ROUND(`surplus` * 100.0) AS INTEGER),
                        `endTimestamp`
                    FROM `monthly_history`
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE `monthly_history`")
                db.execSQL("ALTER TABLE `monthly_history_new` RENAME TO `monthly_history`")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_monthly_history_year_month` ON `monthly_history` (`year`, `month`)"
                )
            }
        }
    }
}

