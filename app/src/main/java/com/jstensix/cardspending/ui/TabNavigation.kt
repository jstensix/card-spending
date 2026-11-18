package com.jstensix.cardspending.ui

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

/** Bottom-tab navigation: one saved back stack per tab. */
fun NavHostController.navigateToTab(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/**
 * Where the main screen's banners and empty state lead. These targets are tabs, so they must be opened as
 * tabs: a plain push leaves the tab back stacks inconsistent and the 홈 tab then restores the pushed screen.
 */
class MainNavActions(private val nav: NavHostController) {
    fun openCards() = nav.navigateToTab(CardsRoute)
    fun openSettings() = nav.navigateToTab(SettingsRoute)
    fun openUnparsed() = nav.navigateToTab(UnparsedRoute)
}
