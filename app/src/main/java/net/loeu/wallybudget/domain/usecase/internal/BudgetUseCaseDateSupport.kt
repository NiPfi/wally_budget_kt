package net.loeu.wallybudget.domain.usecase.internal

import net.loeu.wallybudget.data.time.WallyTime
import net.loeu.wallybudget.domain.model.UserSettings
import java.time.LocalDate
import java.time.format.DateTimeParseException

internal fun UserSettings.lastResetDateOrNull(): LocalDate? {
    if (lastResetTimestamp <= 0L) return null
    return WallyTime.localDateAtEpochTimeMs(lastResetTimestamp)
}

internal fun UserSettings.lastSeenDateOrNull(): LocalDate? = lastSeenDate?.parseLocalDateOrNull()

internal fun UserSettings.pendingCycleRangeOrNull(): CycleRange? {
    val start = pendingCycleStartDate?.parseLocalDateOrNull()
    val end = pendingCycleEndDateExclusive?.parseLocalDateOrNull()
    return if (start != null && end != null && end.isAfter(start)) {
        CycleRange(start = start, endExclusive = end)
    } else {
        null
    }
}

internal fun LocalDate.toStartOfDayMillis(): Long {
    return WallyTime.startOfDayEpochTimeMs(this)
}

private fun String.parseLocalDateOrNull(): LocalDate? {
    return try {
        LocalDate.parse(this)
    } catch (_: DateTimeParseException) {
        null
    }
}
