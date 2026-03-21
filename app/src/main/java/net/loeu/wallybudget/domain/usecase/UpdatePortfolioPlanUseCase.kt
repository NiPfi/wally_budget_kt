@file:Suppress("LongMethod", "ReturnCount", "TooManyFunctions")

package net.loeu.wallybudget.domain.usecase

import kotlinx.coroutines.flow.first
import net.loeu.wallybudget.data.planning.DefaultPlanningRepository
import net.loeu.wallybudget.data.planning.PortfolioPlanningMutationContext
import net.loeu.wallybudget.data.planning.PortfolioPlanningMutationApplier
import net.loeu.wallybudget.data.local.dao.BudgetBucketDao
import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationPolicyDao
import net.loeu.wallybudget.data.local.db.TransactionRunner
import net.loeu.wallybudget.data.local.entity.toDomainModel as bucketToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel as bucketPolicyToDomainModel
import net.loeu.wallybudget.data.local.entity.toDomainModel as policyToDomainModel
import net.loeu.wallybudget.data.local.entity.toEntity
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.data.time.CurrentDateProvider
import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.BucketAllocationPolicy
import net.loeu.wallybudget.domain.model.BucketBalanceBehavior
import net.loeu.wallybudget.domain.model.BucketTrackingMode
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_NAME
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.planning.PlanningConfig
import net.loeu.wallybudget.domain.planning.PlanningContext
import net.loeu.wallybudget.domain.planning.PlanningEngine
import net.loeu.wallybudget.domain.planning.toPlanningBucket
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import net.loeu.wallybudget.domain.usecase.internal.newBucketAllocationPolicy
import net.loeu.wallybudget.domain.usecase.internal.resolveSelectedOpenBucketUuid
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class UpdatePortfolioPlanResult(
    val summaryMessage: String
)

private data class UpdatePortfolioPlanContext(
    val settings: UserSettings,
    val today: LocalDate,
    val currentCycleStart: LocalDate,
    val currentCycleEndExclusive: LocalDate,
    val buckets: List<BudgetBucket>,
    val futureBucketPolicies: List<BucketAllocationPolicy>
)

class UpdatePortfolioPlanUseCase(
    private val transactionRunner: TransactionRunner,
    private val userSettingsStore: UserSettingsStore,
    private val budgetPolicyDao: BudgetPolicyDao,
    private val budgetBucketDao: BudgetBucketDao,
    private val bucketAllocationPolicyDao: BucketAllocationPolicyDao,
    private val bucketAllocationAdjustmentDao: BucketAllocationAdjustmentDao,
    private val currentDateProvider: CurrentDateProvider,
    private val cycleScheduleResolver: CycleScheduleResolver,
    private val hybridLogicalClockService: HybridLogicalClockService
) {
    private val syncObservedDateUseCase = SyncObservedDateUseCase(userSettingsStore)
    private val planningEngine = PlanningEngine()
    private val planningRepository = DefaultPlanningRepository(userSettingsStore)
    private val planningMutationApplier = PortfolioPlanningMutationApplier(
        budgetBucketDao = budgetBucketDao,
        bucketAllocationPolicyDao = bucketAllocationPolicyDao,
        bucketAllocationAdjustmentDao = bucketAllocationAdjustmentDao,
        hybridLogicalClockService = hybridLogicalClockService
    )

    suspend operator fun invoke(request: UpdatePortfolioPlanRequest): UpdatePortfolioPlanResult {
        val context = buildContext()
        val changeSet = planningEngine.buildChangeSet(
            context = context.toPlanningContext(),
            request = request
        )
        require(changeSet.state.isValid) {
            changeSet.state.validationErrors.first()
        }
        if (!changeSet.hasChanges) {
            return UpdatePortfolioPlanResult("No planning changes.")
        }

        val mutation = transactionRunner.inTransaction {
            planningMutationApplier.apply(
                context = context.toMutationContext(),
                bucketDrafts = changeSet.state.normalizedDrafts,
                portfolioMonthlyBudgetCents = request.portfolioMonthlyBudgetCents,
                leftoverReceiverBucketUuid = changeSet.state.resolvedLeftoverReceiverBucketUuid
            )
        }

        userSettingsStore.updateMonthlyBudget(request.portfolioMonthlyBudgetCents)
        userSettingsStore.updatePortfolioMonthlyBudget(request.portfolioMonthlyBudgetCents)
        planningRepository.persistPlanningSettings(
            leftoverReceiverBucketUuid = changeSet.state.resolvedLeftoverReceiverBucketUuid,
            selectedBucketUuid = mutation
        )
        invalidateOrExpirePendingPaydayUndo()

        val parts = buildList {
            if (changeSet.budgetChanged) add("Portfolio budget updated.")
            if (changeSet.bucketChanged || changeSet.leftoverReceiverChanged) add("Bucket plan updated.")
        }
        return UpdatePortfolioPlanResult(parts.joinToString(" "))
    }

    private suspend fun buildContext(): UpdatePortfolioPlanContext {
        val settings = userSettingsStore.ensureIdentity()
        val today = syncObservedDateUseCase(settings, currentDateProvider.currentDate())
        val activePolicies = budgetPolicyDao.getAllForSnapshot()
            .filter { it.deletedAtEpochMs == null }
            .map { it.policyToDomainModel() }
            .sortedBy { it.cycleStartDate }
        val currentPolicy = cycleScheduleResolver.resolvePolicyForDate(today, settings, activePolicies)
        val futureBucketPolicies = bucketAllocationPolicyDao.getAllForSnapshot()
            .map { it.bucketPolicyToDomainModel() }
            .filter { it.deletedAtEpochMs == null && !it.cycleStart().isBefore(currentPolicy.cycleEndExclusive) }
        return UpdatePortfolioPlanContext(
            settings = settings,
            today = today,
            currentCycleStart = currentPolicy.cycleStart,
            currentCycleEndExclusive = currentPolicy.cycleEndExclusive,
            buckets = budgetBucketDao.getAllForSnapshot().map { it.bucketToDomainModel() },
            futureBucketPolicies = futureBucketPolicies
        )
    }

    private suspend fun invalidateOrExpirePendingPaydayUndo() {
        val settings = userSettingsStore.ensureIdentity()
        val today = syncObservedDateUseCase(settings, currentDateProvider.currentDate())
        val pendingUndo = userSettingsStore.pendingPaydayUndo.first() ?: return
        if (!today.isBefore(pendingUndo.expiresAtExclusiveDate())) {
            userSettingsStore.clearPendingPaydayUndo()
            return
        }
        userSettingsStore.clearPendingPaydayUndo()
    }

    private fun UpdatePortfolioPlanContext.toPlanningContext(): PlanningContext {
        return PlanningContext(
            config = PlanningConfig(
                portfolioMonthlyBudgetCents = settings.resolvedPortfolioMonthlyBudgetCents,
                leftoverReceiverBucketUuid = settings.leftoverReceiverBucketUuid
            ),
            buckets = buckets.map { it.toPlanningBucket() },
            selectedBucketUuid = settings.selectedBucketUuid
        )
    }

    private fun UpdatePortfolioPlanContext.toMutationContext(): PortfolioPlanningMutationContext {
        return PortfolioPlanningMutationContext(
            settings = settings,
            today = today,
            currentCycleStart = currentCycleStart,
            currentCycleEndExclusive = currentCycleEndExclusive,
            buckets = buckets,
            futureBucketPolicies = futureBucketPolicies
        )
    }
}
