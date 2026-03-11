package net.loeu.wallybudget.domain.usecase.internal

import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.dao.MonthlyHistoryDao
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.data.local.querymodel.ExpenseDayTotalRow
import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.ExpenseDaySection
import net.loeu.wallybudget.domain.model.MonthlyHistory
import net.loeu.wallybudget.domain.model.TimelineLockState
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.policy.ObservedDatePolicy
import net.loeu.wallybudget.domain.policy.TimelineLockPolicy
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import java.time.LocalDate

internal fun effectiveCurrentDate(
    settings: UserSettings,
    observedDate: LocalDate
): LocalDate {
    return ObservedDatePolicy.resolve(
        lastSeenDate = settings.lastSeenDateOrNull(),
        observedDate = observedDate
    )
}

internal fun List<Expense>.filterByRange(
    start: LocalDate,
    endExclusive: LocalDate
): List<Expense> {
    val startDate = start.toString()
    val endDate = endExclusive.toString()
    return filter { expense ->
        expense.expenseDate in startDate..<endDate
    }
}

internal fun List<ExpenseDayTotalRow>.toDayTotalsMap(): Map<LocalDate, Long> =
    associate { row -> LocalDate.parse(row.expenseDate) to row.totalSpentCents }

internal suspend fun archiveCycleIfNeeded(
    expenseDao: ExpenseDao,
    budgetPolicyDao: BudgetPolicyDao,
    monthlyHistoryDao: MonthlyHistoryDao,
    budgetCalculationService: BudgetCalculationService,
    settings: UserSettings,
    cycleStart: LocalDate,
    cycleEnd: LocalDate
) {
    val totalSpentCents = expenseDao.totalSpentInRange(
        cycleStart.toString(),
        cycleEnd.toString()
    ) ?: 0L
    val budgetAmountCents = budgetPolicyDao.findActivePolicyForCycle(cycleStart.toString())
        ?.budgetAmountCents
        ?: settings.monthlyBudgetCents

    monthlyHistoryDao.insert(
        MonthlyHistory(
            cycleStartDate = cycleStart.toString(),
            budgetAmountCents = budgetAmountCents,
            totalSpentCents = totalSpentCents,
            surplusCents = budgetCalculationService.calculateSurplus(
                budgetAmountCents,
                totalSpentCents
            ),
            cycleEndDate = cycleEnd.toString(),
            endTimestamp = cycleEnd.toStartOfDayMillis()
        ).toEntity()
    )
}

internal fun buildBudgetState(
    settings: UserSettings,
    today: LocalDate,
    history: List<MonthlyHistory>,
    totalSpentThisCycleCents: Long,
    spentTodayCents: Long,
    currentCycleBudgetAmountCents: Long = settings.monthlyBudgetCents,
    budgetCalculationService: BudgetCalculationService
): BudgetState {
    val currentCycleRange = budgetCalculationService.getCurrentCycleProgressRange(
        now = today,
        paydayDate = settings.paydayDate
    )
    val cumulativeSavingsCents = history
        .filter { !it.getCycleEnd().isAfter(currentCycleRange.start) }
        .sumOf { it.surplusCents }

    return budgetCalculationService.calculateBudgetState(
        settings = settings,
        now = today,
        totalSpentThisCycleCents = totalSpentThisCycleCents,
        spentTodayCents = spentTodayCents,
        cumulativeSavingsCents = cumulativeSavingsCents,
        cycleBudgetAmountCents = currentCycleBudgetAmountCents
    )
}

internal fun buildTimelineLockState(
    settings: UserSettings,
    effectiveCurrentDate: LocalDate,
    latestExpenseDate: LocalDate?,
    budgetCalculationService: BudgetCalculationService
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

internal fun buildContinuousDaySections(
    start: LocalDate,
    endInclusive: LocalDate,
    expensesByDate: Map<LocalDate, List<Expense>>,
    dayTotals: Map<LocalDate, Long> = emptyMap(),
    remainingBudgetForDay: (Long) -> Long?,
    isEditable: Boolean,
    today: LocalDate?
): List<ExpenseDaySection> {
    if (endInclusive.isBefore(start)) return emptyList()

    val sections = mutableListOf<ExpenseDaySection>()
    var currentDate = endInclusive
    while (!currentDate.isBefore(start)) {
        val expenses = expensesByDate[currentDate].orEmpty()
        val totalSpent = dayTotals[currentDate] ?: expenses.sumOf { it.amountCents }
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

internal fun buildTrendSummary(daySections: List<ExpenseDaySection>): String {
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
