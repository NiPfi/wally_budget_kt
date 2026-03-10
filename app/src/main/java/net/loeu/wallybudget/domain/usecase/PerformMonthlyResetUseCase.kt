package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.dao.MonthlyHistoryDao
import net.loeu.wallybudget.data.local.db.TransactionRunner
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.domain.model.MonthlyHistory
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.usecase.internal.CycleRange
import net.loeu.wallybudget.domain.usecase.internal.lastResetDateOrNull
import net.loeu.wallybudget.domain.usecase.internal.pendingCycleRangeOrNull
import net.loeu.wallybudget.domain.usecase.internal.toStartOfDayMillis
import java.time.Instant
import java.time.LocalDate

class PerformMonthlyResetUseCase(
    private val transactionRunner: TransactionRunner,
    private val expenseDao: ExpenseDao,
    private val monthlyHistoryDao: MonthlyHistoryDao,
    private val userSettingsStore: UserSettingsStore,
    private val budgetCalculationService: BudgetCalculationService
) {
    suspend operator fun invoke(settings: UserSettings, now: LocalDate) {
        val lastResetDate = settings.lastResetDateOrNull() ?: return
        val currentCycleStart = budgetCalculationService.getCycleStartDate(now, settings.paydayDate)
        val existingPending = settings.pendingCycleRangeOrNull()
        val recoveryPendingCycle = if (existingPending == null) {
            recoverMissingPendingCycle(settings, currentCycleStart)
        } else {
            null
        }

        if (!budgetCalculationService.shouldPerformReset(now, settings.paydayDate, lastResetDate)) {
            recoveryPendingCycle?.let { pendingCycle ->
                userSettingsStore.setPendingCycle(
                    cycleStartDate = pendingCycle.start,
                    cycleEndDateExclusive = pendingCycle.endExclusive,
                    detectedAtTimestamp = Instant.now().toEpochMilli()
                )
            }
            return
        }

        var clearPending = false
        var nextPendingCycle: CycleRange? = null

        transactionRunner.inTransaction {
            if (existingPending != null && existingPending.endExclusive.isBefore(currentCycleStart)) {
                archiveCycleIfNeeded(settings, existingPending.start, existingPending.endExclusive)
                clearPending = true
            }

            val endedCycles = buildEndedCycles(
                fromStart = lastResetDate,
                untilExclusive = currentCycleStart,
                paydayDate = settings.paydayDate
            )

            if (endedCycles.isEmpty()) {
                return@inTransaction
            }

            endedCycles.dropLast(1).forEach { cycle ->
                archiveCycleIfNeeded(settings, cycle.start, cycle.endExclusive)
            }
            nextPendingCycle = endedCycles.last()
        }

        if (clearPending) {
            userSettingsStore.clearPendingCycle()
        }
        when {
            nextPendingCycle != null -> userSettingsStore.setPendingCycle(
                cycleStartDate = nextPendingCycle.start,
                cycleEndDateExclusive = nextPendingCycle.endExclusive,
                detectedAtTimestamp = Instant.now().toEpochMilli()
            )

            recoveryPendingCycle != null -> userSettingsStore.setPendingCycle(
                cycleStartDate = recoveryPendingCycle.start,
                cycleEndDateExclusive = recoveryPendingCycle.endExclusive,
                detectedAtTimestamp = Instant.now().toEpochMilli()
            )
        }
        userSettingsStore.updateLastResetTimestamp(currentCycleStart.toStartOfDayMillis())
    }

    private suspend fun recoverMissingPendingCycle(
        settings: UserSettings,
        currentCycleStart: LocalDate
    ): CycleRange? {
        val previousCycleStart = budgetCalculationService.getCycleStartDate(
            currentCycleStart.minusDays(1),
            settings.paydayDate
        )
        if (!previousCycleStart.isBefore(currentCycleStart)) return null

        val archivedPreviousCycle = monthlyHistoryDao.findByCycleStart(previousCycleStart.toString())
        if (archivedPreviousCycle != null) return null

        val previousCycleExpenseCount = expenseDao.countInRange(
            previousCycleStart.toString(),
            currentCycleStart.toString()
        )
        if (previousCycleExpenseCount == 0) return null

        return CycleRange(
            start = previousCycleStart,
            endExclusive = currentCycleStart
        )
    }

    private suspend fun archiveCycleIfNeeded(
        settings: UserSettings,
        cycleStart: LocalDate,
        cycleEnd: LocalDate
    ) {
        val totalSpentCents = expenseDao.totalSpentInRange(
            cycleStart.toString(),
            cycleEnd.toString()
        ) ?: 0L
        val expenseCount = expenseDao.countInRange(
            cycleStart.toString(),
            cycleEnd.toString()
        )
        if (expenseCount == 0) {
            return
        }

        monthlyHistoryDao.insert(
            MonthlyHistory(
                cycleStartDate = cycleStart.toString(),
                budgetAmountCents = settings.monthlyBudgetCents,
                totalSpentCents = totalSpentCents,
                surplusCents = budgetCalculationService.calculateSurplus(
                    settings.monthlyBudgetCents,
                    totalSpentCents
                ),
                cycleEndDate = cycleEnd.toString(),
                endTimestamp = cycleEnd.toStartOfDayMillis()
            ).toEntity()
        )
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
}
