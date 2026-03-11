package net.loeu.wallybudget.ui.screens.overview

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.assertEquals
import org.junit.Test

class OverviewPageNestedScrollConnectionTest {

    @Test
    fun `nested scroll connection reads latest collapse offset on each scroll`() {
        var collapseOffsetPx = 0f
        val connection = overviewNestedScrollConnection(
            enableHeaderCollapse = true,
            canExpand = { true },
            collapseOffsetPx = { collapseOffsetPx },
            setCollapseOffsetPx = { collapseOffsetPx = it },
            maxCollapsePx = { 100f }
        )

        val firstConsumed = connection.onPreScroll(
            available = Offset(0f, -20f),
            source = NestedScrollSource.UserInput
        )
        val secondConsumed = connection.onPreScroll(
            available = Offset(0f, -10f),
            source = NestedScrollSource.UserInput
        )

        assertEquals(-20f, firstConsumed.y, 0.001f)
        assertEquals(-10f, secondConsumed.y, 0.001f)
        assertEquals(30f, collapseOffsetPx, 0.001f)
    }
}
