package com.jstensix.cardspending.ui.settings

import androidx.test.core.app.ApplicationProvider
import com.jstensix.cardspending.backup.BackupCodec
import com.jstensix.cardspending.backup.BackupData
import com.jstensix.cardspending.backup.BackupManager
import com.jstensix.cardspending.backup.BackupPrefs
import com.jstensix.cardspending.backup.FakeDriveApi
import com.jstensix.cardspending.backup.FakeTokenProvider
import com.jstensix.cardspending.data.AppDatabase
import com.jstensix.cardspending.data.SpendingRepository
import com.jstensix.cardspending.model.Card
import com.jstensix.cardspending.model.Issuer
import com.jstensix.cardspending.ui.await
import com.jstensix.cardspending.ui.inMemoryDb
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Prefs are checked directly: SharedPreferences listeners are delivered on the main looper, which these tests block.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: SpendingRepository
    private lateinit var prefs: BackupPrefs
    private val drive = FakeDriveApi()
    private var scheduled = 0
    private lateinit var vm: SettingsViewModel

    @Before fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        db = inMemoryDb()
        repo = SpendingRepository(db) {}
        prefs = BackupPrefs(ApplicationProvider.getApplicationContext())
        val manager = BackupManager(repo, drive, FakeTokenProvider(), prefs) { LocalDateTime.of(2026, 11, 18, 12, 0) }
        vm = SettingsViewModel(repo, drive, manager, prefs, scheduleBackup = { scheduled++ }, cancelBackup = {})
        // This phone already has a card (registered before connecting): the risky path.
        runBlocking { repo.saveCard(Card(issuer = Issuer.LOTTE, last4 = "7512", nickname = "이 폰")) }
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun seedRemote() = drive.seed(
        "backup-old.json",
        BackupCodec.encode(BackupData(exportedAt = "2026-11-01T00:00", cards = listOf(Card(9, Issuer.HANA, "0381", "원격")), transactions = emptyList(), unparsed = emptyList())),
    )

    private fun cardNames() = runBlocking { repo.observeCards().first() }.map { it.nickname }

    @Test fun remoteBackupExists_waitsForChoice() {
        seedRemote()
        vm.onTokenGranted("t")
        vm.state.await { it.remoteChoice != null }
        assertNull(prefs.email)
        assertEquals(0, scheduled)
        assertEquals(0, drive.uploads)
    }

    @Test fun chooseRestore_restoresThenConnects() {
        seedRemote()
        vm.onTokenGranted("t")
        vm.state.await { it.remoteChoice != null }
        vm.chooseRestore()
        vm.state.await { it.message == "드라이브에서 복원했어요" }
        assertEquals(listOf("원격"), cardNames())
        assertEquals("me@example.com", prefs.email)
        assertEquals(0, drive.uploads)
    }

    @Test fun chooseOverwrite_uploadsThisPhoneThenConnects() {
        seedRemote()
        vm.onTokenGranted("t")
        vm.state.await { it.remoteChoice != null }
        vm.chooseOverwrite()
        vm.state.await { it.message == "이 폰의 데이터로 백업했어요" }
        assertEquals(1, drive.uploads)
        assertEquals("me@example.com", prefs.email)
        assertEquals(listOf("이 폰"), cardNames())
    }

    @Test fun cancelConnect_staysDisconnected() {
        seedRemote()
        vm.onTokenGranted("t")
        vm.state.await { it.remoteChoice != null }
        vm.cancelConnect()
        val state = vm.state.await { it.remoteChoice == null }
        assertNull(prefs.email)
        assertEquals(0, scheduled)
        assertEquals("연결하지 않았어요", state.message)
    }

    @Test fun remoteCheckFails_staysDisconnected() {
        drive.failList = true
        vm.onTokenGranted("t")
        vm.state.await { it.message != null && !it.busy }
        assertNull(prefs.email)
        assertEquals(0, scheduled)
    }

    @Test fun noRemoteBackup_connectsAndSchedules() {
        vm.onTokenGranted("t")
        vm.state.await { it.message != null && !it.busy }
        assertEquals("me@example.com", prefs.email)
        assertEquals(1, scheduled)
    }
}
