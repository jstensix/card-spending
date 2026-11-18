package com.jstensix.cardspending.ui.theme

import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The app is light-only, so system-bar icons must stay dark even when the phone is in dark mode. */
@RunWith(RobolectricTestRunner::class)
class SystemBarsTest {
    private fun assertDarkIcons() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.enableBrandEdgeToEdge()
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        assertTrue("status bar icons", controller.isAppearanceLightStatusBars)
        assertTrue("navigation bar icons", controller.isAppearanceLightNavigationBars)
    }

    @Test fun lightMode_darkIcons() = assertDarkIcons()

    @Config(qualifiers = "night")
    @Test fun nightMode_stillDarkIcons() = assertDarkIcons()
}
