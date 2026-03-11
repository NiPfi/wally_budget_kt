package net.loeu.wallybudget.domain.usecase

import android.net.Uri
import net.loeu.wallybudget.data.local.entity.BudgetPolicyEntity
import net.loeu.wallybudget.data.local.entity.ExpenseEntity
import net.loeu.wallybudget.data.snapshot.DecodedSnapshotPayload
import net.loeu.wallybudget.data.snapshot.DocumentUriGateway
import net.loeu.wallybudget.data.snapshot.GzipSnapshotCodec
import net.loeu.wallybudget.data.snapshot.SnapshotCompatibilityService
import net.loeu.wallybudget.data.snapshot.SnapshotJsonCodec
import net.loeu.wallybudget.data.snapshot.model.SnapshotEnvelopeV1
import net.loeu.wallybudget.domain.model.ExpenseCategory
import net.loeu.wallybudget.domain.model.SnapshotError
import net.loeu.wallybudget.domain.model.SnapshotImportPreview
import net.loeu.wallybudget.domain.model.UserSettings

@Suppress("ThrowsCount", "TooGenericExceptionCaught")
class PrepareSnapshotImportUseCase(
    private val documentUriGateway: DocumentUriGateway,
    private val gzipSnapshotCodec: GzipSnapshotCodec,
    private val snapshotJsonCodec: SnapshotJsonCodec,
    private val snapshotCompatibilityService: SnapshotCompatibilityService
) {
    suspend operator fun invoke(uri: Uri): PreparedSnapshotImport {
        val rawBytes = try {
            documentUriGateway.openInputStream(uri)?.use { it.readBytes() }
                ?: throw SnapshotImportException(
                    SnapshotError.IoFailure("Unable to open the selected snapshot file.")
                )
        } catch (exception: SnapshotImportException) {
            throw exception
        } catch (exception: Exception) {
            throw SnapshotImportException(
                SnapshotError.IoFailure("Unable to read the selected snapshot file."),
                exception
            )
        }

        val payload = try {
            gzipSnapshotCodec.decodeFromBytes(rawBytes)
        } catch (exception: Exception) {
            throw SnapshotImportException(SnapshotError.InvalidCompression, exception)
        }
        val envelope = try {
            snapshotJsonCodec.decode(payload.text)
        } catch (exception: Exception) {
            throw SnapshotImportException(SnapshotError.MalformedSnapshot, exception)
        }

        snapshotCompatibilityService.validateSchemaVersion(envelope.schemaVersion)?.let {
            throw SnapshotImportException(it)
        }
        if (envelope.format != SnapshotCompatibilityService.SNAPSHOT_FORMAT) {
            throw SnapshotImportException(SnapshotError.MalformedSnapshot)
        }

        return PreparedSnapshotImport(
            preview = envelope.toPreview(payload),
            settings = envelope.toUserSettings(),
            budgetPolicies = envelope.toBudgetPolicyEntities(),
            expenses = envelope.toExpenseEntities()
        )
    }

    private fun SnapshotEnvelopeV1.toPreview(payload: DecodedSnapshotPayload): SnapshotImportPreview {
        return SnapshotImportPreview(
            exportedAtEpochMs = exportedAtEpochMs,
            writerInstallId = writerInstallId,
            expenseCount = expenses.size,
            tombstoneCount = expenses.count { it.deletedAtEpochMs != null },
            budgetPolicyCount = budgetPolicies.size,
            defaultMonthlyBudgetCents = settings.defaultMonthlyBudgetCents,
            paydayDate = settings.paydayDate,
            compressed = payload.compressed
        )
    }

    private fun SnapshotEnvelopeV1.toUserSettings(): UserSettings {
        return UserSettings(
            monthlyBudgetCents = settings.defaultMonthlyBudgetCents,
            paydayDate = settings.paydayDate,
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

    private fun SnapshotEnvelopeV1.toExpenseEntities(): List<ExpenseEntity> {
        return expenses.map { record ->
            ExpenseEntity(
                recordUuid = record.recordUuid,
                amountCents = record.amountCents,
                description = record.description,
                timestamp = record.timestampEpochMs,
                expenseDate = record.expenseDate,
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
}

data class PreparedSnapshotImport(
    val preview: SnapshotImportPreview,
    val settings: UserSettings,
    val budgetPolicies: List<BudgetPolicyEntity>,
    val expenses: List<ExpenseEntity>
)

class SnapshotImportException(
    val snapshotError: SnapshotError,
    cause: Throwable? = null
) : IllegalStateException(cause)
