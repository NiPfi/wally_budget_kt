package net.loeu.wallybudget.data.local.db

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class ExpenseDateMigrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "expense-date-migration-test"

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migration6To7_backfillsExpenseDateFromCurrentLocalDayView() {
        val timestamp = Instant.parse("2026-03-02T23:30:00Z").toEpochMilli()
        createVersion6Database(timestamp)
        val installId = "expense-date-migration-test"

        val database = Room.databaseBuilder(context, BudgetDatabase::class.java, databaseName)
            .addMigrations(*BudgetDatabaseMigrations.all(installId))
            .allowMainThreadQueries()
            .build()

        database.openHelper.writableDatabase

        val cursor = database.openHelper.readableDatabase.query(
            "SELECT expenseDate FROM expenses WHERE id = 1"
        )
        cursor.use {
            org.junit.Assert.assertTrue(it.moveToFirst())
            val expectedDate = Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .toString()
            assertEquals(expectedDate, it.getString(0))
        }

        database.close()
    }

    private fun createVersion6Database(timestamp: Long) {
        context.deleteDatabase(databaseName)
        val databaseFile = context.getDatabasePath(databaseName)
        databaseFile.parentFile?.mkdirs()

        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { db ->
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `expenses` (
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
                CREATE TABLE IF NOT EXISTS `monthly_history` (
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
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_timestamp` ON `expenses` (`timestamp`)")
            db.execSQL(
                """
                INSERT INTO `expenses` (`id`, `amountCents`, `description`, `timestamp`, `icon`)
                VALUES (1, 1299, 'Migrated lunch', $timestamp, NULL)
                """.trimIndent()
            )
            db.version = 6
        }
    }
}
