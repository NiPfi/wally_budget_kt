package net.loeu.wallybudget.domain.usecase.internal

import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.dao.MonthlyHistoryDao
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.domain.model.BudgetAdjustment
import net.loeu.wallybudget.data.local.querymodel.ExpenseDayTotalRow
import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.domain.model.Expense
import net.loeu.wallybudget.domain.model.ExpenseDaySection
import net.loeu.wallybudget.domain.model.MonthlyHistory
import net.loeu.wallybudget.domain.model.PendingCycleCloseoutState
import net.loeu.wallybudget.domain.model.TimelineLockState
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.model.recordedDate
import net.loeu.wallybudget.domain.policy.ObservedDatePolicy
import net.loeu.wallybudget.domain.policy.TimelineLockPolicy
import net.loeu.wallybudget.domain.service.BudgetAdjustmentResolver
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.ResolvedCyclePolicy
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
    budgetAdjustmentResolver: BudgetAdjustmentResolver,
    cyclePolicy: ResolvedCyclePolicy,
    adjustments: List<BudgetAdjustment>,
    settings: UserSettings,
    cycleStart: LocalDate,
    cycleEnd: LocalDate
) {
    val totalSpentCents = expenseDao.totalSpentInRange(
        cycleStart.toString(),
        cycleEnd.toString()
    ) ?: 0L
    val budgetAmountCents = if (cyclePolicy.cycleStart == cycleStart && cyclePolicy.cycleEndExclusive == cycleEnd) {
        budgetAdjustmentResolver.resolveEffectiveCycleBudgetAmount(
            cycleStart = cycleStart,
            cycleEndExclusive = cycleEnd,
            baseMonthlyBudgetCents = cyclePolicy.budgetAmountCents,
            adjustments = adjustments
        )
    } else {
        budgetPolicyDao.findActivePolicyForCycle(cycleStart.toString())
            ?.budgetAmountCents
            ?: settings.resolvedPortfolioMonthlyBudgetCents
    }

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
    today: LocalDate,
    @Suppress("UNUSED_PARAMETER")
    history: List<MonthlyHistory>,
    totalSpentThisCycleCents: Long,
    spentTodayCents: Long,
    cyclePolicy: ResolvedCyclePolicy,
    adjustments: List<BudgetAdjustment>,
    budgetAdjustmentResolver: BudgetAdjustmentResolver,
    budgetCalculationService: BudgetCalculationService
): BudgetState {
    val currentCycleRange = net.loeu.wallybudget.domain.service.CycleDateRange(
        start = cyclePolicy.cycleStart,
        endExclusive = minOf(today.plusDays(1), cyclePolicy.cycleEndExclusive)
    )
    val resolvedCycleBudget = budgetAdjustmentResolver.resolveCycleBudget(
        cycleStart = cyclePolicy.cycleStart,
        cycleEndExclusive = cyclePolicy.cycleEndExclusive,
        baseMonthlyBudgetCents = cyclePolicy.budgetAmountCents,
        adjustments = adjustments,
        today = today
    )

    return budgetCalculationService.calculateBudgetStateForResolvedCycle(
        now = today,
        cycleStart = cyclePolicy.cycleStart,
        cycleEndExclusive = cyclePolicy.cycleEndExclusive,
        totalSpentThisCycleCents = totalSpentThisCycleCents,
        spentTodayCents = spentTodayCents,
        cycleBudgetAmountCents = resolvedCycleBudget.effectiveCycleBudgetCents,
        plannedTodayBudgetCents = resolvedCycleBudget.plannedTodayBudgetCents,
        allocatedBeforeTodayCents = resolvedCycleBudget.allocatedBeforeDateCents,
        paydayDate = cyclePolicy.paydayDayOfMonth
    )
}

internal fun buildPendingCycleCloseoutState(
    settings: UserSettings,
    expenses: List<Expense>,
    dayTotals: Map<LocalDate, Long>,
    adjustments: List<BudgetAdjustment>,
    resolvedPendingPolicy: ResolvedCyclePolicy?,
    budgetAdjustmentResolver: BudgetAdjustmentResolver
): PendingCycleCloseoutState? {
    val pendingCycle = settings.pendingCycleRangeOrNull() ?: return null
    val cycleExpenses = expenses.filterByRange(
        start = pendingCycle.start,
        endExclusive = pendingCycle.endExclusive
    )
    val dayCount = java.time.temporal.ChronoUnit.DAYS
        .between(pendingCycle.start, pendingCycle.endExclusive)
        .toInt()
        .coerceAtLeast(1)
    val cycleBudgetAmount = budgetAdjustmentResolver.resolveEffectiveCycleBudgetAmount(
        cycleStart = pendingCycle.start,
        cycleEndExclusive = pendingCycle.endExclusive,
        baseMonthlyBudgetCents = resolvedPendingPolicy?.budgetAmountCents
            ?: settings.resolvedPortfolioMonthlyBudgetCents,
        adjustments = adjustments
    )
    val baseDailyBudget = cycleBudgetAmount / dayCount
    val daySections = buildContinuousDaySections(
        start = pendingCycle.start,
        endInclusive = pendingCycle.endExclusive.minusDays(1),
        expensesByDate = cycleExpenses.groupBy { it.recordedDate() },
        dayTotals = dayTotals,
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
        .maxByOrNull { (_, categoryExpenses) -> categoryExpenses.sumOf { it.amountCents } }
        ?.key

    return PendingCycleCloseoutState(
        cycleStartDate = pendingCycle.start,
        cycleEndDateExclusive = pendingCycle.endExclusive,
        budgetAmountCents = cycleBudgetAmount,
        totalSpentCents = totalSpent,
        surplusCents = cycleBudgetAmount - totalSpent,
        averageDailySpendCents = totalSpent / dayCount,
        biggestExpense = biggestExpense,
        highestSpendDay = highestSpendDay,
        topCategory = topCategory,
        trendSummary = buildTrendSummary(daySections),
        daySections = daySections
    )
}

internal fun buildTimelineLockState(
    effectiveCurrentDate: LocalDate,
    currentCycleStart: LocalDate,
    lastResetDate: LocalDate?,
    latestExpenseDate: LocalDate?,
): TimelineLockState {
    return TimelineLockPolicy.resolve(
        effectiveCurrentDate = effectiveCurrentDate,
        currentCycleStart = currentCycleStart,
        lastResetDate = lastResetDate,
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
