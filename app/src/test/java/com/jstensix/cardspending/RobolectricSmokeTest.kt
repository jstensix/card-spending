package com.jstensix.cardspending

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RobolectricSmokeTest {
    @Test fun appContext_hasPackageName() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("com.jstensix.cardspending", context.packageName)
    }
}
