package net.loeu.wallybudget.data.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Central shim for wall-clock access in application code.
 *
 * New production code should call this object, or an injected [CurrentDateProvider] /
 * [CurrentEpochTimeProvider], instead of reaching straight for `LocalDate.now()`,
 * `Instant.now()`, `System.currentTimeMillis()`, or `ZoneId.systemDefault()`.
 *
 * Keeping time access here makes it easy to audit, easy to wrap with higher-level providers,
 * and easy to block raw clock calls in CI.
 */
object WallyTime {

    fun systemZoneId(): ZoneId = ZoneId.systemDefault()

    fun currentEpochTimeMs(): Long = System.currentTimeMillis()

    fun currentDate(zoneId: ZoneId = systemZoneId()): LocalDate =
        Instant.ofEpochMilli(currentEpochTimeMs())
            .atZone(zoneId)
            .toLocalDate()

    fun currentLocalDateTime(zoneId: ZoneId = systemZoneId()): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(currentEpochTimeMs()), zoneId)

    fun startOfDayEpochTimeMs(
        date: LocalDate,
        zoneId: ZoneId = systemZoneId()
    ): Long = date.atStartOfDay(zoneId).toInstant().toEpochMilli()

    fun localDateAtEpochTimeMs(
        epochTimeMs: Long,
        zoneId: ZoneId = systemZoneId()
    ): LocalDate = Instant.ofEpochMilli(epochTimeMs).atZone(zoneId).toLocalDate()

    fun zonedDateTimeAtEpochTimeMs(
        epochTimeMs: Long,
        zoneId: ZoneId = systemZoneId()
    ): ZonedDateTime = Instant.ofEpochMilli(epochTimeMs).atZone(zoneId)
}
