package net.loeu.wallybudget.ui.navigation

import kotlin.reflect.KClass
import kotlinx.serialization.Serializable
import net.loeu.wallybudget.R

internal const val BUCKETS_NAVIGATION_LABEL = "Buckets"

@Serializable
object HomeRoute

@Serializable
object PortfolioRoute

@Serializable
object HistoryRoute

@Serializable
object AnalysisRoute

@Serializable
object SettingsRoute

@Serializable
object CycleCloseoutRoute

@Serializable
object CycleCloseoutReviewRoute

internal data class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<out Any>,
    val iconRes: Int,
    val label: String
)

internal enum class NavigationChromeDestination {
    Analysis,
    Settings,
    Other
}

internal val primaryTopLevelDestinations = listOf(
    TopLevelDestination(
        route = HomeRoute,
        routeClass = HomeRoute::class,
        iconRes = R.drawable.ic_money_bag,
        label = BUCKETS_NAVIGATION_LABEL
    ),
    TopLevelDestination(
        route = PortfolioRoute,
        routeClass = PortfolioRoute::class,
        iconRes = R.drawable.ic_finance,
        label = "Portfolio"
    ),
    TopLevelDestination(
        route = HistoryRoute,
        routeClass = HistoryRoute::class,
        iconRes = R.drawable.ic_history,
        label = "History"
    )
)

internal val settingsTopLevelDestination = TopLevelDestination(
    route = SettingsRoute,
    routeClass = SettingsRoute::class,
    iconRes = R.drawable.ic_settings,
    label = "Settings"
)
