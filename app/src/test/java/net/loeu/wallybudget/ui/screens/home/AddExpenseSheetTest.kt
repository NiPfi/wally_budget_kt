package net.loeu.wallybudget.ui.screens.home

import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.BucketBalanceBehavior
import net.loeu.wallybudget.domain.model.BucketTrackingMode
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class AddExpenseSheetTest {

    @Test
    fun resolveInitialBucketUuid_fallsBackToFirstOpenBucketWhenInitialSelectionIsInvalid() {
        val resolved = resolveInitialBucketUuid(
            initialBucketUuid = "closed-bucket",
            bucketOptions = listOf(
                bucket("open-bucket", "Open"),
                bucket("other-bucket", "Other")
            )
        )

        assertEquals("open-bucket", resolved)
    }

    @Test
    fun resolveInitialBucketUuid_fallsBackToDefaultWhenNoOptionsExist() {
        val resolved = resolveInitialBucketUuid(
            initialBucketUuid = "missing-bucket",
            bucketOptions = emptyList()
        )

        assertEquals(DEFAULT_SPENDING_BUCKET_UUID, resolved)
    }

    private fun bucket(bucketUuid: String, name: String) = BudgetBucket(
        bucketUuid = bucketUuid,
        name = name,
        trackingMode = BucketTrackingMode.DAILY_TARGET,
        balanceBehavior = BucketBalanceBehavior.RETURN_TO_PORTFOLIO,
        defaultAllocatedAmountCents = 0L,
        sortOrder = 0,
        originInstallId = "test-install-id",
        lastModifiedByInstallId = "test-install-id",
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
        modClock = "0000000000001-0000-test-install-id"
    )
}
