package net.loeu.wallybudget.domain.usecase.internal

import net.loeu.wallybudget.domain.model.BudgetAdjustment
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import java.time.LocalDate
import java.util.UUID

internal fun newBudgetAdjustment(
    cycleStart: LocalDate,
    effectiveDate: LocalDate,
    previousMonthlyBudgetCents: Long,
    newMonthlyBudgetCents: Long,
    installId: String,
    nowEpochMs: Long,
    hybridLogicalClockService: HybridLogicalClockService
): BudgetAdjustment {
    return BudgetAdjustment(
        adjustmentUuid = UUID.randomUUID().toString(),
        cycleStartDate = cycleStart.toString(),
        effectiveDate = effectiveDate.toString(),
        previousMonthlyBudgetCents = previousMonthlyBudgetCents,
        newMonthlyBudgetCents = newMonthlyBudgetCents,
        originInstallId = installId,
        lastModifiedByInstallId = installId,
        createdAtEpochMs = nowEpochMs,
        updatedAtEpochMs = nowEpochMs,
        deletedAtEpochMs = null,
        modClock = hybridLogicalClockService.format(nowEpochMs, 0, installId)
    )
}
