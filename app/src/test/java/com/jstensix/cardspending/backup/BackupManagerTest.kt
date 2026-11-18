package com.jstensix.cardspending.backup

import androidx.test.core.app.ApplicationProvider
import com.jstensix.cardspending.data.AppDatabase
import com.jstensix.cardspending.data.SpendingRepository
import com.jstensix.cardspending.model.Card
import com.jstensix.cardspending.model.Issuer
import com.jstensix.cardspending.model.TxKind
import com.jstensix.cardspending.ui.inMemoryDb
import com.jstensix.cardspending.ui.notificationTx
import java.io.IOException
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private class FakeDriveApi : DriveApi {
    val files = mutableListOf<Pair<DriveFile, String>>()
    var failUpload = false
    var uploads = 0
    private var clock = 0

    fun seed(name: String, content: String = "{}") = add(name, content)

    private fun add(name: String, content: String): DriveFile {
        clock++
        val file = DriveFile("id$clock", name, String.format("2026-11-%02dT00:00:00Z", clock))
        files += file to content
        return file
    }

    override suspend fun listBackups(token: String) = files.map { it.first }.sortedByDescending { it.createdTime }
    override suspend fun upload(token: String, name: String, content: String): DriveFile {
        if (failUpload) throw IOException("network down")
        uploads++
        return add(name, content)
    }
    override suspend fun download(token: String, fileId: String) = files.first { it.first.id == fileId }.second
    override suspend fun delete(token: String, fileId: String) {
        files.removeAll { it.first.id == fileId }
    }
    override suspend fun fetchEmail(token: String) = "me@example.com"
}

private class FakeTokenProvider(var result: TokenResult = TokenResult.Token("t")) : TokenProvider {
    override suspend fun accessToken() = result
}

@RunWith(RobolectricTestRunner::class)
class BackupManagerTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: SpendingRepository
    private lateinit var prefs: BackupPrefs
    private val drive = FakeDriveApi()
    private val tokens = FakeTokenProvider()
    private val now = LocalDateTime.of(2026, 11, 18, 11, 43, 0)
    private lateinit var manager: BackupManager

    @Before fun setUp() {
        db = inMemoryDb()
        repo = SpendingRepository(db) {}
        prefs = BackupPrefs(ApplicationProvider.getApplicationContext())
        prefs.email = "me@example.com"
        manager = BackupManager(repo, drive, tokens, prefs) { now }
        runBlocking { repo.saveCard(Card(issuer = Issuer.LOTTE, last4 = "7512", nickname = "텔로")) }
    }

    @After fun tearDown() = db.close()

    @Test fun backupNow_uploadsSnapshotAndRecordsSuccess() {
        assertEquals(BackupResult.Success, runBlocking { manager.backupNow() })
        val (file, content) = drive.files.single()
        assertEquals("backup-20261118-114300.json", file.name)
        assertTrue(content.contains("텔로"))
        assertEquals(now, prefs.lastSuccessAt)
        assertNull(prefs.lastError)
    }

    @Test fun backupNow_keepsTwoNewest() {
        drive.seed("old1")
        drive.seed("old2")
        drive.seed("old3")
        runBlocking { manager.backupNow() }
        assertEquals(listOf("backup-20261118-114300.json", "old3"), runBlocking { drive.listBackups("t") }.map { it.name })
    }

    @Test fun needsReconnect_doesNotUpload_setsWarning() {
        tokens.result = TokenResult.NeedsReconnect
        assertEquals(BackupResult.NeedsReconnect, runBlocking { manager.backupNow() })
        assertEquals(0, drive.uploads)
        assertEquals("구글 계정을 다시 연결해야 해요", prefs.lastError)
    }

    @Test fun uploadFailure_keepsExistingBackups() {
        drive.seed("old1")
        drive.seed("old2")
        drive.failUpload = true
        val result = runBlocking { manager.backupNow() }
        assertTrue(result is BackupResult.Failed)
        assertEquals(listOf("old2", "old1"), runBlocking { drive.listBackups("t") }.map { it.name })
        assertTrue(prefs.lastError!!.startsWith("백업에 실패했어요"))
    }

    @Test fun restoreLatest_replacesLocalData() {
        val approval = notificationTx(11000, "편의점", LocalDateTime.of(2026, 11, 10, 12, 0)).copy(id = 10)
        val cancel = approval.copy(id = 11, kind = TxKind.CANCEL, amount = 3000, originalId = 10)
        val remote = BackupData(
            exportedAt = "2026-11-17T09:00",
            cards = listOf(Card(5, Issuer.HANA, "0381", "원격")),
            transactions = listOf(approval, cancel),
            unparsed = emptyList(),
        )
        drive.seed("older", BackupCodec.encode(remote.copy(cards = listOf(Card(6, Issuer.HANA, "0381", "예전")))))
        drive.seed("newest", BackupCodec.encode(remote))

        assertEquals(BackupResult.Success, runBlocking { manager.restoreLatest() })

        assertEquals(listOf("원격"), runBlocking { repo.observeCards().first() }.map { it.nickname })
        assertEquals(10L, runBlocking { repo.getTransaction(11) }?.originalId)
    }
}
