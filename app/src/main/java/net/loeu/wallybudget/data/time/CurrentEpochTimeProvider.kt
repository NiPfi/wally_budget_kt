package net.loeu.wallybudget.data.time

fun interface CurrentEpochTimeProvider {
    fun currentEpochTimeMs(): Long
}

class SystemCurrentEpochTimeProvider : CurrentEpochTimeProvider {
    override fun currentEpochTimeMs(): Long = System.currentTimeMillis()
}
