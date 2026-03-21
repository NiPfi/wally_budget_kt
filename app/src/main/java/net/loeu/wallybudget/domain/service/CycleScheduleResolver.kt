package net.loeu.wallybudget.domain.service

import net.loeu.wallybudget.domain.model.BudgetPolicy
import net.loeu.wallybudget.domain.model.UserSettings
import java.time.LocalDate
data class ResolvedCyclePolicy(
    val cycleStart: LocalDate,
    val cycleEndExclusive: LocalDate,
    val budgetAmountCents: Long,
    val paydayDayOfMonth: Int
)

data class ImmediatePaydayChangePlan(
    val rewrittenCurrentCycle: ResolvedCyclePolicy,
    val originalCurrentCycleEndExclusive: LocalDate,
    val firstRegularCycle: ResolvedCyclePolicy
)

class CycleScheduleResolver(
    private val budgetCalculationService: BudgetCalculationService
) {
    fun resolvePolicyForDate(
        date: LocalDate,
        settings: UserSettings,
        policies: List<BudgetPolicy>
    ): ResolvedCyclePolicy {
        val active = policies
            .filter { it.deletedAtEpochMs == null }
            .map(::toResolvedPolicy)
            .firstOrNull { policy ->
                !date.isBefore(policy.cycleStart) && date.isBefore(policy.cycleEndExclusive)
            }
        return active ?: syntheticPolicyForDate(date, settings)
    }

    fun policyForCycleStart(
        cycleStart: LocalDate,
        settings: UserSettings,
        policies: List<BudgetPolicy>
    ): ResolvedCyclePolicy {
        val persisted = policies
            .filter { it.deletedAtEpochMs == null }
            .map(::toResolvedPolicy)
            .firstOrNull { it.cycleStart == cycleStart }
        return persisted ?: syntheticPolicyForDate(cycleStart, settings)
    }

    fun findPreviousCycleStart(
        date: LocalDate,
        settings: UserSettings,
        policies: List<BudgetPolicy>
    ): LocalDate {
        val current = resolvePolicyForDate(date, settings, policies)
        return resolvePolicyForDate(current.cycleStart.minusDays(1), settings, policies).cycleStart
    }

    fun completedCyclesBetween(
        fromStartInclusive: LocalDate,
        untilStartExclusive: LocalDate,
        settings: UserSettings,
        policies: List<BudgetPolicy>
    ): List<ResolvedCyclePolicy> {
        if (!fromStartInclusive.isBefore(untilStartExclusive)) return emptyList()

        val cycles = mutableListOf<ResolvedCyclePolicy>()
        var cursor = fromStartInclusive
        while (cursor.isBefore(untilStartExclusive)) {
            val policy = policyForCycleStart(cursor, settings, policies)
            cycles += policy
            cursor = policy.cycleEndExclusive
        }
        return cycles.filter { it.cycleStart.isBefore(untilStartExclusive) }
    }

    fun firstOccurrenceOnOrAfter(anchor: LocalDate, paydayDayOfMonth: Int): LocalDate {
        val thisMonthDay = minOf(paydayDayOfMonth.coerceIn(1, 31), anchor.lengthOfMonth())
        val thisMonthOccurrence = anchor.withDayOfMonth(thisMonthDay)
        if (!thisMonthOccurrence.isBefore(anchor)) {
            return thisMonthOccurrence
        }
        val nextMonth = anchor.plusMonths(1)
        return nextMonth.withDayOfMonth(minOf(paydayDayOfMonth.coerceIn(1, 31), nextMonth.lengthOfMonth()))
    }

    fun planImmediatePaydayChange(
        currentCycle: ResolvedCyclePolicy,
        today: LocalDate,
        targetMonthlyBudgetCents: Long,
        newPaydayDayOfMonth: Int
    ): ImmediatePaydayChangePlan {
        val rewrittenCurrentCycleEnd = nextOccurrenceWithinCurrentCycle(
            today = today,
            currentCycleEndExclusive = currentCycle.cycleEndExclusive,
            newPaydayDayOfMonth = newPaydayDayOfMonth
        ) ?: firstOccurrenceOnOrAfter(currentCycle.cycleEndExclusive, newPaydayDayOfMonth)

        return ImmediatePaydayChangePlan(
            rewrittenCurrentCycle = currentCycle.copy(
                cycleEndExclusive = rewrittenCurrentCycleEnd,
                paydayDayOfMonth = newPaydayDayOfMonth
            ),
            originalCurrentCycleEndExclusive = currentCycle.cycleEndExclusive,
            firstRegularCycle = ResolvedCyclePolicy(
                cycleStart = rewrittenCurrentCycleEnd,
                cycleEndExclusive = budgetCalculationService.getNextCycleStartDate(
                    rewrittenCurrentCycleEnd,
                    newPaydayDayOfMonth
                ),
                budgetAmountCents = targetMonthlyBudgetCents,
                paydayDayOfMonth = newPaydayDayOfMonth
            )
        )
    }

    fun nextOccurrenceWithinCurrentCycle(
        today: LocalDate,
        currentCycleEndExclusive: LocalDate,
        newPaydayDayOfMonth: Int
    ): LocalDate? {
        val candidate = firstOccurrenceOnOrAfter(today.plusDays(1), newPaydayDayOfMonth)
        return candidate.takeIf { it.isBefore(currentCycleEndExclusive) }
    }

    private fun syntheticPolicyForDate(date: LocalDate, settings: UserSettings): ResolvedCyclePolicy {
        val cycleStart = budgetCalculationService.getCycleStartDate(date, settings.paydayDate)
        return ResolvedCyclePolicy(
            cycleStart = cycleStart,
            cycleEndExclusive = budgetCalculationService.getNextCycleStartDate(
                cycleStart,
                settings.paydayDate
            ),
            budgetAmountCents = settings.resolvedPortfolioMonthlyBudgetCents,
            paydayDayOfMonth = settings.paydayDate
        )
    }

    private fun toResolvedPolicy(policy: BudgetPolicy): ResolvedCyclePolicy {
        return ResolvedCyclePolicy(
            cycleStart = policy.cycleStart(),
            cycleEndExclusive = policy.cycleEndExclusive(),
            budgetAmountCents = policy.budgetAmountCents,
            paydayDayOfMonth = policy.paydayDayOfMonth
        )
    }
}
