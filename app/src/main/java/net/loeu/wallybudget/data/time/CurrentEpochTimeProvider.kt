package net.loeu.wallybudget.data.time

/**
 * Injectable source for current epoch time when business logic needs a controllable clock.
 *
 * Prefer this in domain and view-model code. For non-injected call sites, use [WallyTime].
 */
fun interface CurrentEpochTimeProvider {
    fun currentEpochTimeMs(): Long
}

class SystemCurrentEpochTimeProvider : CurrentEpochTimeProvider {
    override fun currentEpochTimeMs(): Long = WallyTime.currentEpochTimeMs()
}
