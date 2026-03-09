package net.loeu.wallybudget.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import net.loeu.wallybudget.data.local.ExpenseDao
import net.loeu.wallybudget.data.local.MonthlyHistoryDao
import net.loeu.wallybudget.data.local.UserPreferencesManager
import net.loeu.wallybudget.data.model.BudgetState
import net.loeu.wallybudget.data.model.Expense
import net.loeu.wallybudget.data.model.ExpenseCategory
import net.loeu.wallybudget.data.model.ExpenseCycleSection
import net.loeu.wallybudget.data.model.ExpenseDaySection
import net.loeu.wallybudget.data.model.MonthlyHistory
import net.loeu.wallybudget.data.model.PendingCycleCloseoutState
import net.loeu.wallybudget.data.model.SpendingForecast
import net.loeu.wallybudget.data.model.TimelineLockState
import net.loeu.wallybudget.data.model.UserSettings
import net.loeu.wallybudget.data.model.recordedDate
import net.loeu.wallybudget.data.time.CurrentDateProvider
import net.loeu.wallybudget.domain.config.ForecastConfig
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private data class CycleRange(
    val start: LocalDate,
    val endExclusive: LocalDate
)

internal object ObservedDatePolicy {
    // Keep small backward shifts monotonic, but recover quickly from clearly bad future jumps.
    private const val MAX_BACKWARD_DATE_SKEW_DAYS = 1L

    fun resolve(lastSeenDate: LocalDate?, observedDate: LocalDate): LocalDate {
        if (lastSeenDate == null || !observedDate.isBefore(lastSeenDate)) {
            return observedDate
        }

        val rollbackDays = ChronoUnit.DAYS.between(observedDate, lastSeenDate)
        return if (rollbackDays <= MAX_BACKWARD_DATE_SKEW_DAYS) {
            lastSeenDate
        } else {
            observedDate
        }
    }

    fun shouldPersist(lastSeenDate: LocalDate?, observedDate: LocalDate): Boolean {
        if (lastSeenDate == null || observedDate.isAfter(lastSeenDate)) {
            return true
        }

        if (!observedDate.isBefore(lastSeenDate)) {
            return false
        }

        val rollbackDays = ChronoUnit.DAYS.between(observedDate, lastSeenDate)
        return rollbackDays > MAX_BACKWARD_DATE_SKEW_DAYS
    }
}

class BudgetRepository(
    private val expenseDao: ExpenseDao,
    private val monthlyHistoryDao: MonthlyHistoryDao,
    private val userPreferencesManager: UserPreferencesManager,
    private val budgetCalculationService: BudgetCalculationService = BudgetCalculationService(),
    private val currentDateProvider: CurrentDateProvider
) {

    val userSettings: Flow<UserSettings> = userPreferencesManager.userSettings

    fun getEffectiveCurrentDate(): Flow<LocalDate> {
        return combine(userSettings, currentDateProvider.observeCurrentDate()) { settings, observedDate ->
            effectiveCurrentDate(settings, observedDate)
        }.distinctUntilChanged()
    }

    fun getBudgetState(): Flow<BudgetState> {
        return combine(
            userSettings,
            expenseDao.observeExpenseCount(),
            getEffectiveCurrentDate(),
            monthlyHistoryDao.getAllHistory()
        ) { settings, _, today, history ->
            val currentCycleRange = budgetCalculationService.getCurrentCycleProgressRange(
                now = today,
                paydayDate = settings.paydayDate
            )

            val totalSpentCents = expenseDao.getTotalSpentInRange(
                currentCycleRange.start.toString(),
                currentCycleRange.endExclusive.toString()
            ) ?: 0L

            val spentTodayCents = expenseDao.getTotalSpentInRange(
                today.toString(),
                today.plusDays(1).toString()
            ) ?: 0L

            val cumulativeSavingsCents = history
                .filter { !it.getCycleEnd().isAfter(currentCycleRange.start) }
                .sumOf { it.surplusCents }

            budgetCalculationService.calculateBudgetState(
                settings = settings,
                now = today,
                totalSpentThisCycleCents = totalSpentCents,
                spentTodayCents = spentTodayCents,
                cumulativeSavingsCents = cumulativeSavingsCents
            )
        }
    }

    fun getTimelineLockState(): Flow<TimelineLockState> {
        return combine(
            userSettings,
            expenseDao.observeLatestExpenseDate(),
            getEffectiveCurrentDate()
        ) { settings, latestExpenseDate, effectiveCurrentDate ->
            buildTimelineLockState(
                settings = settings,
                effectiveCurrentDate = effectiveCurrentDate,
                latestExpenseDate = latestExpenseDate?.let(LocalDate::parse)
            )
        }.distinctUntilChanged()
    }

    suspend fun addExpense(expense: Expense): Long = expenseDao.insert(expense)

    suspend fun updateExpense(expense: Expense) {
        expenseDao.update(expense)
    }

    suspend fun deleteExpense(expense: Expense) {
        expenseDao.delete(expense)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getTodayExpenses(): Flow<List<Expense>> {
        return getEffectiveCurrentDate().flatMapLatest { now ->
            expenseDao.getExpensesByDateRange(
                startDateInclusive = now.toString(),
                endDateExclusive = now.plusDays(1).toString()
            )
        }
    }

    fun getActiveCycleExpenseSections(): Flow<List<ExpenseDaySection>> {
        return combine(
            userSettings,
            getBudgetState(),
            expenseDao.getAllExpensesOrderedByTimestampDesc(),
            getEffectiveCurrentDate()
        ) { settings, budgetState, allExpenses, today ->
            val cycleStart = budgetState.cycleStartDate
            val cycleExpenses = allExpenses.filterByRange(cycleStart, today.plusDays(1))
            val expensesByDate = cycleExpenses.groupByLocalDate()
            val timelineLockState = buildTimelineLockState(
                settings = settings,
                effectiveCurrentDate = today,
                latestExpenseDate = allExpenses.firstOrNull()?.recordedDate()
            )
            buildContinuousDaySections(
                start = cycleStart,
                endInclusive = today,
                expensesByDate = expensesByDate,
                remainingBudgetForDay = { totalSpent -> budgetState.dailyBudgetCents - totalSpent },
                isEditable = !timelineLockState.isLocked,
                today = today
            )
        }
    }

    fun getHistorySections(): Flow<List<ExpenseCycleSection>> {
        return combine(
            userSettings,
            monthlyHistoryDao.getAllHistory(),
            expenseDao.getAllExpensesOrderedByTimestampDesc(),
            getBudgetState(),
            getEffectiveCurrentDate()
        ) { settings, history, allExpenses, budgetState, today ->
            val sections = mutableListOf<ExpenseCycleSection>()
            val currentCycleStart = budgetState.cycleStartDate
            val timelineLockState = buildTimelineLockState(
                settings = settings,
                effectiveCurrentDate = today,
                latestExpenseDate = allExpenses.firstOrNull()?.recordedDate()
            )
            val activeCycleExpenses = allExpenses.filterByRange(currentCycleStart, today.plusDays(1))
            val activeCycleDaySections = buildContinuousDaySections(
                start = currentCycleStart,
                endInclusive = today,
                expensesByDate = activeCycleExpenses.groupByLocalDate(),
                remainingBudgetForDay = { totalSpent -> budgetState.dailyBudgetCents - totalSpent },
                isEditable = !timelineLockState.isLocked,
                today = today
            )

            sections += ExpenseCycleSection(
                cycleStartDate = currentCycleStart,
                cycleEndDateExclusive = today.plusDays(1),
                title = "Current cycle",
                budgetAmountCents = budgetState.monthlyBudgetCents,
                totalSpentCents = budgetState.totalSpentThisCycleCents,
                surplusCents = budgetState.remainingCycleCents,
                daySections = activeCycleDaySections,
                isActiveCycle = true,
                isReadOnly = timelineLockState.isLocked,
                isCompletedCycle = false
            )

            val futureExpenses = allExpenses.filter { it.recordedDate().isAfter(today) }
            if (futureExpenses.isNotEmpty()) {
                val futureDaySections = futureExpenses
                    .groupByLocalDate()
                    .toSortedMap(compareByDescending { it })
                    .map { (date, expenses) ->
                        ExpenseDaySection(
                            date = date,
                            expenses = expenses,
                            totalSpentCents = expenses.sumOf { it.amountCents },
                            remainingForDayCents = null,
                            isEditable = false
                        )
                    }
                val futureStart = futureDaySections.last().date
                val futureEndExclusive = futureDaySections.first().date.plusDays(1)
                val futureTotalSpent = futureExpenses.sumOf { it.amountCents }
                sections += ExpenseCycleSection(
                    cycleStartDate = futureStart,
                    cycleEndDateExclusive = futureEndExclusive,
                    title = "Future-dated expenses",
                    budgetAmountCents = budgetState.monthlyBudgetCents,
                    totalSpentCents = futureTotalSpent,
                    surplusCents = budgetState.monthlyBudgetCents - futureTotalSpent,
                    daySections = futureDaySections,
                    isActiveCycle = false,
                    isReadOnly = true,
                    isCompletedCycle = false
                )
            }

            sections += history
                .filter { it.totalSpentCents > 0L }
                .filterNot { it.getCycleStart() == currentCycleStart }
                .sortedByDescending { it.endTimestamp }
                .map { monthlyHistory ->
                    val cycleExpenses = allExpenses.filterByRange(
                        start = monthlyHistory.getCycleStart(),
                        endExclusive = monthlyHistory.getCycleEnd()
                    )
                    val daySections = cycleExpenses
                        .groupByLocalDate()
                        .toSortedMap(compareByDescending { it })
                        .map { (date, expenses) ->
                            ExpenseDaySection(
                                date = date,
                                expenses = expenses,
                                totalSpentCents = expenses.sumOf { it.amountCents },
                                remainingForDayCents = null,
                                isEditable = false
                            )
                        }

                    ExpenseCycleSection(
                        cycleStartDate = monthlyHistory.getCycleStart(),
                        cycleEndDateExclusive = monthlyHistory.getCycleEnd(),
                        title = monthlyHistory.getDisplayName(),
                        budgetAmountCents = monthlyHistory.budgetAmountCents,
                        totalSpentCents = monthlyHistory.totalSpentCents,
                        surplusCents = monthlyHistory.surplusCents,
                        daySections = daySections,
                        isActiveCycle = false,
                        isReadOnly = true,
                        isCompletedCycle = true
                    )
                }

            sections
        }
    }

    fun getPendingCycleCloseoutState(): Flow<PendingCycleCloseoutState?> {
        return combine(
            userSettings,
            expenseDao.getAllExpensesOrderedByTimestampDesc()
        ) { settings, allExpenses ->
            val pendingCycle = settings.pendingCycleRangeOrNull() ?: return@combine null
            val cycleExpenses = allExpenses.filterByRange(pendingCycle.start, pendingCycle.endExclusive)
            val dayCount = ChronoUnit.DAYS.between(pendingCycle.start, pendingCycle.endExclusive)
                .toInt()
                .coerceAtLeast(1)
            val baseDailyBudget = settings.monthlyBudgetCents / dayCount
            val expensesByDate = cycleExpenses.groupByLocalDate()
            val daySections = buildContinuousDaySections(
                start = pendingCycle.start,
                endInclusive = pendingCycle.endExclusive.minusDays(1),
                expensesByDate = expensesByDate,
                remainingBudgetForDay = { totalSpent -> baseDailyBudget - totalSpent },
                isEditable = true,
                today = null
            )
            val totalSpent = cycleExpenses.sumOf { it.amountCents }
            val biggestExpense = cycleExpenses.maxByOrNull { it.amountCents }
            val highestSpendDay = daySections.maxByOrNull { it.totalSpentCents }?.date
            val topCategory = cycleExpenses
                .filter { it.icon != null }
                .groupBy { it.icon }
                .maxByOrNull { (_, expenses) -> expenses.sumOf { it.amountCents } }
                ?.key

            PendingCycleCloseoutState(
                cycleStartDate = pendingCycle.start,
                cycleEndDateExclusive = pendingCycle.endExclusive,
                budgetAmountCents = settings.monthlyBudgetCents,
                totalSpentCents = totalSpent,
                surplusCents = settings.monthlyBudgetCents - totalSpent,
                averageDailySpendCents = totalSpent / dayCount,
                biggestExpense = biggestExpense,
                highestSpendDay = highestSpendDay,
                topCategory = topCategory,
                trendSummary = buildTrendSummary(daySections),
                daySections = daySections
            )
        }
    }

    suspend fun updateMonthlyBudget(amountCents: Long) {
        userPreferencesManager.updateMonthlyBudget(amountCents)
    }

    suspend fun updatePaydayDate(day: Int) {
        userPreferencesManager.updatePaydayDate(day)
    }

    suspend fun completeOnboarding(
        monthlyBudgetCents: Long,
        paydayDate: Int,
        cycleStartDate: LocalDate,
        previousExpensesCents: Long
    ) {
        userPreferencesManager.updateMonthlyBudget(monthlyBudgetCents)
        userPreferencesManager.updatePaydayDate(paydayDate)
        userPreferencesManager.updateLastResetTimestamp(cycleStartDate.toStartOfDayMillis())
        userPreferencesManager.updateLastSeenDate(currentDateProvider.currentDate())

        if (previousExpensesCents > 0L) {
            val previousCycleStart = budgetCalculationService.getCycleStartDate(
                cycleStartDate.minusDays(1),
                paydayDate
            )
            monthlyHistoryDao.insert(
                MonthlyHistory(
                    cycleStartDate = previousCycleStart.toString(),
                    budgetAmountCents = monthlyBudgetCents,
                    totalSpentCents = previousExpensesCents,
                    surplusCents = budgetCalculationService.calculateSurplus(
                        monthlyBudgetCents = monthlyBudgetCents,
                        totalSpentCents = previousExpensesCents
                    ),
                    cycleEndDate = cycleStartDate.toString(),
                    endTimestamp = cycleStartDate.toStartOfDayMillis()
                )
            )
        }

        userPreferencesManager.completeOnboarding()
    }

    suspend fun checkAndPerformMonthlyReset(settings: UserSettings, now: LocalDate) {
        val lastResetDate = settings.lastResetDateOrNull() ?: return
        val currentCycleStart = budgetCalculationService.getCycleStartDate(now, settings.paydayDate)
        val existingPending = settings.pendingCycleRangeOrNull()

        if (existingPending == null) {
            recoverMissingPendingCycle(settings, currentCycleStart)
        }

        if (!budgetCalculationService.shouldPerformReset(now, settings.paydayDate, lastResetDate)) {
            return
        }

        if (existingPending != null && existingPending.endExclusive.isBefore(currentCycleStart)) {
            archiveCycleIfNeeded(settings, existingPending.start, existingPending.endExclusive)
            userPreferencesManager.clearPendingCycle()
        }

        val endedCycles = buildEndedCycles(
            fromStart = lastResetDate,
            untilExclusive = currentCycleStart,
            paydayDate = settings.paydayDate
        )
        if (endedCycles.isEmpty()) {
            userPreferencesManager.updateLastResetTimestamp(currentCycleStart.toStartOfDayMillis())
            return
        }

        endedCycles.dropLast(1).forEach { cycle ->
            archiveCycleIfNeeded(settings, cycle.start, cycle.endExclusive)
        }

        val latestEndedCycle = endedCycles.last()
        userPreferencesManager.setPendingCycle(
            cycleStartDate = latestEndedCycle.start,
            cycleEndDateExclusive = latestEndedCycle.endExclusive,
            detectedAtTimestamp = Instant.now().toEpochMilli()
        )
        userPreferencesManager.updateLastResetTimestamp(currentCycleStart.toStartOfDayMillis())
    }

    suspend fun concludePendingCycle(settings: UserSettings) {
        val pendingCycle = settings.pendingCycleRangeOrNull() ?: return
        archiveCycleIfNeeded(settings, pendingCycle.start, pendingCycle.endExclusive)
        userPreferencesManager.clearPendingCycle()
    }

    fun getMonthlyHistory(): Flow<List<MonthlyHistory>> {
        return monthlyHistoryDao.getAllHistory().map { history ->
            history
                .filter { it.totalSpentCents > 0L }
                .sortedByDescending { it.endTimestamp }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getSpendingForecast(): Flow<SpendingForecast> {
        val nowFlow = getEffectiveCurrentDate()

        val recentExpensesFlow = nowFlow
            .map { now ->
                now.minusDays(ForecastConfig.HISTORICAL_DAYS_LOOKBACK.toLong()).toString()
            }
            .distinctUntilChanged()
            .flatMapLatest { lookbackDate ->
                expenseDao.getExpensesSince(lookbackDate)
            }

        return combine(
            getBudgetState(),
            monthlyHistoryDao.getAllHistory(),
            nowFlow,
            recentExpensesFlow
        ) { budgetState, history, now, recentExpenses ->
            val usableHistory = history.filter { it.totalSpentCents > 0L }
            budgetCalculationService.calculateSpendingForecast(
                budgetState = budgetState,
                now = now,
                monthlyHistory = usableHistory,
                recentExpenses = recentExpenses
            )
        }
    }

    private suspend fun archiveCycleIfNeeded(
        settings: UserSettings,
        cycleStart: LocalDate,
        cycleEnd: LocalDate
    ) {
        val endTime = cycleEnd.toStartOfDayMillis()
        val totalSpentCents = expenseDao.getTotalSpentInRange(
            cycleStart.toString(),
            cycleEnd.toString()
        ) ?: 0L
        val expenseCount = expenseDao.getExpenseCountInRange(
            cycleStart.toString(),
            cycleEnd.toString()
        )

        if (expenseCount == 0) {
            return
        }

        val history = MonthlyHistory(
            cycleStartDate = cycleStart.toString(),
            budgetAmountCents = settings.monthlyBudgetCents,
            totalSpentCents = totalSpentCents,
            surplusCents = budgetCalculationService.calculateSurplus(
                settings.monthlyBudgetCents,
                totalSpentCents
            ),
            cycleEndDate = cycleEnd.toString(),
            endTimestamp = endTime
        )
        monthlyHistoryDao.insert(history)
    }

    private fun buildEndedCycles(
        fromStart: LocalDate,
        untilExclusive: LocalDate,
        paydayDate: Int
    ): List<CycleRange> {
        if (!fromStart.isBefore(untilExclusive)) return emptyList()

        val cycles = mutableListOf<CycleRange>()
        var cursor = fromStart
        while (cursor.isBefore(untilExclusive)) {
            val nextCycleStart = budgetCalculationService.getNextCycleStartDate(cursor, paydayDate)
            cycles += CycleRange(start = cursor, endExclusive = nextCycleStart)
            cursor = nextCycleStart
        }
        return cycles
    }

    private suspend fun recoverMissingPendingCycle(
        settings: UserSettings,
        currentCycleStart: LocalDate
    ) {
        val previousCycleStart = budgetCalculationService.getCycleStartDate(
            currentCycleStart.minusDays(1),
            settings.paydayDate
        )
        if (!previousCycleStart.isBefore(currentCycleStart)) return

        val archivedPreviousCycle = monthlyHistoryDao.getHistoryForCycle(previousCycleStart.toString())
        if (archivedPreviousCycle != null) return

        val previousCycleExpenseCount = expenseDao.getExpenseCountInRange(
            previousCycleStart.toString(),
            currentCycleStart.toString()
        )
        if (previousCycleExpenseCount == 0) return

        userPreferencesManager.setPendingCycle(
            cycleStartDate = previousCycleStart,
            cycleEndDateExclusive = currentCycleStart,
            detectedAtTimestamp = Instant.now().toEpochMilli()
        )
    }

    private fun buildContinuousDaySections(
        start: LocalDate,
        endInclusive: LocalDate,
        expensesByDate: Map<LocalDate, List<Expense>>,
        remainingBudgetForDay: (Long) -> Long?,
        isEditable: Boolean,
        today: LocalDate?
    ): List<ExpenseDaySection> {
        if (endInclusive.isBefore(start)) return emptyList()

        val sections = mutableListOf<ExpenseDaySection>()
        var currentDate = endInclusive
        while (!currentDate.isBefore(start)) {
            val expenses = expensesByDate[currentDate].orEmpty()
            val totalSpent = expenses.sumOf { it.amountCents }
            sections += ExpenseDaySection(
                date = currentDate,
                expenses = expenses,
                totalSpentCents = totalSpent,
                remainingForDayCents = remainingBudgetForDay(totalSpent),
                isToday = today == currentDate,
                isEditable = isEditable
            )
            currentDate = currentDate.minusDays(1)
        }
        return sections
    }

    private fun List<Expense>.filterByRange(
        start: LocalDate,
        endExclusive: LocalDate
    ): List<Expense> {
        val startDate = start.toString()
        val endDate = endExclusive.toString()
        return filter { expense ->
            expense.expenseDate >= startDate && expense.expenseDate < endDate
        }
    }

    private fun List<Expense>.groupByLocalDate(): Map<LocalDate, List<Expense>> {
        return groupBy(Expense::recordedDate)
    }

    private fun buildTrendSummary(daySections: List<ExpenseDaySection>): String {
        if (daySections.isEmpty()) {
            return "This cycle had very little spending activity."
        }

        val chronologicalTotals = daySections
            .map { it.totalSpentCents }
            .reversed()
        val splitIndex = (chronologicalTotals.size / 2).coerceAtLeast(1)
        val firstHalf = chronologicalTotals.take(splitIndex)
        val secondHalf = chronologicalTotals.drop(splitIndex).ifEmpty { firstHalf }

        val firstAverage = firstHalf.average()
        val secondAverage = secondHalf.average()

        return when {
            secondAverage > firstAverage * 1.15 -> "Spending accelerated in the second half of the cycle."
            secondAverage < firstAverage * 0.85 -> "Spending slowed down in the second half of the cycle."
            else -> "Spending pace stayed fairly consistent across the cycle."
        }
    }

    private fun LocalDate.toStartOfDayMillis(): Long {
        return atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun effectiveCurrentDate(settings: UserSettings, observedDate: LocalDate): LocalDate {
        return ObservedDatePolicy.resolve(
            lastSeenDate = settings.lastSeenDateOrNull(),
            observedDate = observedDate
        )
    }

    private fun UserSettings.lastResetDateOrNull(): LocalDate? {
        if (lastResetTimestamp <= 0L) return null
        return Instant.ofEpochMilli(lastResetTimestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
    }

    suspend fun syncObservedDate(settings: UserSettings, observedDate: LocalDate): LocalDate {
        val lastSeenDate = settings.lastSeenDateOrNull()
        if (ObservedDatePolicy.shouldPersist(lastSeenDate, observedDate)) {
            userPreferencesManager.updateLastSeenDate(observedDate)
        }
        return effectiveCurrentDate(settings, observedDate)
    }

    private fun UserSettings.pendingCycleRangeOrNull(): CycleRange? {
        val start = pendingCycleStartDate?.let(LocalDate::parse) ?: return null
        val end = pendingCycleEndDateExclusive?.let(LocalDate::parse) ?: return null
        return CycleRange(start = start, endExclusive = end)
    }

    private fun UserSettings.lastSeenDateOrNull(): LocalDate? {
        return lastSeenDate?.let(LocalDate::parse)
    }

    private fun buildTimelineLockState(
        settings: UserSettings,
        effectiveCurrentDate: LocalDate,
        latestExpenseDate: LocalDate?
    ): TimelineLockState {
        val currentCycleStart = budgetCalculationService.getCycleStartDate(
            now = effectiveCurrentDate,
            paydayDate = settings.paydayDate
        )
        return TimelineLockPolicy.resolve(
            effectiveCurrentDate = effectiveCurrentDate,
            currentCycleStart = currentCycleStart,
            lastResetDate = settings.lastResetDateOrNull(),
            latestExpenseDate = latestExpenseDate
        )
    }
}
