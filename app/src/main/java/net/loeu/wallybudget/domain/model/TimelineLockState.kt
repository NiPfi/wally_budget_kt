package net.loeu.wallybudget.domain.model

data class TimelineLockState(
    val isLocked: Boolean = false,
    val reason: String? = null
)
