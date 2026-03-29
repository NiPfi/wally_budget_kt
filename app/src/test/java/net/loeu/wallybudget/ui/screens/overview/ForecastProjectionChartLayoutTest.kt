package net.loeu.wallybudget.ui.screens.overview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ForecastProjectionChartLayoutTest {

    @Test
    fun computeChartLayout_usesInclusiveTodayMarkerPosition() {
        val layout = computeChartLayout(
            params = ChartParams(
                totalDays = 30,
                daysElapsed = 0,
                budgetCents = 250_000L,
                totalSpentCents = 0L,
                projectedCents = 120_000L,
                lowerBoundCents = 100_000L,
                upperBoundCents = 140_000L,
            ),
            chartW = 300f,
            chartH = 100f,
        )

        assertEquals(10f, layout.todayX, 0.001f)
    }

    @Test
    fun computeChartLayout_keepsCoordinatesFiniteWhenAllAmountsAreZero() {
        val layout = computeChartLayout(
            params = ChartParams(
                totalDays = 30,
                daysElapsed = 0,
                budgetCents = 0L,
                totalSpentCents = 0L,
                projectedCents = 0L,
                lowerBoundCents = 0L,
                upperBoundCents = 0L,
            ),
            chartW = 300f,
            chartH = 100f,
        )

        assertFalse(layout.budgetY.isNaN())
        assertFalse(layout.projectedEndY.isNaN())
        assertEquals(100f, layout.budgetY, 0.001f)
        assertEquals(100f, layout.projectedEndY, 0.001f)
    }
}
