package com.jstensix.cardspending.backup

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupPrefsTest {
    private val prefs = BackupPrefs(ApplicationProvider.getApplicationContext())

    @Test fun warning_notConnected() {
        assertEquals("구글 드라이브 백업이 연결되지 않았어요", runBlocking { prefs.observeWarning().first() })
    }

    @Test fun warning_lastError() {
        prefs.email = "me@example.com"
        prefs.lastError = "x"
        assertEquals("x", runBlocking { prefs.observeWarning().first() })
    }

    @Test fun warning_none() {
        prefs.email = "me@example.com"
        prefs.lastError = null
        assertNull(runBlocking { prefs.observeWarning().first() })
    }
}
