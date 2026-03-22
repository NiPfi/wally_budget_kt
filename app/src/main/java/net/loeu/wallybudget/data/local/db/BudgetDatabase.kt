@file:Suppress("LongMethod", "MaxLineLength")

package net.loeu.wallybudget.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.BudgetAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BudgetBucketDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationPolicyDao
import net.loeu.wallybudget.data.local.dao.BucketMonthlyHistoryDao
import net.loeu.wallybudget.data.local.dao.CycleOverviewDao
import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.dao.FundDao
import net.loeu.wallybudget.data.local.dao.FundTransactionDao
import net.loeu.wallybudget.data.local.dao.MonthlyHistoryDao
import net.loeu.wallybudget.data.local.entity.BudgetAdjustmentEntity
import net.loeu.wallybudget.data.local.entity.BudgetBucketEntity
import net.loeu.wallybudget.data.local.entity.BudgetPolicyEntity
import net.loeu.wallybudget.data.local.entity.BucketAllocationAdjustmentEntity
import net.loeu.wallybudget.data.local.entity.BucketAllocationPolicyEntity
import net.loeu.wallybudget.data.local.entity.BucketMonthlyHistoryEntity
import net.loeu.wallybudget.data.local.entity.ExpenseEntity
import net.loeu.wallybudget.data.local.entity.FundEntity
import net.loeu.wallybudget.data.local.entity.FundTransactionEntity
import net.loeu.wallybudget.data.local.entity.MonthlyHistoryEntity
import net.loeu.wallybudget.domain.model.DEFAULT_FUND_NAME
import net.loeu.wallybudget.domain.model.DEFAULT_FUND_UUID
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_NAME
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID

@Database(
    entities = [
        ExpenseEntity::class,
        MonthlyHistoryEntity::class,
        BudgetPolicyEntity::class,
        BudgetAdjustmentEntity::class,
        BudgetBucketEntity::class,
        BucketAllocationPolicyEntity::class,
        BucketAllocationAdjustmentEntity::class,
        BucketMonthlyHistoryEntity::class,
        FundEntity::class,
        FundTransactionEntity::class
    ],
    version = 13,
    exportSchema = false
)
@TypeConverters(Converters::class)
@Suppress("TooManyFunctions")
abstract class BudgetDatabase : RoomDatabase(), TransactionRunner {
    abstract fun expenseDao(): ExpenseDao
    abstract fun monthlyHistoryDao(): MonthlyHistoryDao
    abstract fun cycleOverviewDao(): CycleOverviewDao
    abstract fun budgetPolicyDao(): BudgetPolicyDao
    abstract fun budgetAdjustmentDao(): BudgetAdjustmentDao
    abstract fun budgetBucketDao(): BudgetBucketDao
    abstract fun bucketAllocationPolicyDao(): BucketAllocationPolicyDao
    abstract fun bucketAllocationAdjustmentDao(): BucketAllocationAdjustmentDao
    abstract fun bucketMonthlyHistoryDao(): BucketMonthlyHistoryDao
    abstract fun fundDao(): FundDao
    abstract fun fundTransactionDao(): FundTransactionDao

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

        fun migration7To8(installId: String): Migration {
            return object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `expenses_new` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `recordUuid` TEXT NOT NULL,
                            `amountCents` INTEGER NOT NULL,
                            `description` TEXT NOT NULL,
                            `timestamp` INTEGER NOT NULL,
                            `expenseDate` TEXT NOT NULL,
                            `icon` TEXT,
                            `originInstallId` TEXT NOT NULL,
                            `lastModifiedByInstallId` TEXT NOT NULL,
                            `createdAtEpochMs` INTEGER NOT NULL,
                            `updatedAtEpochMs` INTEGER NOT NULL,
                            `deletedAtEpochMs` INTEGER,
                            `modClock` TEXT NOT NULL
                        )
                        """.trimIndent()
                    )

                    val escapedInstallId = installId.replace("'", "''")
                    db.execSQL(
                        """
                        INSERT INTO `expenses_new` (
                            `id`,
                            `recordUuid`,
                            `amountCents`,
                            `description`,
                            `timestamp`,
                            `expenseDate`,
                            `icon`,
                            `originInstallId`,
                            `lastModifiedByInstallId`,
                            `createdAtEpochMs`,
                            `updatedAtEpochMs`,
                            `deletedAtEpochMs`,
                            `modClock`
                        )
                        SELECT
                            `id`,
                            lower(hex(randomblob(4))) || '-' ||
                                lower(hex(randomblob(2))) || '-' ||
                                '4' || substr(lower(hex(randomblob(2))), 2) || '-' ||
                                substr('89ab', abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))), 2) || '-' ||
                                lower(hex(randomblob(6))),
                            `amountCents`,
                            `description`,
                            `timestamp`,
                            `expenseDate`,
                            `icon`,
                            '$escapedInstallId',
                            '$escapedInstallId',
                            `timestamp`,
                            `timestamp`,
                            NULL,
                            printf('%013d-%04d-%s', `timestamp`, 0, '$escapedInstallId')
                        FROM `expenses`
                        """.trimIndent()
                    )

                    db.execSQL("DROP TABLE `expenses`")
                    db.execSQL("ALTER TABLE `expenses_new` RENAME TO `expenses`")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_expenses_recordUuid` ON `expenses` (`recordUuid`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_timestamp` ON `expenses` (`timestamp`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_expenseDate` ON `expenses` (`expenseDate`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_deletedAtEpochMs` ON `expenses` (`deletedAtEpochMs`)")

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `budget_policies` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `policyUuid` TEXT NOT NULL,
                            `cycleStartDate` TEXT NOT NULL,
                            `cycleEndDateExclusive` TEXT NOT NULL,
                            `budgetAmountCents` INTEGER NOT NULL,
                            `paydayDayOfMonth` INTEGER NOT NULL,
                            `originInstallId` TEXT NOT NULL,
                            `lastModifiedByInstallId` TEXT NOT NULL,
                            `createdAtEpochMs` INTEGER NOT NULL,
                            `updatedAtEpochMs` INTEGER NOT NULL,
                            `deletedAtEpochMs` INTEGER,
                            `modClock` TEXT NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_budget_policies_policyUuid` ON `budget_policies` (`policyUuid`)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_budget_policies_cycleStartDate` ON `budget_policies` (`cycleStartDate`)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_budget_policies_cycleEndDateExclusive` ON `budget_policies` (`cycleEndDateExclusive`)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_budget_policies_deletedAtEpochMs` ON `budget_policies` (`deletedAtEpochMs`)"
                    )
                }
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `budget_adjustments` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `adjustmentUuid` TEXT NOT NULL,
                        `cycleStartDate` TEXT NOT NULL,
                        `effectiveDate` TEXT NOT NULL,
                        `previousMonthlyBudgetCents` INTEGER NOT NULL,
                        `newMonthlyBudgetCents` INTEGER NOT NULL,
                        `originInstallId` TEXT NOT NULL,
                        `lastModifiedByInstallId` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        `deletedAtEpochMs` INTEGER,
                        `modClock` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_budget_adjustments_adjustmentUuid` ON `budget_adjustments` (`adjustmentUuid`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_budget_adjustments_cycleStartDate` ON `budget_adjustments` (`cycleStartDate`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_budget_adjustments_effectiveDate` ON `budget_adjustments` (`effectiveDate`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_budget_adjustments_deletedAtEpochMs` ON `budget_adjustments` (`deletedAtEpochMs`)"
                )
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE `expenses`
                    ADD COLUMN `bucketUuid` TEXT NOT NULL DEFAULT '$DEFAULT_SPENDING_BUCKET_UUID'
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_bucketUuid` ON `expenses` (`bucketUuid`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `budget_buckets` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `bucketUuid` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `trackingMode` TEXT NOT NULL,
                        `balanceBehavior` TEXT NOT NULL,
                        `defaultAllocatedAmountCents` INTEGER NOT NULL DEFAULT 0,
                        `sortOrder` INTEGER NOT NULL,
                        `isPrimary` INTEGER NOT NULL,
                        `originInstallId` TEXT NOT NULL,
                        `lastModifiedByInstallId` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        `closedAtEpochMs` INTEGER,
                        `deletedAtEpochMs` INTEGER,
                        `modClock` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_budget_buckets_bucketUuid` ON `budget_buckets` (`bucketUuid`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_budget_buckets_sortOrder` ON `budget_buckets` (`sortOrder`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_budget_buckets_closedAtEpochMs` ON `budget_buckets` (`closedAtEpochMs`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_budget_buckets_deletedAtEpochMs` ON `budget_buckets` (`deletedAtEpochMs`)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `bucket_allocation_policies` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `allocationUuid` TEXT NOT NULL,
                        `bucketUuid` TEXT NOT NULL,
                        `cycleStartDate` TEXT NOT NULL,
                        `cycleEndDateExclusive` TEXT NOT NULL,
                        `allocatedAmountCents` INTEGER NOT NULL,
                        `originInstallId` TEXT NOT NULL,
                        `lastModifiedByInstallId` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        `deletedAtEpochMs` INTEGER,
                        `modClock` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_bucket_allocation_policies_allocationUuid` ON `bucket_allocation_policies` (`allocationUuid`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_bucket_allocation_policies_bucketUuid` ON `bucket_allocation_policies` (`bucketUuid`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_bucket_allocation_policies_cycleStartDate` ON `bucket_allocation_policies` (`cycleStartDate`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_bucket_allocation_policies_cycleEndDateExclusive` ON `bucket_allocation_policies` (`cycleEndDateExclusive`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_bucket_allocation_policies_deletedAtEpochMs` ON `bucket_allocation_policies` (`deletedAtEpochMs`)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `bucket_allocation_adjustments` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `adjustmentUuid` TEXT NOT NULL,
                        `bucketUuid` TEXT NOT NULL,
                        `cycleStartDate` TEXT NOT NULL,
                        `effectiveDate` TEXT NOT NULL,
                        `previousAllocatedAmountCents` INTEGER NOT NULL,
                        `newAllocatedAmountCents` INTEGER NOT NULL,
                        `originInstallId` TEXT NOT NULL,
                        `lastModifiedByInstallId` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        `deletedAtEpochMs` INTEGER,
                        `modClock` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_bucket_allocation_adjustments_adjustmentUuid` ON `bucket_allocation_adjustments` (`adjustmentUuid`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_bucket_allocation_adjustments_bucketUuid` ON `bucket_allocation_adjustments` (`bucketUuid`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_bucket_allocation_adjustments_cycleStartDate` ON `bucket_allocation_adjustments` (`cycleStartDate`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_bucket_allocation_adjustments_effectiveDate` ON `bucket_allocation_adjustments` (`effectiveDate`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_bucket_allocation_adjustments_deletedAtEpochMs` ON `bucket_allocation_adjustments` (`deletedAtEpochMs`)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `bucket_monthly_history` (
                        `bucketUuid` TEXT NOT NULL,
                        `cycleStartDate` TEXT NOT NULL,
                        `budgetAmountCents` INTEGER NOT NULL,
                        `totalSpentCents` INTEGER NOT NULL,
                        `surplusCents` INTEGER NOT NULL,
                        `cycleEndDate` TEXT NOT NULL,
                        `endTimestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`bucketUuid`, `cycleStartDate`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `budget_buckets` (
                        `bucketUuid`,
                        `name`,
                        `trackingMode`,
                        `balanceBehavior`,
                        `defaultAllocatedAmountCents`,
                        `sortOrder`,
                        `isPrimary`,
                        `originInstallId`,
                        `lastModifiedByInstallId`,
                        `createdAtEpochMs`,
                        `updatedAtEpochMs`,
                        `closedAtEpochMs`,
                        `deletedAtEpochMs`,
                        `modClock`
                    )
                    SELECT
                        '$DEFAULT_SPENDING_BUCKET_UUID',
                        '$DEFAULT_SPENDING_BUCKET_NAME',
                        'DAILY_TARGET',
                        'RETURN_TO_PORTFOLIO',
                        0,
                        0,
                        1,
                        COALESCE((SELECT originInstallId FROM budget_policies ORDER BY createdAtEpochMs ASC LIMIT 1), ''),
                        COALESCE((SELECT lastModifiedByInstallId FROM budget_policies ORDER BY updatedAtEpochMs DESC LIMIT 1), ''),
                        COALESCE((SELECT MIN(createdAtEpochMs) FROM budget_policies), 0),
                        COALESCE((SELECT MAX(updatedAtEpochMs) FROM budget_policies), 0),
                        NULL,
                        NULL,
                        COALESCE((SELECT modClock FROM budget_policies ORDER BY updatedAtEpochMs DESC LIMIT 1), '')
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO `bucket_allocation_policies` (
                        `allocationUuid`,
                        `bucketUuid`,
                        `cycleStartDate`,
                        `cycleEndDateExclusive`,
                        `allocatedAmountCents`,
                        `originInstallId`,
                        `lastModifiedByInstallId`,
                        `createdAtEpochMs`,
                        `updatedAtEpochMs`,
                        `deletedAtEpochMs`,
                        `modClock`
                    )
                    SELECT
                        lower(hex(randomblob(4))) || '-' ||
                            lower(hex(randomblob(2))) || '-' ||
                            '4' || substr(lower(hex(randomblob(2))), 2) || '-' ||
                            substr('89ab', abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))), 2) || '-' ||
                            lower(hex(randomblob(6))),
                        '$DEFAULT_SPENDING_BUCKET_UUID',
                        `cycleStartDate`,
                        `cycleEndDateExclusive`,
                        `budgetAmountCents`,
                        `originInstallId`,
                        `lastModifiedByInstallId`,
                        `createdAtEpochMs`,
                        `updatedAtEpochMs`,
                        `deletedAtEpochMs`,
                        `modClock`
                    FROM `budget_policies`
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO `bucket_allocation_adjustments` (
                        `adjustmentUuid`,
                        `bucketUuid`,
                        `cycleStartDate`,
                        `effectiveDate`,
                        `previousAllocatedAmountCents`,
                        `newAllocatedAmountCents`,
                        `originInstallId`,
                        `lastModifiedByInstallId`,
                        `createdAtEpochMs`,
                        `updatedAtEpochMs`,
                        `deletedAtEpochMs`,
                        `modClock`
                    )
                    SELECT
                        lower(hex(randomblob(4))) || '-' ||
                            lower(hex(randomblob(2))) || '-' ||
                            '4' || substr(lower(hex(randomblob(2))), 2) || '-' ||
                            substr('89ab', abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))), 2) || '-' ||
                            lower(hex(randomblob(6))),
                        '$DEFAULT_SPENDING_BUCKET_UUID',
                        `cycleStartDate`,
                        `effectiveDate`,
                        `previousMonthlyBudgetCents`,
                        `newMonthlyBudgetCents`,
                        `originInstallId`,
                        `lastModifiedByInstallId`,
                        `createdAtEpochMs`,
                        `updatedAtEpochMs`,
                        `deletedAtEpochMs`,
                        `modClock`
                    FROM `budget_adjustments`
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO `bucket_monthly_history` (
                        `bucketUuid`,
                        `cycleStartDate`,
                        `budgetAmountCents`,
                        `totalSpentCents`,
                        `surplusCents`,
                        `cycleEndDate`,
                        `endTimestamp`
                    )
                    SELECT
                        '$DEFAULT_SPENDING_BUCKET_UUID',
                        `cycleStartDate`,
                        `budgetAmountCents`,
                        `totalSpentCents`,
                        `surplusCents`,
                        `cycleEndDate`,
                        `endTimestamp`
                    FROM `monthly_history`
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `budget_buckets_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `bucketUuid` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `trackingMode` TEXT NOT NULL,
                        `balanceBehavior` TEXT NOT NULL,
                        `defaultAllocatedAmountCents` INTEGER NOT NULL DEFAULT 0,
                        `sortOrder` INTEGER NOT NULL,
                        `originInstallId` TEXT NOT NULL,
                        `lastModifiedByInstallId` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        `closedAtEpochMs` INTEGER,
                        `deletedAtEpochMs` INTEGER,
                        `modClock` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `budget_buckets_new` (
                        `id`,
                        `bucketUuid`,
                        `name`,
                        `trackingMode`,
                        `balanceBehavior`,
                        `defaultAllocatedAmountCents`,
                        `sortOrder`,
                        `originInstallId`,
                        `lastModifiedByInstallId`,
                        `createdAtEpochMs`,
                        `updatedAtEpochMs`,
                        `closedAtEpochMs`,
                        `deletedAtEpochMs`,
                        `modClock`
                    )
                    SELECT
                        `id`,
                        `bucketUuid`,
                        `name`,
                        `trackingMode`,
                        `balanceBehavior`,
                        `defaultAllocatedAmountCents`,
                        `sortOrder`,
                        `originInstallId`,
                        `lastModifiedByInstallId`,
                        `createdAtEpochMs`,
                        `updatedAtEpochMs`,
                        `closedAtEpochMs`,
                        `deletedAtEpochMs`,
                        `modClock`
                    FROM `budget_buckets`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `budget_buckets`")
                db.execSQL("ALTER TABLE `budget_buckets_new` RENAME TO `budget_buckets`")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_budget_buckets_bucketUuid` ON `budget_buckets` (`bucketUuid`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_budget_buckets_sortOrder` ON `budget_buckets` (`sortOrder`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_budget_buckets_closedAtEpochMs` ON `budget_buckets` (`closedAtEpochMs`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_budget_buckets_deletedAtEpochMs` ON `budget_buckets` (`deletedAtEpochMs`)"
                )
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val bucketColumns = db.query("PRAGMA table_info(`budget_buckets`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")
                    buildSet {
                        while (cursor.moveToNext()) {
                            if (nameIndex >= 0) add(cursor.getString(nameIndex))
                        }
                    }
                }
                val hasTrackingModeColumn = "trackingMode" in bucketColumns
                val hasBalanceBehaviorColumn = "balanceBehavior" in bucketColumns
                val allocatedAmountColumn = when {
                    "defaultAllocatedAmountCents" in bucketColumns -> "defaultAllocatedAmountCents"
                    "allocatedCents" in bucketColumns -> "allocatedCents"
                    else -> null
                }

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `budget_buckets_normalized` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `bucketUuid` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `trackingMode` TEXT NOT NULL,
                        `balanceBehavior` TEXT NOT NULL,
                        `defaultAllocatedAmountCents` INTEGER NOT NULL DEFAULT 0,
                        `sortOrder` INTEGER NOT NULL,
                        `originInstallId` TEXT NOT NULL,
                        `lastModifiedByInstallId` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        `closedAtEpochMs` INTEGER,
                        `deletedAtEpochMs` INTEGER,
                        `modClock` TEXT NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO `budget_buckets_normalized` (
                        `id`,
                        `bucketUuid`,
                        `name`,
                        `trackingMode`,
                        `balanceBehavior`,
                        `defaultAllocatedAmountCents`,
                        `sortOrder`,
                        `originInstallId`,
                        `lastModifiedByInstallId`,
                        `createdAtEpochMs`,
                        `updatedAtEpochMs`,
                        `closedAtEpochMs`,
                        `deletedAtEpochMs`,
                        `modClock`
                    )
                    SELECT
                        `id`,
                        `bucketUuid`,
                        `name`,
                        ${if (hasTrackingModeColumn) "`trackingMode`" else "'DAILY_TARGET'"},
                        ${if (hasBalanceBehaviorColumn) "`balanceBehavior`" else "'RETURN_TO_PORTFOLIO'"},
                        ${allocatedAmountColumn?.let { "`$it`" } ?: "0"},
                        `sortOrder`,
                        `originInstallId`,
                        `lastModifiedByInstallId`,
                        `createdAtEpochMs`,
                        `updatedAtEpochMs`,
                        `closedAtEpochMs`,
                        `deletedAtEpochMs`,
                        `modClock`
                    FROM `budget_buckets`
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE `budget_buckets`")
                db.execSQL("ALTER TABLE `budget_buckets_normalized` RENAME TO `budget_buckets`")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_budget_buckets_bucketUuid` ON `budget_buckets` (`bucketUuid`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_budget_buckets_sortOrder` ON `budget_buckets` (`sortOrder`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_budget_buckets_closedAtEpochMs` ON `budget_buckets` (`closedAtEpochMs`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_budget_buckets_deletedAtEpochMs` ON `budget_buckets` (`deletedAtEpochMs`)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `funds` (
                        `uuid` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `balanceCents` INTEGER NOT NULL DEFAULT 0,
                        `allocationPerCycleCents` INTEGER NOT NULL DEFAULT 0,
                        `targetAmountCents` INTEGER,
                        `sortOrder` INTEGER NOT NULL DEFAULT 0,
                        `originInstallId` TEXT NOT NULL,
                        `lastModifiedByInstallId` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        `closedAtEpochMs` INTEGER,
                        `deletedAtEpochMs` INTEGER,
                        `modClock` TEXT NOT NULL,
                        PRIMARY KEY(`uuid`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_funds_sortOrder` ON `funds` (`sortOrder`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_funds_closedAtEpochMs` ON `funds` (`closedAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_funds_deletedAtEpochMs` ON `funds` (`deletedAtEpochMs`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `fund_transactions` (
                        `uuid` TEXT NOT NULL,
                        `fundUuid` TEXT NOT NULL,
                        `amountCents` INTEGER NOT NULL,
                        `type` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `dateEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`uuid`),
                        FOREIGN KEY(`fundUuid`) REFERENCES `funds`(`uuid`) ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_fund_transactions_fundUuid` ON `fund_transactions` (`fundUuid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_fund_transactions_dateEpochMs` ON `fund_transactions` (`dateEpochMs`)")

                val migratedBalanceCents = try {
                    db.query("SELECT COALESCE(SUM(surplusCents), 0) FROM `monthly_history`").use { cursor ->
                        if (cursor.moveToFirst()) cursor.getLong(0).coerceAtLeast(0L) else 0L
                    }
                } catch (_: Exception) {
                    0L
                }

                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `funds` (
                        `uuid`,
                        `name`,
                        `balanceCents`,
                        `allocationPerCycleCents`,
                        `targetAmountCents`,
                        `sortOrder`,
                        `originInstallId`,
                        `lastModifiedByInstallId`,
                        `createdAtEpochMs`,
                        `updatedAtEpochMs`,
                        `closedAtEpochMs`,
                        `deletedAtEpochMs`,
                        `modClock`
                    )
                    SELECT
                        '$DEFAULT_FUND_UUID',
                        '$DEFAULT_FUND_NAME',
                        $migratedBalanceCents,
                        0,
                        NULL,
                        0,
                        seed.originInstallId,
                        seed.lastModifiedByInstallId,
                        seed.createdAtEpochMs,
                        seed.updatedAtEpochMs,
                        NULL,
                        NULL,
                        seed.modClock
                    FROM (
                        SELECT
                            COALESCE(
                                NULLIF((SELECT originInstallId FROM budget_buckets ORDER BY createdAtEpochMs ASC LIMIT 1), ''),
                                NULLIF((SELECT originInstallId FROM budget_policies ORDER BY createdAtEpochMs ASC LIMIT 1), '')
                            ) AS originInstallId,
                            COALESCE(
                                NULLIF(
                                    (SELECT lastModifiedByInstallId FROM budget_buckets ORDER BY updatedAtEpochMs DESC LIMIT 1),
                                    ''
                                ),
                                NULLIF(
                                    (SELECT lastModifiedByInstallId FROM budget_policies ORDER BY updatedAtEpochMs DESC LIMIT 1),
                                    ''
                                )
                            ) AS lastModifiedByInstallId,
                            COALESCE(
                                (SELECT MIN(createdAtEpochMs) FROM budget_buckets),
                                (SELECT MIN(createdAtEpochMs) FROM budget_policies),
                                0
                            ) AS createdAtEpochMs,
                            COALESCE(
                                (SELECT MAX(updatedAtEpochMs) FROM budget_buckets),
                                (SELECT MAX(updatedAtEpochMs) FROM budget_policies),
                                0
                            ) AS updatedAtEpochMs,
                            COALESCE(
                                NULLIF((SELECT modClock FROM budget_buckets ORDER BY updatedAtEpochMs DESC LIMIT 1), ''),
                                NULLIF((SELECT modClock FROM budget_policies ORDER BY updatedAtEpochMs DESC LIMIT 1), '')
                            ) AS modClock
                    ) AS seed
                    WHERE seed.originInstallId IS NOT NULL
                        AND seed.lastModifiedByInstallId IS NOT NULL
                        AND seed.modClock IS NOT NULL
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys=OFF")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `funds_new` (
                        `uuid` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `balanceCents` INTEGER NOT NULL DEFAULT 0,
                        `allocationPerCycleCents` INTEGER NOT NULL DEFAULT 0,
                        `targetAmountCents` INTEGER,
                        `sortOrder` INTEGER NOT NULL DEFAULT 0,
                        `originInstallId` TEXT NOT NULL,
                        `lastModifiedByInstallId` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        `closedAtEpochMs` INTEGER,
                        `deletedAtEpochMs` INTEGER,
                        `modClock` TEXT NOT NULL,
                        PRIMARY KEY(`uuid`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `funds_new` (
                        `uuid`,
                        `name`,
                        `balanceCents`,
                        `allocationPerCycleCents`,
                        `targetAmountCents`,
                        `sortOrder`,
                        `originInstallId`,
                        `lastModifiedByInstallId`,
                        `createdAtEpochMs`,
                        `updatedAtEpochMs`,
                        `closedAtEpochMs`,
                        `deletedAtEpochMs`,
                        `modClock`
                    )
                    SELECT
                        `uuid`,
                        `name`,
                        `balanceCents`,
                        `allocationPerCycleCents`,
                        `targetAmountCents`,
                        `sortOrder`,
                        `originInstallId`,
                        `lastModifiedByInstallId`,
                        `createdAtEpochMs`,
                        `updatedAtEpochMs`,
                        `closedAtEpochMs`,
                        `deletedAtEpochMs`,
                        `modClock`
                    FROM `funds`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `funds`")
                db.execSQL("ALTER TABLE `funds_new` RENAME TO `funds`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_funds_sortOrder` ON `funds` (`sortOrder`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_funds_closedAtEpochMs` ON `funds` (`closedAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_funds_deletedAtEpochMs` ON `funds` (`deletedAtEpochMs`)")

                db.execSQL("PRAGMA foreign_keys=ON")
            }
        }
    }
}
