package com.jstensix.cardspending.ui

import android.os.SystemClock

/**
 * Home's "press back twice to exit": the first press only warns, a second one within [windowMillis] exits.
 * The window matches Toast.LENGTH_SHORT, so the warning is still on screen while the second press counts.
 */
class ExitOnSecondBack(
    private val windowMillis: Long = 2_000,
    private val now: () -> Long = SystemClock::uptimeMillis,
) {
    private var warnedAt: Long? = null

    /** True when this press should close the app; false means show the warning. */
    fun onBack(): Boolean {
        val t = now()
        val last = warnedAt
        return if (last != null && t - last <= windowMillis) {
            warnedAt = null
            true
        } else {
            warnedAt = t
            false
        }
    }
}
