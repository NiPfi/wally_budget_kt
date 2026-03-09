package net.loeu.wallybudget.ui.screens.overview

import net.loeu.wallybudget.data.model.SpendingForecast
import org.junit.Assert.assertEquals
import org.junit.Test

class ForecastDisplayValuesTest {

    @Test
    fun calculateAvailableRecoverableOverspendCents_keepsFullAmountWhileStillWithinTodayAllowance() {
        assertEquals(
            3_152L,
            calculateAvailableRecoverableOverspendCentsFromForecast(
                remainingTodayCents = 1_000L,
                forecast = testForecast(recoverableOverspendCents = 3_152L)
            )
        )
    }

    @Test
    fun calculateAvailableRecoverableOverspendCents_burnsDownDollarForDollarAfterOverspendingToday() {
        assertEquals(
            2_152L,
            calculateAvailableRecoverableOverspendCentsFromForecast(
                remainingTodayCents = -1_000L,
                forecast = testForecast(recoverableOverspendCents = 3_152L)
            )
        )
        assertEquals(
            0L,
            calculateAvailableRecoverableOverspendCentsFromForecast(
                remainingTodayCents = -3_813L,
                forecast = testForecast(recoverableOverspendCents = 3_152L)
            )
        )
    }

    @Test
    fun calculateSafeToSpendNowCents_isZeroWhenOverspendHasConsumedRecoverableBuffer() {
        val availableRecoverableOverspendCents = calculateAvailableRecoverableOverspendCentsFromForecast(
            remainingTodayCents = -3_813L,
            forecast = testForecast(recoverableOverspendCents = 3_152L)
        )

        assertEquals(
            0L,
            calculateSafeToSpendNowCents(
                remainingTodayCents = -3_813L,
                availableRecoverableOverspendCents = availableRecoverableOverspendCents
            )
        )
    }

    @Test
    fun calculateAvailableRecoverableOverspendCents_staysZeroWhenForecastMarksNoRecoverableHeadroom() {
        // grossRecoverableOverspendCents is positive, but the cap reduced it to zero.
        // The UI must use the capped field; if it mistakenly used the gross field it would
        // return 5_000 instead of 0, causing the user to see recoverable headroom that
        // doesn't actually exist.
        assertEquals(
            0L,
            calculateAvailableRecoverableOverspendCentsFromForecast(
                remainingTodayCents = 1_000L,
                forecast = testForecast(
                    recoverableOverspendCents = 0L,
                    grossRecoverableOverspendCents = 5_000L
                )
            )
        )
    }

    private fun testForecast(
        recoverableOverspendCents: Long,
        grossRecoverableOverspendCents: Long = recoverableOverspendCents
    ) = SpendingForecast(
        grossRecoverableOverspendCents = grossRecoverableOverspendCents,
        recoverableOverspendCents = recoverableOverspendCents
    )
}
