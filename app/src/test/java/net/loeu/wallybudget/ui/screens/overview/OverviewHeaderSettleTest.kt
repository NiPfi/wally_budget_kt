package net.loeu.wallybudget.ui.screens.overview

import org.junit.Assert.assertEquals
import org.junit.Test

class OverviewHeaderSettleTest {

    @Test
    fun snapHeaderOffset_returnsZeroWhenThereIsNoCollapseRange() {
        assertEquals(0f, snapHeaderOffset(collapseOffsetPx = 24f, maxCollapsePx = 0f), 0.0001f)
    }

    @Test
    fun snapHeaderOffset_settlesExpandedWhenOffsetIsBelowHalfwayPoint() {
        assertEquals(0f, snapHeaderOffset(collapseOffsetPx = 19f, maxCollapsePx = 40f), 0.0001f)
    }

    @Test
    fun snapHeaderOffset_settlesCollapsedWhenOffsetReachesHalfwayPoint() {
        assertEquals(40f, snapHeaderOffset(collapseOffsetPx = 20f, maxCollapsePx = 40f), 0.0001f)
        assertEquals(40f, snapHeaderOffset(collapseOffsetPx = 32f, maxCollapsePx = 40f), 0.0001f)
    }
}
