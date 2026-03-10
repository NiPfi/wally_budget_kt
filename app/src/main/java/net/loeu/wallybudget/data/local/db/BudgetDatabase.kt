package net.loeu.wallybudget.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import net.loeu.wallybudget.data.local.dao.CycleOverviewDao
import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.dao.MonthlyHistoryDao
import net.loeu.wallybudget.data.local.entity.ExpenseEntity
import net.loeu.wallybudget.data.local.entity.MonthlyHistoryEntity

@Database(
    entities = [ExpenseEntity::class, MonthlyHistoryEntity::class],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class BudgetDatabase : RoomDatabase(), TransactionRunner {
    abstract fun expenseDao(): ExpenseDao
    abstract fun monthlyHistoryDao(): MonthlyHistoryDao
    abstract fun cycleOverviewDao(): CycleOverviewDao

    override suspend fun <T> inTransaction(block: suspend () -> T): T = withTransaction { block() }

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
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_monthly_history_year_month`
                    ON `monthly_history` (`year`, `month`)
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // NOTE: Historical records before schema v3 do not store payday/cycle-length metadata.
                // The cycleStartDate generated below is therefore an approximation based on endTimestamp.
                // For edge cases (e.g., payday = 31 and February), reconstructed start dates may be shifted.
                // This affects only legacy migrated history labels; spending totals remain unchanged.
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
                        `cycleStartDate`, `year`, `month`, `budgetAmountCents`,
                        `totalSpentCents`, `surplusCents`, `cycleEndDate`, `endTimestamp`
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

                // Migrate existing data - estimate cycleStartDate from year/month.
                // This remains approximate because legacy rows lack payday/cycle-length information.
                // Example limitation: payday on the 31st across February cannot be reconstructed exactly.
                // The approximation is used only to preserve uniqueness and avoid data loss.
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `monthly_history_new` (
                        `cycleStartDate`, `year`, `month`, `budgetAmountCents`,
                        `totalSpentCents`, `surplusCents`, `cycleEndDate`, `endTimestamp`
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
                        `cycleStartDate`, `budgetAmountCents`, `totalSpentCents`,
                        `surplusCents`, `cycleEndDate`, `endTimestamp`
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

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Freeze each expense's currently visible local calendar day into persistent storage.
                // If an older app already displayed a shifted day after a timezone change, the
                // original intended local date cannot be reconstructed from the timestamp alone.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `expenses_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `amountCents` INTEGER NOT NULL,
                        `description` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `expenseDate` TEXT NOT NULL,
                        `icon` TEXT
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO `expenses_new` (`id`, `amountCents`, `description`, `timestamp`, `expenseDate`, `icon`)
                    SELECT
                        `id`,
                        `amountCents`,
                        `description`,
                        `timestamp`,
                        date(`timestamp` / 1000, 'unixepoch', 'localtime'),
                        `icon`
                    FROM `expenses`
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE `expenses`")
                db.execSQL("ALTER TABLE `expenses_new` RENAME TO `expenses`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_timestamp` ON `expenses` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_expenseDate` ON `expenses` (`expenseDate`)")
            }
        }
    }
}
