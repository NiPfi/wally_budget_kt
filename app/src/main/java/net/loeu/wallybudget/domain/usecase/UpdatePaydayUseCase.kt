@file:Suppress("LongMethod")

package net.loeu.wallybudget.domain.usecase

import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.db.TransactionRunner
import net.loeu.wallybudget.data.local.entity.toDomainModel
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.data.time.CurrentDateProvider
import net.loeu.wallybudget.domain.model.BudgetPolicy
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import net.loeu.wallybudget.domain.service.ResolvedCyclePolicy
import net.loeu.wallybudget.domain.usecase.internal.newBudgetPolicy
import java.time.LocalDate
import java.time.ZoneId

data class UpdatePaydayRequest(
    val paydayDate: Int
)

data class UpdatePaydayResult(
    val summaryMessage: String
)

class UpdatePaydayUseCase(
    private val transactionRunner: TransactionRunner,
    private val userSettingsStore: UserSettingsStore,
    private val budgetPolicyDao: BudgetPolicyDao,
    private val currentDateProvider: CurrentDateProvider,
    private val cycleScheduleResolver: CycleScheduleResolver,
    private val hybridLogicalClockService: HybridLogicalClockService
) {
    private val syncObservedDateUseCase = SyncObservedDateUseCase(userSettingsStore)

    suspend operator fun invoke(request: UpdatePaydayRequest): UpdatePaydayResult {
        require(request.paydayDate in 1..31) { "Payday must be between 1 and 31." }

        val settings = userSettingsStore.ensureIdentity()
        if (request.paydayDate == settings.paydayDate) {
            return UpdatePaydayResult("No payday changes.")
        }

        val today = syncObservedDateUseCase(settings, currentDateProvider.currentDate())
        val policies = budgetPolicyDao.getAllForSnapshot()
            .filter { it.deletedAtEpochMs == null }
            .map { it.toDomainModel() }
            .sortedBy { it.cycleStartDate }
        val currentPolicy = cycleScheduleResolver.resolvePolicyForDate(today, settings, policies)

        transactionRunner.inTransaction {
            replaceNextCyclePolicy(
                settings = settings,
                currentPolicy = currentPolicy,
                targetPayday = request.paydayDate,
                today = today,
                futurePolicies = policies.filter { it.cycleStart() == currentPolicy.cycleEndExclusive }
            )
        }

        userSettingsStore.updatePaydayDate(request.paydayDate)
        userSettingsStore.clearPendingPaydayUndo()
        return UpdatePaydayResult(
            "Payday changes from ${settings.paydayDate} to ${request.paydayDate} on ${currentPolicy.cycleEndExclusive}."
        )
    }

    private suspend fun replaceNextCyclePolicy(
        settings: net.loeu.wallybudget.domain.model.UserSettings,
        currentPolicy: ResolvedCyclePolicy,
        targetPayday: Int,
        today: LocalDate,
        futurePolicies: List<BudgetPolicy>
    ) {
        val nowEpochMs = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        futurePolicies.forEach { policy ->
            val entity = budgetPolicyDao.findByPolicyUuid(policy.policyUuid) ?: return@forEach
            budgetPolicyDao.update(
                entity.copy(
                    deletedAtEpochMs = nowEpochMs,
                    updatedAtEpochMs = nowEpochMs,
                    lastModifiedByInstallId = settings.installDeviceId,
                    modClock = hybridLogicalClockService.next(entity.modClock, nowEpochMs, settings.installDeviceId)
                )
            )
        }

        val nextCycleStart = currentPolicy.cycleEndExclusive
        val nextCycle = cycleScheduleResolver.policyForCycleStart(
            cycleStart = nextCycleStart,
            settings = settings.copy(paydayDate = targetPayday),
            policies = emptyList()
        )
        budgetPolicyDao.insert(
            newBudgetPolicy(
                cycleStart = nextCycleStart,
                cycleEndExclusive = nextCycle.cycleEndExclusive,
                budgetAmountCents = settings.resolvedPortfolioMonthlyBudgetCents,
                paydayDayOfMonth = targetPayday,
                installId = settings.installDeviceId,
                nowEpochMs = nowEpochMs,
                hybridLogicalClockService = hybridLogicalClockService
            ).toEntity()
        )
    }
}
