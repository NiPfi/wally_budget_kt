package net.loeu.wallybudget

import net.loeu.wallybudget.ui.navigation.NavigationChromeDestination
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainNavigationChromeTest {

    @Test
    fun shouldShowNavigationChrome_hidesBottomNavigationOnSettings() {
        assertFalse(
            shouldShowNavigationChrome(
                currentDestination = NavigationChromeDestination.Settings,
                usesVerticalNavigation = false
            )
        )
    }

    @Test
    fun shouldShowNavigationChrome_keepsRailNavigationOnSettings() {
        assertTrue(
            shouldShowNavigationChrome(
                currentDestination = NavigationChromeDestination.Settings,
                usesVerticalNavigation = true
            )
        )
    }

    @Test
    fun shouldShowNavigationChrome_hidesChromeOnAnalysis() {
        assertFalse(
            shouldShowNavigationChrome(
                currentDestination = NavigationChromeDestination.Analysis,
                usesVerticalNavigation = false
            )
        )
    }

    @Test
    fun shouldShowNavigationChrome_showsChromeOnOtherCompactDestinations() {
        assertTrue(
            shouldShowNavigationChrome(
                currentDestination = NavigationChromeDestination.Other,
                usesVerticalNavigation = false
            )
        )
    }
}
