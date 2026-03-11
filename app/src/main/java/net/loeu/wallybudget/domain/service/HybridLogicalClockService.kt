package net.loeu.wallybudget.domain.service

class HybridLogicalClockService {
    fun next(
        previousClock: String?,
        nowEpochMs: Long,
        installId: String
    ): String {
        val previous = previousClock?.let(::parse)
        val previousEpoch = previous?.epochMs ?: Long.MIN_VALUE
        val nextEpoch = maxOf(nowEpochMs, previousEpoch)
        val nextCounter = if (previous != null && nextEpoch == previous.epochMs) {
            previous.counter + 1
        } else {
            0
        }
        return format(nextEpoch, nextCounter, installId)
    }

    fun format(epochMs: Long, counter: Int, installId: String): String {
        return "%013d-%04d-%s".format(epochMs, counter, installId)
    }

    private fun parse(value: String): ParsedClock {
        val firstSeparator = value.indexOf('-')
        val secondSeparator = value.indexOf('-', firstSeparator + 1)
        check(firstSeparator > 0 && secondSeparator > firstSeparator) {
            "Invalid hybrid logical clock: $value"
        }
        return ParsedClock(
            epochMs = value.substring(0, firstSeparator).toLong(),
            counter = value.substring(firstSeparator + 1, secondSeparator).toInt()
        )
    }

    private data class ParsedClock(
        val epochMs: Long,
        val counter: Int
    )
}
