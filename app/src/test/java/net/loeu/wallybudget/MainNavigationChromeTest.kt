package net.loeu.wallybudget

import net.loeu.wallybudget.ui.navigation.Screen
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainNavigationChromeTest {

    @Test
    fun shouldShowNavigationChrome_hidesBottomNavigationOnSettings() {
        assertFalse(
            shouldShowNavigationChrome(
                currentRoute = Screen.Settings.route,
                usesVerticalNavigation = false
            )
        )
    }

    @Test
    fun shouldShowNavigationChrome_keepsRailNavigationOnSettings() {
        assertTrue(
            shouldShowNavigationChrome(
                currentRoute = Screen.Settings.route,
                usesVerticalNavigation = true
            )
        )
    }

    @Test
    fun shouldShowNavigationChrome_hidesChromeOnAnalysis() {
        assertFalse(
            shouldShowNavigationChrome(
                currentRoute = Screen.Analysis.route,
                usesVerticalNavigation = false
            )
        )
    }
}
