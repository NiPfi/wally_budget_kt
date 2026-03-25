package net.loeu.wallybudget.domain.service

import net.loeu.wallybudget.domain.model.BucketAllocationPolicy
import net.loeu.wallybudget.domain.model.BucketCycleBaseline
import net.loeu.wallybudget.domain.model.BucketTransfer
import java.time.LocalDate

data class ResolvedCurrentCycleBucketAllocation(
    val baselineAmountCents: Long,
    val transferDeltaCents: Long,
    val effectiveAllocationCents: Long
)

class CurrentCycleBucketAllocationResolver {
    fun resolve(
        bucketUuid: String,
        cycleStart: LocalDate,
        fallbackAllocationCents: Long,
        baselines: List<BucketCycleBaseline>,
        transfers: List<BucketTransfer>,
        legacyPolicies: List<BucketAllocationPolicy> = emptyList()
    ): ResolvedCurrentCycleBucketAllocation {
        val baselineAmountCents = baselines
            .lastOrNull {
                it.deletedAtEpochMs == null &&
                    it.bucketUuid == bucketUuid &&
                    it.cycleStart() == cycleStart
            }
            ?.baselineAmountCents
            ?: legacyPolicies.lastOrNull {
                it.deletedAtEpochMs == null &&
                    it.bucketUuid == bucketUuid &&
                    it.cycleStart() == cycleStart
            }?.allocatedAmountCents
            ?: fallbackAllocationCents
        val transferDeltaCents = transfers
            .filter { it.deletedAtEpochMs == null && it.cycleStart() == cycleStart }
            .sumOf { transfer ->
                when (bucketUuid) {
                    transfer.toBucketUuid -> transfer.amountCents
                    transfer.fromBucketUuid -> -transfer.amountCents
                    else -> 0L
                }
            }
        return ResolvedCurrentCycleBucketAllocation(
            baselineAmountCents = baselineAmountCents,
            transferDeltaCents = transferDeltaCents,
            effectiveAllocationCents = baselineAmountCents + transferDeltaCents
        )
    }
}
