package net.loeu.wallybudget.domain.usecase

import android.net.Uri
import net.loeu.wallybudget.data.local.dao.BudgetAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationPolicyDao
import net.loeu.wallybudget.data.local.dao.BudgetBucketDao
import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.data.snapshot.DocumentUriGateway
import net.loeu.wallybudget.data.snapshot.GzipSnapshotCodec
import net.loeu.wallybudget.data.snapshot.SnapshotCompatibilityService
import net.loeu.wallybudget.data.snapshot.SnapshotHasher
import net.loeu.wallybudget.data.snapshot.SnapshotJsonCodec
import net.loeu.wallybudget.data.snapshot.model.SnapshotBudgetPolicyRecordV1
import net.loeu.wallybudget.data.snapshot.model.SnapshotBudgetAdjustmentRecordV2
import net.loeu.wallybudget.data.snapshot.model.SnapshotBudgetBucketRecordV3
import net.loeu.wallybudget.data.snapshot.model.SnapshotBucketAllocationAdjustmentRecordV3
import net.loeu.wallybudget.data.snapshot.model.SnapshotBucketAllocationPolicyRecordV3
import net.loeu.wallybudget.data.snapshot.model.SnapshotEnvelopeV1
import net.loeu.wallybudget.data.snapshot.model.SnapshotExpenseRecordV1
import net.loeu.wallybudget.data.snapshot.model.SnapshotSettingsRecordV1
import net.loeu.wallybudget.domain.model.SnapshotError
import net.loeu.wallybudget.domain.service.HybridLogicalClockService

@Suppress("LongMethod", "ThrowsCount", "TooGenericExceptionCaught")
class ExportSnapshotUseCase(
    private val documentUriGateway: DocumentUriGateway,
    private val gzipSnapshotCodec: GzipSnapshotCodec,
    private val snapshotJsonCodec: SnapshotJsonCodec,
    private val snapshotHasher: SnapshotHasher,
    private val expenseDao: ExpenseDao,
    private val budgetPolicyDao: BudgetPolicyDao,
    private val budgetAdjustmentDao: BudgetAdjustmentDao,
    private val budgetBucketDao: BudgetBucketDao,
    private val bucketAllocationPolicyDao: BucketAllocationPolicyDao,
    private val bucketAllocationAdjustmentDao: BucketAllocationAdjustmentDao,
    private val userSettingsStore: UserSettingsStore,
    private val hybridLogicalClockService: HybridLogicalClockService,
    private val appVersionName: String
) {
    suspend operator fun invoke(uri: Uri): Int {
        val settings = userSettingsStore.ensureIdentity()
        val nowEpochMs = System.currentTimeMillis()
        val envelope = SnapshotEnvelopeV1(
            format = SnapshotCompatibilityService.SNAPSHOT_FORMAT,
            schemaVersion = SnapshotCompatibilityService.CURRENT_SCHEMA_VERSION,
            snapshotId = "",
            baseSnapshotId = null,
            exportedAtEpochMs = nowEpochMs,
            writerInstallId = settings.installDeviceId,
            snapshotModClock = hybridLogicalClockService.format(
                epochMs = nowEpochMs,
                counter = 0,
                installId = settings.installDeviceId
            ),
            appVersionName = appVersionName,
            settings = SnapshotSettingsRecordV1(
                recordUuid = settings.settingsRecordUuid,
                defaultMonthlyBudgetCents = settings.monthlyBudgetCents,
                portfolioMonthlyBudgetCents = settings.portfolioMonthlyBudgetCents,
                legacyDefaultBucketBudgetCents = settings.monthlyBudgetCents,
                paydayDate = settings.paydayDate,
                primaryBucketUuid = settings.primaryBucketUuid,
                selectedBucketUuid = settings.selectedBucketUuid,
                lastResetTimestamp = settings.lastResetTimestamp,
                pendingCycleStartDate = settings.pendingCycleStartDate,
                pendingCycleEndDateExclusive = settings.pendingCycleEndDateExclusive,
                pendingCycleDetectedAtTimestamp = settings.pendingCycleDetectedAtTimestamp,
                updatedAtEpochMs = settings.settingsUpdatedAtEpochMs,
                modClock = settings.settingsModClock,
                lastModifiedByInstallId = settings.settingsLastModifiedByInstallId
            ),
            budgetPolicies = budgetPolicyDao.getAllForSnapshot()
                .sortedWith(compareBy({ it.cycleStartDate }, { it.updatedAtEpochMs }, { it.policyUuid }))
                .map { policy ->
                    SnapshotBudgetPolicyRecordV1(
                        policyUuid = policy.policyUuid,
                        cycleStartDate = policy.cycleStartDate,
                        cycleEndDateExclusive = policy.cycleEndDateExclusive,
                        budgetAmountCents = policy.budgetAmountCents,
                        paydayDayOfMonth = policy.paydayDayOfMonth,
                        originInstallId = policy.originInstallId,
                        lastModifiedByInstallId = policy.lastModifiedByInstallId,
                        createdAtEpochMs = policy.createdAtEpochMs,
                        updatedAtEpochMs = policy.updatedAtEpochMs,
                        deletedAtEpochMs = policy.deletedAtEpochMs,
                        modClock = policy.modClock
                    )
                },
            budgetAdjustments = budgetAdjustmentDao.getAllForSnapshot()
                .sortedWith(
                    compareBy(
                        { it.cycleStartDate },
                        { it.effectiveDate },
                        { it.updatedAtEpochMs },
                        { it.adjustmentUuid }
                    )
                )
                .map { adjustment ->
                    SnapshotBudgetAdjustmentRecordV2(
                        adjustmentUuid = adjustment.adjustmentUuid,
                        cycleStartDate = adjustment.cycleStartDate,
                        effectiveDate = adjustment.effectiveDate,
                        previousMonthlyBudgetCents = adjustment.previousMonthlyBudgetCents,
                        newMonthlyBudgetCents = adjustment.newMonthlyBudgetCents,
                        originInstallId = adjustment.originInstallId,
                        lastModifiedByInstallId = adjustment.lastModifiedByInstallId,
                        createdAtEpochMs = adjustment.createdAtEpochMs,
                        updatedAtEpochMs = adjustment.updatedAtEpochMs,
                        deletedAtEpochMs = adjustment.deletedAtEpochMs,
                        modClock = adjustment.modClock
                    )
                },
            expenses = expenseDao.getAllForSnapshot()
                .sortedWith(compareBy({ it.expenseDate }, { it.timestamp }, { it.recordUuid }))
                .map { expense ->
                    SnapshotExpenseRecordV1(
                        recordUuid = expense.recordUuid,
                        amountCents = expense.amountCents,
                        description = expense.description,
                        timestampEpochMs = expense.timestamp,
                        expenseDate = expense.expenseDate,
                        bucketUuid = expense.bucketUuid,
                        icon = expense.icon?.name,
                        originInstallId = expense.originInstallId,
                        lastModifiedByInstallId = expense.lastModifiedByInstallId,
                        createdAtEpochMs = expense.createdAtEpochMs,
                        updatedAtEpochMs = expense.updatedAtEpochMs,
                        deletedAtEpochMs = expense.deletedAtEpochMs,
                        modClock = expense.modClock
                    )
                },
            budgetBuckets = budgetBucketDao.getAllForSnapshot()
                .sortedWith(compareBy({ it.sortOrder }, { it.createdAtEpochMs }, { it.bucketUuid }))
                .map { bucket ->
                    SnapshotBudgetBucketRecordV3(
                        bucketUuid = bucket.bucketUuid,
                        name = bucket.name,
                        trackingMode = bucket.trackingMode.name,
                        balanceBehavior = bucket.balanceBehavior.name,
                        defaultAllocatedAmountCents = bucket.defaultAllocatedAmountCents,
                        sortOrder = bucket.sortOrder,
                        isPrimary = bucket.isPrimary,
                        originInstallId = bucket.originInstallId,
                        lastModifiedByInstallId = bucket.lastModifiedByInstallId,
                        createdAtEpochMs = bucket.createdAtEpochMs,
                        updatedAtEpochMs = bucket.updatedAtEpochMs,
                        closedAtEpochMs = bucket.closedAtEpochMs,
                        deletedAtEpochMs = bucket.deletedAtEpochMs,
                        modClock = bucket.modClock
                    )
                },
            bucketAllocationPolicies = bucketAllocationPolicyDao.getAllForSnapshot()
                .sortedWith(compareBy({ it.bucketUuid }, { it.cycleStartDate }, { it.updatedAtEpochMs }))
                .map { policy ->
                    SnapshotBucketAllocationPolicyRecordV3(
                        allocationUuid = policy.allocationUuid,
                        bucketUuid = policy.bucketUuid,
                        cycleStartDate = policy.cycleStartDate,
                        cycleEndDateExclusive = policy.cycleEndDateExclusive,
                        allocatedAmountCents = policy.allocatedAmountCents,
                        originInstallId = policy.originInstallId,
                        lastModifiedByInstallId = policy.lastModifiedByInstallId,
                        createdAtEpochMs = policy.createdAtEpochMs,
                        updatedAtEpochMs = policy.updatedAtEpochMs,
                        deletedAtEpochMs = policy.deletedAtEpochMs,
                        modClock = policy.modClock
                    )
                },
            bucketAllocationAdjustments = bucketAllocationAdjustmentDao.getAllForSnapshot()
                .sortedWith(
                    compareBy(
                        { it.bucketUuid },
                        { it.cycleStartDate },
                        { it.effectiveDate },
                        { it.updatedAtEpochMs }
                    )
                )
                .map { adjustment ->
                    SnapshotBucketAllocationAdjustmentRecordV3(
                        adjustmentUuid = adjustment.adjustmentUuid,
                        bucketUuid = adjustment.bucketUuid,
                        cycleStartDate = adjustment.cycleStartDate,
                        effectiveDate = adjustment.effectiveDate,
                        previousAllocatedAmountCents = adjustment.previousAllocatedAmountCents,
                        newAllocatedAmountCents = adjustment.newAllocatedAmountCents,
                        originInstallId = adjustment.originInstallId,
                        lastModifiedByInstallId = adjustment.lastModifiedByInstallId,
                        createdAtEpochMs = adjustment.createdAtEpochMs,
                        updatedAtEpochMs = adjustment.updatedAtEpochMs,
                        deletedAtEpochMs = adjustment.deletedAtEpochMs,
                        modClock = adjustment.modClock
                    )
                }
        )
        val canonicalWithoutId = snapshotJsonCodec.encode(envelope)
        val snapshotId = snapshotHasher.sha256(canonicalWithoutId)
        val finalPayload = snapshotJsonCodec.encode(envelope.copy(snapshotId = snapshotId))
        val gzipBytes = gzipSnapshotCodec.encodeToGzip(finalPayload)
        try {
            documentUriGateway.openOutputStream(uri)?.use { output ->
                output.write(gzipBytes)
                output.flush()
            } ?: throw SnapshotOperationException(
                SnapshotError.IoFailure("Unable to write the snapshot file.")
            )
        } catch (exception: SnapshotOperationException) {
            throw exception
        } catch (exception: Exception) {
            throw SnapshotOperationException(
                SnapshotError.IoFailure("Unable to write the snapshot file."),
                exception
            )
        }
        return gzipBytes.size
    }
}
