package net.loeu.wallybudget.domain.usecase

import android.net.Uri
import net.loeu.wallybudget.data.local.entity.BudgetAdjustmentEntity
import net.loeu.wallybudget.data.local.entity.BudgetPolicyEntity
import net.loeu.wallybudget.data.local.entity.BucketAllocationAdjustmentEntity
import net.loeu.wallybudget.data.local.entity.BucketAllocationPolicyEntity
import net.loeu.wallybudget.data.local.entity.BudgetBucketEntity
import net.loeu.wallybudget.data.local.entity.BucketTransferEntity
import net.loeu.wallybudget.data.local.entity.ExpenseEntity
import net.loeu.wallybudget.data.local.entity.FundEntity
import net.loeu.wallybudget.data.local.entity.FundTransactionEntity
import net.loeu.wallybudget.data.local.db.LegacyBucketBalanceBehavior
import net.loeu.wallybudget.data.local.db.LegacyBucketTrackingMode
import net.loeu.wallybudget.data.snapshot.DecodedSnapshotPayload
import net.loeu.wallybudget.data.snapshot.DocumentUriGateway
import net.loeu.wallybudget.data.snapshot.GzipSnapshotCodec
import net.loeu.wallybudget.data.snapshot.SnapshotCompatibilityService
import net.loeu.wallybudget.data.snapshot.SnapshotJsonCodec
import net.loeu.wallybudget.data.snapshot.model.SnapshotEnvelopeV1
import net.loeu.wallybudget.domain.model.DEFAULT_FUND_NAME
import net.loeu.wallybudget.domain.model.DEFAULT_FUND_UUID
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_NAME
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import net.loeu.wallybudget.domain.model.BucketTransferReason
import net.loeu.wallybudget.domain.model.ExpenseCategory
import net.loeu.wallybudget.domain.model.FundTransactionType
import net.loeu.wallybudget.domain.model.SnapshotError
import net.loeu.wallybudget.domain.model.SnapshotImportPreview
import net.loeu.wallybudget.domain.model.UserSettings
import java.io.ByteArrayOutputStream
import java.io.InputStream

@Suppress("ThrowsCount", "TooGenericExceptionCaught", "TooManyFunctions")
class PrepareSnapshotImportUseCase(
    private val documentUriGateway: DocumentUriGateway,
    private val gzipSnapshotCodec: GzipSnapshotCodec,
    private val snapshotJsonCodec: SnapshotJsonCodec,
    private val snapshotCompatibilityService: SnapshotCompatibilityService
) {
    suspend operator fun invoke(uri: Uri): PreparedSnapshotImport {
        val rawBytes = try {
            documentUriGateway.openInputStream(uri)?.use { input ->
                readSnapshotBytes(input)
            } ?: throw SnapshotOperationException(
                    SnapshotError.IoFailure("Unable to open the selected snapshot file.")
                )
        } catch (exception: SnapshotOperationException) {
            throw exception
        } catch (exception: Exception) {
            throw SnapshotOperationException(
                SnapshotError.IoFailure("Unable to read the selected snapshot file."),
                exception
            )
        }

        return prepareFromBytes(rawBytes)
    }

    internal fun prepareFromBytes(rawBytes: ByteArray): PreparedSnapshotImport {
        val payload = try {
            gzipSnapshotCodec.decodeFromBytes(rawBytes)
        } catch (exception: Exception) {
            throw SnapshotOperationException(SnapshotError.InvalidCompression, exception)
        }
        val envelope = try {
            snapshotJsonCodec.decode(payload.text)
        } catch (exception: Exception) {
            throw SnapshotOperationException(SnapshotError.MalformedSnapshot, exception)
        }

        snapshotCompatibilityService.validateSchemaVersion(envelope.schemaVersion)?.let {
            throw SnapshotOperationException(it)
        }
        if (envelope.format != SnapshotCompatibilityService.SNAPSHOT_FORMAT) {
            throw SnapshotOperationException(SnapshotError.MalformedSnapshot)
        }

        return try {
            PreparedSnapshotImport(
                preview = envelope.toPreview(payload),
                settings = envelope.toUserSettings(),
                budgetPolicies = envelope.toBudgetPolicyEntities(),
                budgetAdjustments = envelope.toBudgetAdjustmentEntities(),
                budgetBuckets = envelope.toBudgetBucketEntities(),
                bucketAllocationPolicies = envelope.toBucketAllocationPolicyEntities(),
                bucketAllocationAdjustments = envelope.toBucketAllocationAdjustmentEntities(),
                bucketTransfers = envelope.toBucketTransferEntities(),
                funds = envelope.toFundEntities(),
                fundTransactions = envelope.toFundTransactionEntities(),
                expenses = envelope.toExpenseEntities()
            )
        } catch (exception: SnapshotOperationException) {
            throw exception
        } catch (exception: Exception) {
            throw SnapshotOperationException(SnapshotError.MalformedSnapshot, exception)
        }
    }

    internal fun readSnapshotBytes(input: InputStream): ByteArray = input.readBoundedBytes(MAX_SNAPSHOT_BYTES)

    private fun SnapshotEnvelopeV1.toPreview(payload: DecodedSnapshotPayload): SnapshotImportPreview {
        return SnapshotImportPreview(
            exportedAtEpochMs = exportedAtEpochMs,
            writerInstallId = writerInstallId,
            expenseCount = expenses.size,
            tombstoneCount = expenses.count { it.deletedAtEpochMs != null },
            budgetPolicyCount = budgetPolicies.size,
            defaultMonthlyBudgetCents = settings.legacyDefaultBucketBudgetCents
                ?: settings.defaultMonthlyBudgetCents,
            paydayDate = settings.paydayDate,
            compressed = payload.compressed
        )
    }

    private fun SnapshotEnvelopeV1.toUserSettings(): UserSettings {
        val legacyDefaultBucketBudgetCents = settings.legacyDefaultBucketBudgetCents
            ?: settings.defaultMonthlyBudgetCents
        return UserSettings(
            monthlyBudgetCents = legacyDefaultBucketBudgetCents,
            portfolioMonthlyBudgetCents = settings.portfolioMonthlyBudgetCents,
            paydayDate = settings.paydayDate,
            selectedBucketUuid = settings.selectedBucketUuid
                ?: DEFAULT_SPENDING_BUCKET_UUID,
            lastResetTimestamp = settings.lastResetTimestamp,
            pendingCycleStartDate = settings.pendingCycleStartDate,
            pendingCycleEndDateExclusive = settings.pendingCycleEndDateExclusive,
            pendingCycleDetectedAtTimestamp = settings.pendingCycleDetectedAtTimestamp,
            installDeviceId = writerInstallId,
            settingsRecordUuid = settings.recordUuid,
            settingsUpdatedAtEpochMs = settings.updatedAtEpochMs,
            settingsModClock = settings.modClock,
            settingsLastModifiedByInstallId = settings.lastModifiedByInstallId
        )
    }

    private fun SnapshotEnvelopeV1.toBudgetPolicyEntities(): List<BudgetPolicyEntity> {
        return budgetPolicies.map { record ->
            BudgetPolicyEntity(
                policyUuid = record.policyUuid,
                cycleStartDate = record.cycleStartDate,
                cycleEndDateExclusive = record.cycleEndDateExclusive,
                budgetAmountCents = record.budgetAmountCents,
                paydayDayOfMonth = record.paydayDayOfMonth,
                originInstallId = record.originInstallId,
                lastModifiedByInstallId = record.lastModifiedByInstallId,
                createdAtEpochMs = record.createdAtEpochMs,
                updatedAtEpochMs = record.updatedAtEpochMs,
                deletedAtEpochMs = record.deletedAtEpochMs,
                modClock = record.modClock
            )
        }
    }

    private fun SnapshotEnvelopeV1.toBudgetAdjustmentEntities(): List<BudgetAdjustmentEntity> {
        return budgetAdjustments.orEmpty().map { record ->
            BudgetAdjustmentEntity(
                adjustmentUuid = record.adjustmentUuid,
                cycleStartDate = record.cycleStartDate,
                effectiveDate = record.effectiveDate,
                previousMonthlyBudgetCents = record.previousMonthlyBudgetCents,
                newMonthlyBudgetCents = record.newMonthlyBudgetCents,
                originInstallId = record.originInstallId,
                lastModifiedByInstallId = record.lastModifiedByInstallId,
                createdAtEpochMs = record.createdAtEpochMs,
                updatedAtEpochMs = record.updatedAtEpochMs,
                deletedAtEpochMs = record.deletedAtEpochMs,
                modClock = record.modClock
            )
        }
    }

    private fun SnapshotEnvelopeV1.toExpenseEntities(): List<ExpenseEntity> {
        return expenses.map { record ->
            ExpenseEntity(
                recordUuid = record.recordUuid,
                amountCents = record.amountCents,
                description = record.description,
                timestamp = record.timestampEpochMs,
                expenseDate = record.expenseDate,
                bucketUuid = record.bucketUuid ?: DEFAULT_SPENDING_BUCKET_UUID,
                icon = record.icon?.let { ExpenseCategory.entries.find { entry -> entry.name == it } },
                originInstallId = record.originInstallId,
                lastModifiedByInstallId = record.lastModifiedByInstallId,
                createdAtEpochMs = record.createdAtEpochMs,
                updatedAtEpochMs = record.updatedAtEpochMs,
                deletedAtEpochMs = record.deletedAtEpochMs,
                modClock = record.modClock
            )
        }
    }

    private fun SnapshotEnvelopeV1.toBudgetBucketEntities(): List<BudgetBucketEntity> {
        val records = budgetBuckets
        if (!records.isNullOrEmpty()) {
            return records.map { record ->
                BudgetBucketEntity(
                    bucketUuid = record.bucketUuid,
                    name = record.name,
                trackingMode = LegacyBucketTrackingMode.valueOf(record.trackingMode),
                balanceBehavior = LegacyBucketBalanceBehavior.valueOf(record.balanceBehavior),
                defaultAllocatedAmountCents = record.defaultAllocatedAmountCents,
                sortOrder = record.sortOrder,
                originInstallId = record.originInstallId,
                lastModifiedByInstallId = record.lastModifiedByInstallId,
                    createdAtEpochMs = record.createdAtEpochMs,
                    updatedAtEpochMs = record.updatedAtEpochMs,
                    closedAtEpochMs = record.closedAtEpochMs,
                    deletedAtEpochMs = record.deletedAtEpochMs,
                    modClock = record.modClock
                )
            }
        }

        val legacyBudget = settings.legacyDefaultBucketBudgetCents ?: settings.defaultMonthlyBudgetCents
        return listOf(
            BudgetBucketEntity(
                bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                name = DEFAULT_SPENDING_BUCKET_NAME,
                trackingMode = LegacyBucketTrackingMode.DAILY_TARGET,
                balanceBehavior = LegacyBucketBalanceBehavior.RETURN_TO_PORTFOLIO,
                defaultAllocatedAmountCents = legacyBudget,
                sortOrder = 0,
                originInstallId = writerInstallId,
                lastModifiedByInstallId = writerInstallId,
                createdAtEpochMs = exportedAtEpochMs,
                updatedAtEpochMs = exportedAtEpochMs,
                closedAtEpochMs = null,
                deletedAtEpochMs = null,
                modClock = snapshotModClock
            )
        )
    }

    private fun SnapshotEnvelopeV1.toBucketAllocationPolicyEntities(): List<BucketAllocationPolicyEntity> {
        val records = bucketAllocationPolicies
        if (!records.isNullOrEmpty()) {
            return records.map { record ->
                BucketAllocationPolicyEntity(
                    allocationUuid = record.allocationUuid,
                    bucketUuid = record.bucketUuid,
                    cycleStartDate = record.cycleStartDate,
                    cycleEndDateExclusive = record.cycleEndDateExclusive,
                    allocatedAmountCents = record.allocatedAmountCents,
                    originInstallId = record.originInstallId,
                    lastModifiedByInstallId = record.lastModifiedByInstallId,
                    createdAtEpochMs = record.createdAtEpochMs,
                    updatedAtEpochMs = record.updatedAtEpochMs,
                    deletedAtEpochMs = record.deletedAtEpochMs,
                    modClock = record.modClock
                )
            }
        }

        return budgetPolicies.map { record ->
            BucketAllocationPolicyEntity(
                allocationUuid = record.policyUuid,
                bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                cycleStartDate = record.cycleStartDate,
                cycleEndDateExclusive = record.cycleEndDateExclusive,
                allocatedAmountCents = record.budgetAmountCents,
                originInstallId = record.originInstallId,
                lastModifiedByInstallId = record.lastModifiedByInstallId,
                createdAtEpochMs = record.createdAtEpochMs,
                updatedAtEpochMs = record.updatedAtEpochMs,
                deletedAtEpochMs = record.deletedAtEpochMs,
                modClock = record.modClock
            )
        }
    }

    private fun SnapshotEnvelopeV1.toBucketAllocationAdjustmentEntities(): List<BucketAllocationAdjustmentEntity> {
        val records = bucketAllocationAdjustments
        if (!records.isNullOrEmpty()) {
            return records.map { record ->
                BucketAllocationAdjustmentEntity(
                    adjustmentUuid = record.adjustmentUuid,
                    bucketUuid = record.bucketUuid,
                    cycleStartDate = record.cycleStartDate,
                    effectiveDate = record.effectiveDate,
                    previousAllocatedAmountCents = record.previousAllocatedAmountCents,
                    newAllocatedAmountCents = record.newAllocatedAmountCents,
                    originInstallId = record.originInstallId,
                    lastModifiedByInstallId = record.lastModifiedByInstallId,
                    createdAtEpochMs = record.createdAtEpochMs,
                    updatedAtEpochMs = record.updatedAtEpochMs,
                    deletedAtEpochMs = record.deletedAtEpochMs,
                    modClock = record.modClock
                )
            }
        }

        return budgetAdjustments.orEmpty().map { record ->
            BucketAllocationAdjustmentEntity(
                adjustmentUuid = record.adjustmentUuid,
                bucketUuid = DEFAULT_SPENDING_BUCKET_UUID,
                cycleStartDate = record.cycleStartDate,
                effectiveDate = record.effectiveDate,
                previousAllocatedAmountCents = record.previousMonthlyBudgetCents,
                newAllocatedAmountCents = record.newMonthlyBudgetCents,
                originInstallId = record.originInstallId,
                lastModifiedByInstallId = record.lastModifiedByInstallId,
                createdAtEpochMs = record.createdAtEpochMs,
                updatedAtEpochMs = record.updatedAtEpochMs,
                deletedAtEpochMs = record.deletedAtEpochMs,
                modClock = record.modClock
            )
        }
    }

    private fun SnapshotEnvelopeV1.toBucketTransferEntities(): List<BucketTransferEntity> {
        return bucketTransfers.orEmpty().map { record ->
            val reason = BucketTransferReason.entries.find { it.name == record.reason }
                ?: throw SnapshotOperationException(SnapshotError.MalformedSnapshot)
            BucketTransferEntity(
                transferUuid = record.transferUuid,
                fromBucketUuid = record.fromBucketUuid,
                toBucketUuid = record.toBucketUuid,
                amountCents = record.amountCents,
                reason = reason,
                cycleStartDate = record.cycleStartDate,
                cycleEndDateExclusive = record.cycleEndDateExclusive,
                effectiveDate = record.effectiveDate,
                originInstallId = record.originInstallId,
                lastModifiedByInstallId = record.lastModifiedByInstallId,
                createdAtEpochMs = record.createdAtEpochMs,
                updatedAtEpochMs = record.updatedAtEpochMs,
                deletedAtEpochMs = record.deletedAtEpochMs,
                modClock = record.modClock
            )
        }
    }

    private fun SnapshotEnvelopeV1.toFundEntities(): List<FundEntity> {
        val records = funds
        if (!records.isNullOrEmpty()) {
            return records.map { record ->
                FundEntity(
                    uuid = record.uuid,
                    name = record.name,
                    balanceCents = record.balanceCents,
                    allocationPerCycleCents = record.allocationPerCycleCents,
                    targetAmountCents = record.targetAmountCents,
                    sortOrder = record.sortOrder,
                    originInstallId = record.originInstallId,
                    lastModifiedByInstallId = record.lastModifiedByInstallId,
                    createdAtEpochMs = record.createdAtEpochMs,
                    updatedAtEpochMs = record.updatedAtEpochMs,
                    closedAtEpochMs = record.closedAtEpochMs,
                    deletedAtEpochMs = record.deletedAtEpochMs,
                    modClock = record.modClock
                )
            }
        }

        return listOf(
            FundEntity(
                uuid = DEFAULT_FUND_UUID,
                name = DEFAULT_FUND_NAME,
                balanceCents = 0L,
                allocationPerCycleCents = 0L,
                targetAmountCents = null,
                sortOrder = 0,
                originInstallId = writerInstallId,
                lastModifiedByInstallId = writerInstallId,
                createdAtEpochMs = exportedAtEpochMs,
                updatedAtEpochMs = exportedAtEpochMs,
                closedAtEpochMs = null,
                deletedAtEpochMs = null,
                modClock = snapshotModClock
            )
        )
    }

    private fun SnapshotEnvelopeV1.toFundTransactionEntities(): List<FundTransactionEntity> {
        return fundTransactions.orEmpty().map { record ->
            val type = FundTransactionType.entries.find { it.name == record.type }
                ?: throw SnapshotOperationException(SnapshotError.MalformedSnapshot)
            FundTransactionEntity(
                uuid = record.uuid,
                fundUuid = record.fundUuid,
                amountCents = record.amountCents,
                type = type,
                description = record.description,
                dateEpochMs = record.dateEpochMs
            )
        }
    }

    private fun InputStream.readBoundedBytes(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytes = 0

        while (true) {
            val readCount = read(buffer)
            if (readCount == -1) {
                return output.toByteArray()
            }
            totalBytes += readCount
            if (totalBytes > maxBytes) {
                throw SnapshotOperationException(
                    SnapshotError.IoFailure("The selected snapshot file is too large to import safely.")
                )
            }
            output.write(buffer, 0, readCount)
        }
    }

    internal companion object {
        const val MAX_SNAPSHOT_BYTES = 8 * 1024 * 1024
    }
}
