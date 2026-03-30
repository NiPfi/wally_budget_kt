package net.loeu.wallybudget.data.time

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Injectable source for the current observed calendar date.
 *
 * Prefer this in domain and view-model code. For non-injected call sites, use [WallyTime].
 */
interface CurrentDateProvider {
    fun currentDate(): LocalDate
    fun observeCurrentDate(): Flow<LocalDate>
}

class SystemCurrentDateProvider(
    private val context: Context,
    private val zoneId: ZoneId = WallyTime.systemZoneId()
) : CurrentDateProvider {

    override fun currentDate(): LocalDate = WallyTime.currentDate(zoneId)

    override fun observeCurrentDate(): Flow<LocalDate> = callbackFlow {
        fun emitCurrentDate() {
            trySend(currentDate())
        }

        emitCurrentDate()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                emitCurrentDate()
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val midnightRefreshJob = launch {
            while (isActive) {
                kotlinx.coroutines.delay(millisUntilNextMidnight())
                emitCurrentDate()
            }
        }

        awaitClose {
            midnightRefreshJob.cancel()
            context.unregisterReceiver(receiver)
        }
    }.distinctUntilChanged()

    private fun millisUntilNextMidnight(): Long {
        val now = WallyTime.currentLocalDateTime(zoneId)
        val today = now.toLocalDate()
        val nextMidnight = today.plusDays(1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val nowMillis = now.atZone(zoneId).toInstant().toEpochMilli()
        return (nextMidnight - nowMillis).coerceAtLeast(1L)
    }
}
