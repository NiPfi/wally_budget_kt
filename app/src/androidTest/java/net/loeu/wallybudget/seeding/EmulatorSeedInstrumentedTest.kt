package net.loeu.wallybudget.seeding

import android.os.Build
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.loeu.wallybudget.data.local.db.BudgetDatabase
import net.loeu.wallybudget.data.local.preferences.UserPreferencesManager
import net.loeu.wallybudget.data.local.entity.Expense
import net.loeu.wallybudget.domain.model.ExpenseCategory
import net.loeu.wallybudget.data.local.entity.MonthlyHistory
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class EmulatorSeedInstrumentedTest {

    @Test
    fun seedAverageSpendingCycles() = runBlocking {
        check(isProbablyEmulator()) {
            "Seeding is restricted to Android emulators. Connected device: ${Build.MODEL} (${Build.FINGERPRINT})"
        }

        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        resetAppStorage(targetContext)

        val database = Room.databaseBuilder(
            targetContext,
            BudgetDatabase::class.java,
            DATABASE_NAME
        )
            .addMigrations(
                BudgetDatabase.MIGRATION_1_2,
                BudgetDatabase.MIGRATION_2_3,
                BudgetDatabase.MIGRATION_3_4,
                BudgetDatabase.MIGRATION_4_5,
                BudgetDatabase.MIGRATION_5_6,
                BudgetDatabase.MIGRATION_6_7
            )
            .build()

        try {
            val today = LocalDate.now()
            val budgetService = BudgetCalculationService()
            val seedPlan = SeedPlanFactory.create(today, budgetService)
            val prefs = UserPreferencesManager(targetContext)

            seedPlan.historyCycles.forEach { cycle ->
                database.monthlyHistoryDao().insert(
                    MonthlyHistory(
                        cycleStartDate = cycle.cycleStartDate.toString(),
                        budgetAmountCents = seedPlan.monthlyBudgetCents,
                        totalSpentCents = cycle.totalSpentCents,
                        surplusCents = budgetService.calculateSurplus(
                            monthlyBudgetCents = seedPlan.monthlyBudgetCents,
                            totalSpentCents = cycle.totalSpentCents
                        ),
                        cycleEndDate = cycle.cycleEndDateExclusive.toString(),
                        endTimestamp = cycle.cycleEndDateExclusive.toStartOfDayMillis()
                    )
                )
            }

            (seedPlan.historyCycles.flatMap { it.expenses } +
                seedPlan.currentCycle.expenses)
                .forEach { spec ->
                    database.expenseDao().insert(spec.toExpense())
                }

            prefs.updateMonthlyBudget(seedPlan.monthlyBudgetCents)
            prefs.updatePaydayDate(seedPlan.paydayDate)
            prefs.updateLastResetTimestamp(seedPlan.currentCycle.cycleStartDate.toStartOfDayMillis())
            prefs.updateLastSeenDate(today)
            prefs.clearPendingCycle()
            prefs.completeOnboarding()

            assertTrue(
                database.monthlyHistoryDao()
                    .getHistoryForCycle(seedPlan.historyCycles.first().cycleStartDate.toString()) != null
            )
            assertTrue((database.expenseDao().getTotalSpentInRange("1900-01-01", "2999-01-01") ?: 0L) > 0L)
        } finally {
            database.close()
        }
    }

    private fun resetAppStorage(targetContext: android.content.Context) {
        targetContext.deleteDatabase(DATABASE_NAME)
        targetContext.filesDir.resolve("datastore/$DATASTORE_NAME").delete()
        targetContext.filesDir.resolve("datastore/$DATASTORE_NAME.bak").delete()
    }

    private fun isProbablyEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.orEmpty().lowercase()
        val model = Build.MODEL.orEmpty().lowercase()
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        val brand = Build.BRAND.orEmpty().lowercase()
        val device = Build.DEVICE.orEmpty().lowercase()
        val product = Build.PRODUCT.orEmpty().lowercase()
        val hardware = Build.HARDWARE.orEmpty().lowercase()

        return fingerprint.startsWith("generic") ||
            fingerprint.startsWith("unknown") ||
            fingerprint.contains("emulator") ||
            model.contains("android sdk built for") ||
            model.contains("emulator") ||
            model.contains("sdk_gphone") ||
            manufacturer.contains("genymotion") ||
            product.contains("sdk") ||
            product.contains("emulator") ||
            product.contains("simulator") ||
            hardware.contains("goldfish") ||
            hardware.contains("ranchu") ||
            (brand.startsWith("generic") && device.startsWith("generic"))
    }

    private fun LocalDate.toStartOfDayMillis(): Long {
        return atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private data class SeedExpenseSpec(
        val expenseDate: LocalDate,
        val amountCents: Long,
        val description: String,
        val icon: ExpenseCategory?,
        val minutesAfterMidnight: Long
    ) {
        fun toExpense(): Expense {
            val timestamp = expenseDate
                .atStartOfDay(ZoneId.systemDefault())
                .plusMinutes(minutesAfterMidnight)
                .toInstant()
                .toEpochMilli()
            return Expense(
                amountCents = amountCents,
                description = description,
                timestamp = timestamp,
                expenseDate = expenseDate.toString(),
                icon = icon
            )
        }
    }

    private data class SeedCycleSnapshot(
        val cycleStartDate: LocalDate,
        val cycleEndDateExclusive: LocalDate,
        val expenses: List<SeedExpenseSpec>
    ) {
        val totalSpentCents: Long = expenses.sumOf { it.amountCents }
    }

    private data class SeedPlan(
        val monthlyBudgetCents: Long,
        val paydayDate: Int,
        val historyCycles: List<SeedCycleSnapshot>,
        val currentCycle: SeedCycleSnapshot
    )

    private object SeedPlanFactory {
        private const val MONTHLY_BUDGET_CENTS = 210_000L
        private const val PAYDAY_DATE = 5

        private data class ExpensePattern(
            val dayOffset: Int,
            val amountCents: Long,
            val description: String,
            val icon: ExpenseCategory?,
            val minutesAfterMidnight: Long
        )

        private val completedCyclePatterns = listOf(
            listOf(
                ExpensePattern(0, 114_000L, "Rent and utilities", ExpenseCategory.HOME, 8 * 60L),
                ExpensePattern(3, 13_500L, "Groceries", ExpenseCategory.GROCERIES, 18 * 60L),
                ExpensePattern(6, 8_900L, "Fuel", ExpenseCategory.TRANSPORT, 7 * 60L + 30L),
                ExpensePattern(10, 10_600L, "Dinner out", ExpenseCategory.RESTAURANT, 19 * 60L),
                ExpensePattern(14, 6_400L, "Pharmacy", ExpenseCategory.HEALTH, 12 * 60L),
                ExpensePattern(18, 14_200L, "Household restock", ExpenseCategory.SHOPPING, 16 * 60L),
                ExpensePattern(22, 12_700L, "Show tickets", ExpenseCategory.ENTERTAINMENT, 20 * 60L),
                ExpensePattern(22, 3_600L, "Dessert stop", ExpenseCategory.RESTAURANT, 21 * 60L + 15L),
                ExpensePattern(25, 20_100L, "Weekend groceries", ExpenseCategory.GROCERIES, 11 * 60L)
            ),
            listOf(
                ExpensePattern(0, 110_000L, "Rent and utilities", ExpenseCategory.HOME, 8 * 60L),
                ExpensePattern(2, 14_800L, "Groceries", ExpenseCategory.GROCERIES, 17 * 60L + 30L),
                ExpensePattern(5, 9_200L, "Transit card", ExpenseCategory.TRANSPORT, 7 * 60L + 45L),
                ExpensePattern(9, 7_600L, "Lunch out", ExpenseCategory.RESTAURANT, 13 * 60L),
                ExpensePattern(13, 15_800L, "Home supplies", ExpenseCategory.SHOPPING, 15 * 60L),
                ExpensePattern(17, 5_400L, "Checkup copay", ExpenseCategory.HEALTH, 9 * 60L + 15L),
                ExpensePattern(20, 9_900L, "Movie night", ExpenseCategory.ENTERTAINMENT, 20 * 60L),
                ExpensePattern(23, 19_600L, "Seasonal clothes", ExpenseCategory.CLOTHING, 14 * 60L),
                ExpensePattern(26, 11_800L, "Top-up groceries", ExpenseCategory.GROCERIES, 10 * 60L + 30L)
            ),
            listOf(
                ExpensePattern(0, 117_000L, "Rent and utilities", ExpenseCategory.HOME, 8 * 60L),
                ExpensePattern(3, 15_200L, "Groceries", ExpenseCategory.GROCERIES, 18 * 60L),
                ExpensePattern(6, 9_800L, "Fuel", ExpenseCategory.TRANSPORT, 7 * 60L + 30L),
                ExpensePattern(9, 10_400L, "Takeout dinner", ExpenseCategory.RESTAURANT, 19 * 60L),
                ExpensePattern(12, 7_800L, "Pharmacy", ExpenseCategory.HEALTH, 12 * 60L),
                ExpensePattern(16, 16_800L, "Apartment fixes", ExpenseCategory.SHOPPING, 16 * 60L),
                ExpensePattern(20, 11_900L, "Streaming and cinema", ExpenseCategory.ENTERTAINMENT, 20 * 60L + 30L),
                ExpensePattern(23, 5_200L, "Misc spend", ExpenseCategory.OTHER, 11 * 60L),
                ExpensePattern(25, 18_400L, "Large grocery run", ExpenseCategory.GROCERIES, 10 * 60L + 15L)
            )
        )

        private val currentCycleAmounts = listOf(7_200L, 4_100L, 8_500L, 5_900L, 7_000L, 4_800L, 9_200L, 5_300L)
        private val currentCycleDescriptions = listOf(
            "Groceries",
            "Transit",
            "Dinner",
            "Household supplies",
            "Health refill",
            "Coffee and snacks",
            "Entertainment",
            "Quick shop"
        )
        private val currentCycleIcons = listOf(
            ExpenseCategory.GROCERIES,
            ExpenseCategory.TRANSPORT,
            ExpenseCategory.RESTAURANT,
            ExpenseCategory.HOME,
            ExpenseCategory.HEALTH,
            ExpenseCategory.RESTAURANT,
            ExpenseCategory.ENTERTAINMENT,
            ExpenseCategory.SHOPPING
        )

        fun create(today: LocalDate, budgetService: BudgetCalculationService): SeedPlan {
            val currentCycleStart = budgetService.getCycleStartDate(today, PAYDAY_DATE)
            val currentCycleEndExclusive = budgetService.getNextCycleStartDate(today, PAYDAY_DATE)

            val historyCyclesDescending = mutableListOf<SeedCycleSnapshot>()
            var cycleEndExclusive = currentCycleStart
            completedCyclePatterns.asReversed().forEach { pattern ->
                val cycleStart = budgetService.getCycleStartDate(
                    cycleEndExclusive.minusDays(1),
                    PAYDAY_DATE
                )
                historyCyclesDescending += createCycleSnapshot(cycleStart, cycleEndExclusive, pattern)
                cycleEndExclusive = cycleStart
            }

            return SeedPlan(
                monthlyBudgetCents = MONTHLY_BUDGET_CENTS,
                paydayDate = PAYDAY_DATE,
                historyCycles = historyCyclesDescending.reversed(),
                currentCycle = buildCurrentCycle(currentCycleStart, currentCycleEndExclusive, today)
            )
        }

        private fun createCycleSnapshot(
            cycleStartDate: LocalDate,
            cycleEndDateExclusive: LocalDate,
            patterns: List<ExpensePattern>
        ): SeedCycleSnapshot {
            val lastCycleDay = ChronoUnit.DAYS.between(cycleStartDate, cycleEndDateExclusive)
                .toInt()
                .coerceAtLeast(1) - 1
            val expenses = patterns.map { pattern ->
                SeedExpenseSpec(
                    expenseDate = cycleStartDate.plusDays(pattern.dayOffset.coerceAtMost(lastCycleDay).toLong()),
                    amountCents = pattern.amountCents,
                    description = pattern.description,
                    icon = pattern.icon,
                    minutesAfterMidnight = pattern.minutesAfterMidnight
                )
            }
            return SeedCycleSnapshot(cycleStartDate, cycleEndDateExclusive, expenses)
        }

        private fun buildCurrentCycle(
            cycleStartDate: LocalDate,
            cycleEndDateExclusive: LocalDate,
            today: LocalDate
        ): SeedCycleSnapshot {
            val visibleEndExclusive = minOf(today.plusDays(1), cycleEndDateExclusive)
            val visibleDayCount = ChronoUnit.DAYS.between(cycleStartDate, visibleEndExclusive).toInt().coerceAtLeast(1)
            val cycleDayCount = ChronoUnit.DAYS.between(cycleStartDate, cycleEndDateExclusive).toInt().coerceAtLeast(1)
            val targetSpentCents = ((MONTHLY_BUDGET_CENTS.toDouble() * visibleDayCount) / cycleDayCount).roundToLong()

            val dayOffsets = buildList {
                var day = 0
                while (day < visibleDayCount) {
                    add(day)
                    day += 2
                }
                val lastVisibleDay = visibleDayCount - 1
                if (last() != lastVisibleDay) {
                    add(lastVisibleDay)
                }
            }

            val scaledAmounts = scaleAmounts(
                baseAmounts = dayOffsets.mapIndexed { index, _ -> currentCycleAmounts[index % currentCycleAmounts.size] },
                targetTotal = targetSpentCents
            )

            val expenses = dayOffsets.mapIndexed { index, dayOffset ->
                SeedExpenseSpec(
                    expenseDate = cycleStartDate.plusDays(dayOffset.toLong()),
                    amountCents = scaledAmounts[index],
                    description = currentCycleDescriptions[index % currentCycleDescriptions.size],
                    icon = currentCycleIcons[index % currentCycleIcons.size],
                    minutesAfterMidnight = 8 * 60L + (index * 73L % (12 * 60L))
                )
            }

            return SeedCycleSnapshot(cycleStartDate, cycleEndDateExclusive, expenses)
        }

        private fun scaleAmounts(baseAmounts: List<Long>, targetTotal: Long): List<Long> {
            val baseTotal = baseAmounts.sum().coerceAtLeast(1L)
            val scaled = baseAmounts.map { amount ->
                ((amount.toDouble() / baseTotal) * targetTotal).roundToLong().coerceAtLeast(1_500L)
            }.toMutableList()

            val delta = targetTotal - scaled.sum()
            scaled[scaled.lastIndex] = (scaled.last() + delta).coerceAtLeast(1_500L)
            return scaled
        }
    }

    companion object {
        private const val DATABASE_NAME = "budget_database"
        private const val DATASTORE_NAME = "user_settings.preferences_pb"
    }
}
