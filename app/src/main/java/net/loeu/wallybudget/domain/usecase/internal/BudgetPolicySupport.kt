package net.loeu.wallybudget.domain.usecase.internal

import net.loeu.wallybudget.domain.model.BudgetPolicy
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import java.time.LocalDate
import java.util.UUID

internal fun newBudgetPolicy(
    cycleStart: LocalDate,
    cycleEndExclusive: LocalDate,
    budgetAmountCents: Long,
    paydayDayOfMonth: Int,
    installId: String,
    nowEpochMs: Long,
    hybridLogicalClockService: HybridLogicalClockService
): BudgetPolicy {
    return BudgetPolicy(
        policyUuid = UUID.randomUUID().toString(),
        cycleStartDate = cycleStart.toString(),
        cycleEndDateExclusive = cycleEndExclusive.toString(),
        budgetAmountCents = budgetAmountCents,
        paydayDayOfMonth = paydayDayOfMonth,
        originInstallId = installId,
        lastModifiedByInstallId = installId,
        createdAtEpochMs = nowEpochMs,
        updatedAtEpochMs = nowEpochMs,
        deletedAtEpochMs = null,
        modClock = hybridLogicalClockService.format(nowEpochMs, 0, installId)
    )
}
