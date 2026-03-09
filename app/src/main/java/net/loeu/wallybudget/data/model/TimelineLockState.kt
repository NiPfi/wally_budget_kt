package net.loeu.wallybudget.data.model

data class TimelineLockState(
    val isLocked: Boolean = false,
    val reason: String? = null
)
