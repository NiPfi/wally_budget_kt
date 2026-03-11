package net.loeu.wallybudget.domain.usecase.internal

import java.time.LocalDate

internal data class CycleRange(
    val start: LocalDate,
    val endExclusive: LocalDate
)
