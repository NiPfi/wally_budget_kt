package net.loeu.wallybudget.data.local.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.domain.model.DEFAULT_FUND_NAME
import net.loeu.wallybudget.domain.model.DEFAULT_FUND_UUID
import net.loeu.wallybudget.domain.model.FundType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FundTypeMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BudgetDatabase::class.java.name,
        FrameworkSQLiteOpenHelperFactory()
    )

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "fund-type-migration-test"

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migration16To17_backfillsFundTypesFromLegacyRows() {
        helper.createDatabase(databaseName, 16).use { db ->
            db.execSQL(
                """
                INSERT INTO `funds` (
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
                ) VALUES (
                    '$DEFAULT_FUND_UUID',
                    'Savings',
                    12500,
                    0,
                    NULL,
                    0,
                    'legacy-install',
                    'legacy-install',
                    1234,
                    1234,
                    NULL,
                    NULL,
                    '0000000001234-0000-legacy-install'
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `funds` (
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
                ) VALUES (
                    'fund-goal',
                    'Travel',
                    4500,
                    0,
                    NULL,
                    1,
                    'legacy-install',
                    'legacy-install',
                    1235,
                    1235,
                    NULL,
                    NULL,
                    '0000000001235-0000-legacy-install'
                )
                """.trimIndent()
            )
        }

        val database = Room.databaseBuilder(context, BudgetDatabase::class.java, databaseName)
            .addMigrations(*BudgetDatabaseMigrations.all("fund-type-migration-test"))
            .allowMainThreadQueries()
            .build()

        database.openHelper.writableDatabase

        val funds = runBlocking { database.fundDao().getAllForSnapshot() }

        assertEquals(FundType.DEFAULT_RESERVE, funds.single { it.uuid == DEFAULT_FUND_UUID }.fundType)
        val migratedDefaultFund = funds.single { it.uuid == DEFAULT_FUND_UUID }
        assertEquals(DEFAULT_FUND_NAME, migratedDefaultFund.name)
        assertEquals(12_500L, migratedDefaultFund.balanceCents)
        assertEquals(0L, migratedDefaultFund.allocationPerCycleCents)
        assertNull(migratedDefaultFund.targetAmountCents)
        assertEquals(0, migratedDefaultFund.sortOrder)
        assertEquals(1_234L, migratedDefaultFund.createdAtEpochMs)
        assertEquals(1_234L, migratedDefaultFund.updatedAtEpochMs)

        val migratedGoalFund = funds.single { it.uuid == "fund-goal" }
        assertEquals(FundType.GOAL, migratedGoalFund.fundType)
        assertEquals("Travel", migratedGoalFund.name)
        assertEquals(4_500L, migratedGoalFund.balanceCents)
        assertEquals(0L, migratedGoalFund.allocationPerCycleCents)
        assertNull(migratedGoalFund.targetAmountCents)
        assertEquals(1, migratedGoalFund.sortOrder)
        assertEquals(1_235L, migratedGoalFund.createdAtEpochMs)
        assertEquals(1_235L, migratedGoalFund.updatedAtEpochMs)

        database.close()
    }
}
