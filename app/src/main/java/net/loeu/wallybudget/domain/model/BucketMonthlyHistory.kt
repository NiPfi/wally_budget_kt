package net.loeu.wallybudget.domain.model

import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

data class BucketMonthlyHistory(
    val bucketUuid: String,
    val cycleStartDate: String,
    val budgetAmountCents: Long,
    val totalSpentCents: Long,
    val surplusCents: Long,
    val cycleEndDate: String,
    val endTimestamp: Long
) {
    private val parsedCycleStart: LocalDate by lazy {
        try {
            LocalDate.parse(cycleStartDate)
        } catch (exception: DateTimeParseException) {
            throw IllegalStateException("Invalid cycleStartDate: '$cycleStartDate'", exception)
        }
    }

    private val parsedCycleEnd: LocalDate by lazy {
        try {
            LocalDate.parse(cycleEndDate)
        } catch (exception: DateTimeParseException) {
            throw IllegalStateException("Invalid cycleEndDate: '$cycleEndDate'", exception)
        }
    }

    fun getCycleStart(): LocalDate = parsedCycleStart

    fun getCycleEnd(): LocalDate = parsedCycleEnd

    fun getDayCount(): Int {
        return ChronoUnit.DAYS.between(getCycleStart(), getCycleEnd()).toInt().coerceAtLeast(1)
    }
}

fun BucketMonthlyHistory.toMonthlyHistory(): MonthlyHistory {
    return MonthlyHistory(
        cycleStartDate = cycleStartDate,
        budgetAmountCents = budgetAmountCents,
        totalSpentCents = totalSpentCents,
        surplusCents = surplusCents,
        cycleEndDate = cycleEndDate,
        endTimestamp = endTimestamp
    )
}
