package com.jstensix.cardspending.ui.theme

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

/**
 * Edge-to-edge with dark system-bar icons always. The default (SystemBarStyle.auto) follows the phone's dark
 * mode and would draw white icons over this light-only app's white top bar.
 */
fun ComponentActivity.enableBrandEdgeToEdge() {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
    )
}
