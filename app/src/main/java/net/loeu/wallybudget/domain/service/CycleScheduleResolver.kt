package net.loeu.wallybudget.domain.service

import net.loeu.wallybudget.domain.model.BudgetPolicy
import net.loeu.wallybudget.domain.model.UserSettings
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

data class ResolvedCyclePolicy(
    val cycleStart: LocalDate,
    val cycleEndExclusive: LocalDate,
    val budgetAmountCents: Long,
    val paydayDayOfMonth: Int
)

data class ScheduledPaydayTransition(
    val bridgeCycle: ResolvedCyclePolicy?,
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

    fun planPaydayTransition(
        currentCycleEndExclusive: LocalDate,
        targetMonthlyBudgetCents: Long,
        newPaydayDayOfMonth: Int
    ): ScheduledPaydayTransition {
        val bridgeEnd = firstOccurrenceOnOrAfter(currentCycleEndExclusive, newPaydayDayOfMonth)
        val bridgeCycle = if (bridgeEnd.isAfter(currentCycleEndExclusive)) {
            val firstRegularEnd = budgetCalculationService.getNextCycleStartDate(bridgeEnd, newPaydayDayOfMonth)
            val fullTargetCycleDays =
                ChronoUnit.DAYS.between(bridgeEnd, firstRegularEnd).toInt().coerceAtLeast(1)
            val bridgeDays =
                ChronoUnit.DAYS.between(currentCycleEndExclusive, bridgeEnd).toInt().coerceAtLeast(0)
            val bridgeBudgetCents =
                ((targetMonthlyBudgetCents.toDouble() * bridgeDays) / fullTargetCycleDays).roundToLong()
            ResolvedCyclePolicy(
                cycleStart = currentCycleEndExclusive,
                cycleEndExclusive = bridgeEnd,
                budgetAmountCents = bridgeBudgetCents,
                paydayDayOfMonth = newPaydayDayOfMonth
            )
        } else {
            null
        }
        val firstRegularStart = bridgeCycle?.cycleEndExclusive ?: currentCycleEndExclusive
        return ScheduledPaydayTransition(
            bridgeCycle = bridgeCycle,
            firstRegularCycle = ResolvedCyclePolicy(
                cycleStart = firstRegularStart,
                cycleEndExclusive = budgetCalculationService.getNextCycleStartDate(
                    firstRegularStart,
                    newPaydayDayOfMonth
                ),
                budgetAmountCents = targetMonthlyBudgetCents,
                paydayDayOfMonth = newPaydayDayOfMonth
            )
        )
    }

    private fun syntheticPolicyForDate(date: LocalDate, settings: UserSettings): ResolvedCyclePolicy {
        val cycleStart = budgetCalculationService.getCycleStartDate(date, settings.paydayDate)
        return ResolvedCyclePolicy(
            cycleStart = cycleStart,
            cycleEndExclusive = budgetCalculationService.getNextCycleStartDate(
                cycleStart,
                settings.paydayDate
            ),
            budgetAmountCents = settings.monthlyBudgetCents,
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
