package net.loeu.wallybudget.domain.model

import java.time.LocalDate
import java.time.format.DateTimeParseException

enum class BucketTransferReason {
    CLOSE_SETTLEMENT,
    MANUAL_REALLOCATION
}

data class BucketTransfer(
    val transferUuid: String,
    val fromBucketUuid: String?,
    val toBucketUuid: String?,
    val amountCents: Long,
    val reason: BucketTransferReason,
    val cycleStartDate: String,
    val cycleEndDateExclusive: String,
    val effectiveDate: String,
    val originInstallId: String,
    val lastModifiedByInstallId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val deletedAtEpochMs: Long? = null,
    val modClock: String
) {
    private val parsedCycleStart: LocalDate by lazy {
        try {
            LocalDate.parse(cycleStartDate)
        } catch (exception: DateTimeParseException) {
            throw IllegalStateException("Invalid cycleStartDate: '$cycleStartDate'", exception)
        }
    }

    private val parsedCycleEndExclusive: LocalDate by lazy {
        try {
            LocalDate.parse(cycleEndDateExclusive)
        } catch (exception: DateTimeParseException) {
            throw IllegalStateException("Invalid cycleEndDateExclusive: '$cycleEndDateExclusive'", exception)
        }
    }

    private val parsedEffectiveDate: LocalDate by lazy {
        try {
            LocalDate.parse(effectiveDate)
        } catch (exception: DateTimeParseException) {
            throw IllegalStateException("Invalid effectiveDate: '$effectiveDate'", exception)
        }
    }

    fun cycleStart(): LocalDate = parsedCycleStart

    fun cycleEndExclusive(): LocalDate = parsedCycleEndExclusive

    fun effectiveDate(): LocalDate = parsedEffectiveDate
}
