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
    version = 6,
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
                    INSERT OR IGNORE INTO `monthly_history_new` (
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE `monthly_history_new` (
                        `cycleStartDate` TEXT NOT NULL,
                        `year` INTEGER NOT NULL,
                        `month` INTEGER NOT NULL,
                        `budgetAmountCents` INTEGER NOT NULL,
                        `totalSpentCents` INTEGER NOT NULL,
                        `surplusCents` INTEGER NOT NULL,
                        `cycleEndDate` TEXT NOT NULL,
                        `endTimestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`cycleStartDate`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `monthly_history_new` (
                        `cycleStartDate`, `year`, `month`, `budgetAmountCents`, `totalSpentCents`, `surplusCents`, `cycleEndDate`, `endTimestamp`
                    )
                    SELECT 
                        date(endTimestamp / 1000, 'unixepoch', '-1 month') as cycleStartDate,
                        `year`, 
                        `month`, 
                        `budgetAmountCents`, 
                        `totalSpentCents`, 
                        `surplusCents`,
                        date(endTimestamp / 1000, 'unixepoch') as cycleEndDate,
                        `endTimestamp`
                    FROM `monthly_history`
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE `monthly_history`")
                db.execSQL("ALTER TABLE `monthly_history_new` RENAME TO `monthly_history`")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Migration from (year, month) composite key to cycleStartDate primary key
                db.execSQL(
                    """
                    CREATE TABLE `monthly_history_new` (
                        `cycleStartDate` TEXT NOT NULL,
                        `year` INTEGER NOT NULL,
                        `month` INTEGER NOT NULL,
                        `budgetAmountCents` INTEGER NOT NULL,
                        `totalSpentCents` INTEGER NOT NULL,
                        `surplusCents` INTEGER NOT NULL,
                        `cycleEndDate` TEXT NOT NULL,
                        `endTimestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`cycleStartDate`)
                    )
                    """.trimIndent()
                )

                // Migrate existing data - estimate cycleStartDate from year/month
                // This is approximate but necessary for migration
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `monthly_history_new` (
                        `cycleStartDate`, `year`, `month`, `budgetAmountCents`, `totalSpentCents`, `surplusCents`, `cycleEndDate`, `endTimestamp`
                    )
                    SELECT 
                        printf('%04d-%02d-01', year, month) as cycleStartDate,
                        `year`, 
                        `month`, 
                        `budgetAmountCents`, 
                        `totalSpentCents`, 
                        `surplusCents`,
                        date(endTimestamp / 1000, 'unixepoch') as cycleEndDate,
                        `endTimestamp`
                    FROM `monthly_history`
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE `monthly_history`")
                db.execSQL("ALTER TABLE `monthly_history_new` RENAME TO `monthly_history`")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Remove year and month columns as they're redundant with cycleStartDate
                db.execSQL(
                    """
                    CREATE TABLE `monthly_history_new` (
                        `cycleStartDate` TEXT NOT NULL,
                        `budgetAmountCents` INTEGER NOT NULL,
                        `totalSpentCents` INTEGER NOT NULL,
                        `surplusCents` INTEGER NOT NULL,
                        `cycleEndDate` TEXT NOT NULL,
                        `endTimestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`cycleStartDate`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO `monthly_history_new` (
                        `cycleStartDate`, `budgetAmountCents`, `totalSpentCents`, `surplusCents`, `cycleEndDate`, `endTimestamp`
                    )
                    SELECT 
                        `cycleStartDate`, 
                        `budgetAmountCents`, 
                        `totalSpentCents`, 
                        `surplusCents`,
                        `cycleEndDate`,
                        `endTimestamp`
                    FROM `monthly_history`
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE `monthly_history`")
                db.execSQL("ALTER TABLE `monthly_history_new` RENAME TO `monthly_history`")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_timestamp` ON `expenses` (`timestamp`)")
            }
        }
    }
}
