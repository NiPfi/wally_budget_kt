package net.loeu.wallybudget.ui.navigation

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object CycleCloseout : Screen("cycle_closeout")
    object CycleCloseoutReview : Screen("cycle_closeout_review")
    object Home : Screen("home")
    object Analysis : Screen("analysis")
    object Settings : Screen("settings")
    object History : Screen("history")
}
