package com.example.ui.navigation

sealed class Screen(val route: String, val title: String) {
    object Home : Screen("home", "Home")
    object Restrictions : Screen("restrictions", "Restrictions")
    object Detox : Screen("detox", "Detox")
    object Stats : Screen("stats", "Statistics")
    object Settings : Screen("settings", "Settings")
}
