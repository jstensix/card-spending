package com.jstensix.cardspending.ui

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Regression for the final review: after opening a tab screen from a main-screen banner, 홈 must go home. */
@RunWith(RobolectricTestRunner::class)
class MainNavActionsTest {
    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private fun newNav(): NavHostController {
        val nav = NavHostController(ApplicationProvider.getApplicationContext<Context>())
        nav.navigatorProvider.addNavigator(ComposeNavigator())
        nav.setLifecycleOwner(Owner().apply { registry.currentState = Lifecycle.State.RESUMED })
        nav.setViewModelStore(ViewModelStore())
        nav.graph = nav.createGraph(startDestination = MainRoute) {
            composable<MainRoute> { }
            composable<CardsRoute> { }
            composable<UnparsedRoute> { }
            composable<SettingsRoute> { }
        }
        return nav
    }

    private fun NavHostController.onMain() = currentDestination?.hasRoute(MainRoute::class) == true

    @Test fun unparsedBanner_thenHomeTab_returnsToMain() {
        val nav = newNav()
        MainNavActions(nav).openUnparsed()
        nav.navigateToTab(MainRoute)
        assertTrue(nav.onMain())
    }

    @Test fun settingsBanner_afterTabTrip_thenHomeTab_returnsToMain() {
        val nav = newNav()
        nav.navigateToTab(CardsRoute)
        nav.navigateToTab(MainRoute)
        MainNavActions(nav).openSettings()
        nav.navigateToTab(MainRoute)
        assertTrue(nav.onMain())
    }

    @Test fun cardsBanner_thenOtherTab_thenHomeTab_returnsToMain() {
        val nav = newNav()
        MainNavActions(nav).openCards()
        nav.navigateToTab(CardsRoute)
        nav.navigateToTab(MainRoute)
        assertTrue(nav.onMain())
    }
}
