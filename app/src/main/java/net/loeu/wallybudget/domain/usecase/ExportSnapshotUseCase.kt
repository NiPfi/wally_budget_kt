package net.loeu.wallybudget.domain.usecase

import android.net.Uri
import net.loeu.wallybudget.data.local.dao.BudgetAdjustmentDao
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
                paydayDate = settings.paydayDate,
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
                .sortedWith(compareBy({ it.cycleStartDate }, { it.effectiveDate }, { it.updatedAtEpochMs }, { it.adjustmentUuid }))
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
                        icon = expense.icon?.name,
                        originInstallId = expense.originInstallId,
                        lastModifiedByInstallId = expense.lastModifiedByInstallId,
                        createdAtEpochMs = expense.createdAtEpochMs,
                        updatedAtEpochMs = expense.updatedAtEpochMs,
                        deletedAtEpochMs = expense.deletedAtEpochMs,
                        modClock = expense.modClock
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
