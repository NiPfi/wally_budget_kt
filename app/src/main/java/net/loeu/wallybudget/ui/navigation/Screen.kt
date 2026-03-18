package net.loeu.wallybudget.ui.navigation

sealed class Screen(val route: String) {
    object CycleCloseout : Screen("cycle_closeout")
    object CycleCloseoutReview : Screen("cycle_closeout_review")
    object MigrationSetup : Screen("migration_setup")
    object Home : Screen("home")
    object Analysis : Screen("analysis")
    object Settings : Screen("settings")
    object History : Screen("history")
}
