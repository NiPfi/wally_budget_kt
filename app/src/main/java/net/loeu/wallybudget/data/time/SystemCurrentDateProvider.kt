package net.loeu.wallybudget.data.time

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import net.loeu.wallybudget.BuildConfig
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

interface CurrentDateProvider {
    fun currentDate(): LocalDate
    fun observeCurrentDate(): Flow<LocalDate>
}

class SystemCurrentDateProvider(
    private val context: Context,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : CurrentDateProvider {

    companion object {
        private const val DEBUG_DATE_REFRESH_INTERVAL_MS = 5_000L
    }

    override fun currentDate(): LocalDate = LocalDate.now(zoneId)

    override fun observeCurrentDate(): Flow<LocalDate> {
        return if (BuildConfig.USE_ACTIVE_DATE_POLLING) {
            pollingDateFlow()
        } else {
            systemEventDateFlow()
        }
    }

    private fun pollingDateFlow(): Flow<LocalDate> = flow {
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
            delay(minOf(millisUntilMidnight, DEBUG_DATE_REFRESH_INTERVAL_MS))
        }
    }

    private fun systemEventDateFlow(): Flow<LocalDate> = callbackFlow {
        trySend(currentDate())

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(currentDate())
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

        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }.distinctUntilChanged()
}
