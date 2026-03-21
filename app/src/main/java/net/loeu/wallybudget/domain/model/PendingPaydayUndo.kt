package net.loeu.wallybudget.domain.model

import java.time.LocalDate
import java.time.format.DateTimeParseException

data class PendingPaydayUndo(
    val previousSettings: UserSettings,
    val policiesToRestore: List<BudgetPolicy>,
    val policiesToDeactivate: List<BudgetPolicy>,
    val adjustmentsToRestore: List<BudgetAdjustment>,
    val adjustmentsToDeactivate: List<BudgetAdjustment>,
    val bucketPoliciesToRestore: List<BucketAllocationPolicy> = emptyList(),
    val bucketPoliciesToDeactivate: List<BucketAllocationPolicy> = emptyList(),
    val bucketAdjustmentsToRestore: List<BucketAllocationAdjustment> = emptyList(),
    val bucketAdjustmentsToDeactivate: List<BucketAllocationAdjustment> = emptyList(),
    val expiresAtExclusive: String
) {
    private val parsedExpiryDate: LocalDate by lazy {
        try {
            LocalDate.parse(expiresAtExclusive)
        } catch (exception: DateTimeParseException) {
            throw IllegalStateException("Invalid expiresAtExclusive: '$expiresAtExclusive'", exception)
        }
    }

    fun expiresAtExclusiveDate(): LocalDate = parsedExpiryDate
}
