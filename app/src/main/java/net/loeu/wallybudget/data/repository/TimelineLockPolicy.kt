package net.loeu.wallybudget.data.repository

import net.loeu.wallybudget.data.model.TimelineLockState
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal object TimelineLockPolicy {
    private val displayDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

    fun resolve(
        effectiveCurrentDate: LocalDate,
        currentCycleStart: LocalDate,
        lastResetDate: LocalDate?,
        latestExpenseDate: LocalDate?
    ): TimelineLockState {
        val reopenedClosedCycle = lastResetDate?.isAfter(currentCycleStart) == true
        val hasFutureExpenses = latestExpenseDate?.isAfter(effectiveCurrentDate) == true

        if (!reopenedClosedCycle && !hasFutureExpenses) {
            return TimelineLockState()
        }

        val resetDateText = lastResetDate?.display()
        val latestExpenseDateText = latestExpenseDate?.display()
        val reason = when {
            reopenedClosedCycle && hasFutureExpenses -> {
                "Your device date is ${effectiveCurrentDate.display()}, but WallyBudget already advanced to the cycle that started $resetDateText and has expenses recorded through $latestExpenseDateText. Expense changes are locked until the device date catches up."
            }
            reopenedClosedCycle -> {
                "Your device date is ${effectiveCurrentDate.display()}, but WallyBudget already advanced to the cycle that started $resetDateText. Expense changes are locked until the device date catches up."
            }
            else -> {
                "Your device date is ${effectiveCurrentDate.display()}, but WallyBudget has expenses recorded through $latestExpenseDateText. Expense changes are locked until the device date catches up."
            }
        }

        return TimelineLockState(
            isLocked = true,
            reason = reason
        )
    }

    private fun LocalDate.display(): String = format(displayDateFormatter)
}
