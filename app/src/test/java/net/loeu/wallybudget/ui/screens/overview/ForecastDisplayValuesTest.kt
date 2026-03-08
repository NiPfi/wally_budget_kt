package net.loeu.wallybudget.ui.screens.overview

import org.junit.Assert.assertEquals
import org.junit.Test

class ForecastDisplayValuesTest {

    @Test
    fun calculateAvailableRecoverableOverspendCents_keepsFullAmountWhileStillWithinTodayAllowance() {
        assertEquals(
            3_152L,
            calculateAvailableRecoverableOverspendCents(
                remainingTodayCents = 1_000L,
                recoverableOverspendCents = 3_152L
            )
        )
    }

    @Test
    fun calculateAvailableRecoverableOverspendCents_burnsDownDollarForDollarAfterOverspendingToday() {
        assertEquals(
            2_152L,
            calculateAvailableRecoverableOverspendCents(
                remainingTodayCents = -1_000L,
                recoverableOverspendCents = 3_152L
            )
        )
        assertEquals(
            0L,
            calculateAvailableRecoverableOverspendCents(
                remainingTodayCents = -3_813L,
                recoverableOverspendCents = 3_152L
            )
        )
    }

    @Test
    fun calculateSafeToSpendNowCents_isZeroWhenOverspendHasConsumedRecoverableBuffer() {
        val availableRecoverableOverspendCents = calculateAvailableRecoverableOverspendCents(
            remainingTodayCents = -3_813L,
            recoverableOverspendCents = 3_152L
        )

        assertEquals(
            0L,
            calculateSafeToSpendNowCents(
                remainingTodayCents = -3_813L,
                availableRecoverableOverspendCents = availableRecoverableOverspendCents
            )
        )
    }
}
