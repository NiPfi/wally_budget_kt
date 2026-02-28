package net.loeu.wallybudget.data.time

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import net.loeu.wallybudget.BuildConfig
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

interface CurrentDateProvider {
    fun observeCurrentDate(): Flow<LocalDate>
}

class SystemCurrentDateProvider(
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : CurrentDateProvider {

    companion object {
        private const val DEBUG_DATE_REFRESH_INTERVAL_MS = 5_000L
    }

    override fun observeCurrentDate(): Flow<LocalDate> = flow {
        var lastEmittedDate: LocalDate? = null

        while (true) {
            val now = LocalDateTime.now(zoneId)
            val today = now.toLocalDate()

            if (today != lastEmittedDate) {
                emit(today)
                lastEmittedDate = today
            }

            val nextMidnight = today.plusDays(1)
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli()
            val nowMillis = now.atZone(zoneId).toInstant().toEpochMilli()
            val millisUntilMidnight = (nextMidnight - nowMillis).coerceAtLeast(1L)
            val delayMillis = getDateRefreshDelayMillis(millisUntilMidnight)
            delay(delayMillis)
        }
    }

    /**
     * Date refresh policy:
     * - Debug builds poll periodically for faster rollover feedback during development/testing.
     * - Release builds wait until next midnight to minimize wakeups and resource usage.
     */
    private fun getDateRefreshDelayMillis(millisUntilMidnight: Long): Long {
        return if (BuildConfig.USE_ACTIVE_DATE_POLLING) {
            minOf(millisUntilMidnight, DEBUG_DATE_REFRESH_INTERVAL_MS)
        } else {
            millisUntilMidnight
        }
    }
}
