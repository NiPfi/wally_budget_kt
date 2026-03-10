package net.loeu.wallybudget.domain.policy

import net.loeu.wallybudget.domain.model.TimelineLockState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal object TimelineLockPolicy {
    private const val DISPLAY_DATE_PATTERN = "MMM d, yyyy"

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
                buildString {
                    append("Your device date is ${effectiveCurrentDate.display()}, ")
                    append("but WallyBudget already advanced to the cycle that started ")
                    append("$resetDateText and has expenses recorded through ")
                    append("$latestExpenseDateText. Expense changes are locked until ")
                    append("the device date catches up.")
                }
            }
            reopenedClosedCycle -> {
                buildString {
                    append("Your device date is ${effectiveCurrentDate.display()}, ")
                    append("but WallyBudget already advanced to the cycle that started ")
                    append("$resetDateText. Expense changes are locked until the ")
                    append("device date catches up.")
                }
            }
            else -> {
                buildString {
                    append("Your device date is ${effectiveCurrentDate.display()}, ")
                    append("but WallyBudget has expenses recorded through ")
                    append("$latestExpenseDateText. Expense changes are locked until ")
                    append("the device date catches up.")
                }
            }
        }

        return TimelineLockState(
            isLocked = true,
            reason = reason
        )
    }

    private fun LocalDate.display(): String {
        return format(
            DateTimeFormatter
                .ofPattern(DISPLAY_DATE_PATTERN)
                .withLocale(Locale.getDefault())
        )
    }
}
