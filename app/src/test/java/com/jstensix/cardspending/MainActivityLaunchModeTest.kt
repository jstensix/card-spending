package com.jstensix.cardspending

import android.content.ComponentName
import android.content.pm.ActivityInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The whole app is this one activity. Opening it from outside (app info "열기", a reinstall-and-run) while it is
 * already running must reuse it: a second instance stacks another home screen that back then has to dig through.
 */
@RunWith(RobolectricTestRunner::class)
class MainActivityLaunchModeTest {
    @Test fun mainActivity_isSingleTask() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val info = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        assertEquals(ActivityInfo.LAUNCH_SINGLE_TASK, info.launchMode)
    }
}
