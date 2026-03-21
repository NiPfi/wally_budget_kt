package net.loeu.wallybudget.data.planning

import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.BucketAllocationAdjustment
import net.loeu.wallybudget.domain.model.BucketAllocationPolicy
import net.loeu.wallybudget.domain.model.UserSettings
import java.time.LocalDate

data class PortfolioPlanningMutationContext(
    val settings: UserSettings,
    val today: LocalDate,
    val currentCycleStart: LocalDate,
    val currentCycleEndExclusive: LocalDate,
    val buckets: List<BudgetBucket>,
    val futureBucketPolicies: List<BucketAllocationPolicy>
)

data class BudgetSettingsPlanningMutationContext(
    val settings: UserSettings,
    val today: LocalDate,
    val currentCycleStart: LocalDate,
    val currentCycleEndExclusive: LocalDate,
    val buckets: List<BudgetBucket>,
    val bucketPolicies: List<BucketAllocationPolicy>,
    val bucketAdjustments: List<BucketAllocationAdjustment>
)
