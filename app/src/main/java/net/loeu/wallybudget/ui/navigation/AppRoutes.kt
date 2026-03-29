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
    val iconRes: Int,
    val label: String
) {
    val routeClass: KClass<out Any> = route::class
}

internal enum class NavigationChromeDestination {
    Analysis,
    Settings,
    Other
}

internal val primaryTopLevelDestinations = listOf(
    TopLevelDestination(
        route = HomeRoute,
        iconRes = R.drawable.ic_money_bag,
        label = BUCKETS_NAVIGATION_LABEL
    ),
    TopLevelDestination(
        route = PortfolioRoute,
        iconRes = R.drawable.ic_finance,
        label = "Portfolio"
    ),
    TopLevelDestination(
        route = HistoryRoute,
        iconRes = R.drawable.ic_history,
        label = "History"
    )
)

internal val settingsTopLevelDestination = TopLevelDestination(
    route = SettingsRoute,
    iconRes = R.drawable.ic_settings,
    label = "Settings"
)
