package net.loeu.wallybudget.domain.usecase

import android.net.Uri
import net.loeu.wallybudget.data.local.dao.BudgetAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationPolicyDao
import net.loeu.wallybudget.data.local.dao.BucketCycleBaselineDao
import net.loeu.wallybudget.data.local.dao.BucketTransferDao
import net.loeu.wallybudget.data.local.dao.BudgetBucketDao
import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.dao.FundDao
import net.loeu.wallybudget.data.local.dao.FundTransactionDao
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
import net.loeu.wallybudget.data.snapshot.model.SnapshotBucketCycleBaselineRecordV1
import net.loeu.wallybudget.data.snapshot.model.SnapshotBucketTransferRecordV1
import net.loeu.wallybudget.data.snapshot.model.SnapshotEnvelopeV1
import net.loeu.wallybudget.data.snapshot.model.SnapshotExpenseRecordV1
import net.loeu.wallybudget.data.snapshot.model.SnapshotFundRecordV6
import net.loeu.wallybudget.data.snapshot.model.SnapshotFundTransactionRecordV5
import net.loeu.wallybudget.data.snapshot.model.SnapshotSettingsRecordV1
import net.loeu.wallybudget.data.time.WallyTime
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
    private val bucketCycleBaselineDao: BucketCycleBaselineDao,
    private val bucketTransferDao: BucketTransferDao,
    private val fundDao: FundDao,
    private val fundTransactionDao: FundTransactionDao,
    private val userSettingsStore: UserSettingsStore,
    private val hybridLogicalClockService: HybridLogicalClockService,
    private val appVersionName: String
) {
    suspend operator fun invoke(uri: Uri): Int {
        val settings = userSettingsStore.ensureIdentity()
        val nowEpochMs = WallyTime.currentEpochTimeMs()
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
                        originInstallId = bucket.originInstallId,
                        lastModifiedByInstallId = bucket.lastModifiedByInstallId,
                        createdAtEpochMs = bucket.createdAtEpochMs,
                        updatedAtEpochMs = bucket.updatedAtEpochMs,
                        settledCloseCycleEndDateExclusive = bucket.settledCloseCycleEndDateExclusive,
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
                },
            bucketTransfers = bucketTransferDao.getAllForSnapshot()
                .sortedWith(
                    compareBy(
                        { it.cycleStartDate },
                        { it.effectiveDate },
                        { it.updatedAtEpochMs },
                        { it.transferUuid }
                    )
                )
                .map { transfer ->
                    SnapshotBucketTransferRecordV1(
                        transferUuid = transfer.transferUuid,
                        fromBucketUuid = transfer.fromBucketUuid,
                        toBucketUuid = transfer.toBucketUuid,
                        amountCents = transfer.amountCents,
                        reason = transfer.reason.name,
                        cycleStartDate = transfer.cycleStartDate,
                        cycleEndDateExclusive = transfer.cycleEndDateExclusive,
                        effectiveDate = transfer.effectiveDate,
                        originInstallId = transfer.originInstallId,
                        lastModifiedByInstallId = transfer.lastModifiedByInstallId,
                        createdAtEpochMs = transfer.createdAtEpochMs,
                        updatedAtEpochMs = transfer.updatedAtEpochMs,
                        deletedAtEpochMs = transfer.deletedAtEpochMs,
                        modClock = transfer.modClock
                    )
                },
            bucketCycleBaselines = bucketCycleBaselineDao.getAllForSnapshot()
                .sortedWith(
                    compareBy(
                        { it.bucketUuid },
                        { it.cycleStartDate },
                        { it.updatedAtEpochMs },
                        { it.baselineUuid }
                    )
                )
                .map { baseline ->
                    SnapshotBucketCycleBaselineRecordV1(
                        baselineUuid = baseline.baselineUuid,
                        bucketUuid = baseline.bucketUuid,
                        cycleStartDate = baseline.cycleStartDate,
                        cycleEndDateExclusive = baseline.cycleEndDateExclusive,
                        baselineAmountCents = baseline.baselineAmountCents,
                        originInstallId = baseline.originInstallId,
                        lastModifiedByInstallId = baseline.lastModifiedByInstallId,
                        createdAtEpochMs = baseline.createdAtEpochMs,
                        updatedAtEpochMs = baseline.updatedAtEpochMs,
                        deletedAtEpochMs = baseline.deletedAtEpochMs,
                        modClock = baseline.modClock
                    )
                },
            funds = fundDao.getAllForSnapshot()
                .sortedWith(compareBy({ it.sortOrder }, { it.createdAtEpochMs }, { it.uuid }))
                .map { fund ->
                    SnapshotFundRecordV6(
                        uuid = fund.uuid,
                        name = fund.name,
                        fundType = fund.fundType.name,
                        balanceCents = fund.balanceCents,
                        allocationPerCycleCents = fund.allocationPerCycleCents,
                        targetAmountCents = fund.targetAmountCents,
                        sortOrder = fund.sortOrder,
                        originInstallId = fund.originInstallId,
                        lastModifiedByInstallId = fund.lastModifiedByInstallId,
                        createdAtEpochMs = fund.createdAtEpochMs,
                        updatedAtEpochMs = fund.updatedAtEpochMs,
                        closedAtEpochMs = fund.closedAtEpochMs,
                        deletedAtEpochMs = fund.deletedAtEpochMs,
                        modClock = fund.modClock
                    )
                },
            fundTransactions = fundTransactionDao.getAllForSnapshot().map { tx ->
                SnapshotFundTransactionRecordV5(
                    uuid = tx.uuid,
                    fundUuid = tx.fundUuid,
                    amountCents = tx.amountCents,
                    type = tx.type.name,
                    description = tx.description,
                    dateEpochMs = tx.dateEpochMs
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
