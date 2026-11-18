package com.jstensix.cardspending.ingest

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jstensix.cardspending.data.AppDatabase
import com.jstensix.cardspending.data.SpendingRepository
import com.jstensix.cardspending.model.Issuer
import com.jstensix.cardspending.model.Transaction
import com.jstensix.cardspending.model.TxKind
import com.jstensix.cardspending.model.TxSource
import com.jstensix.cardspending.model.UnparsedNotification
import com.jstensix.cardspending.parsing.HANA_BODY
import com.jstensix.cardspending.parsing.HANA_TITLE
import com.jstensix.cardspending.parsing.LOTTE_SAMPLE
import com.jstensix.cardspending.parsing.NotificationMessage
import com.jstensix.cardspending.parsing.PARSER_VERSION
import java.time.LocalDateTime
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IngestorTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: SpendingRepository
    private lateinit var ingestor: Ingestor
    private val posted = LocalDateTime.of(2026, 11, 18, 17, 30, 0)
    private val november = YearMonth.of(2026, 11)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = SpendingRepository(db) {}
        ingestor = Ingestor(repo) { posted }
    }

    @After fun tearDown() = db.close()

    private fun lotte(body: String = LOTTE_SAMPLE) = NotificationMessage("com.kakao.talk", "롯데카드", body, posted)
    private fun hana(pkg: String = "com.hanacard.app", body: String = HANA_BODY) = NotificationMessage(pkg, HANA_TITLE, body, posted)
    private suspend fun monthTxs() = repo.observeMonth(november).first().transactions

    @Test fun lotteSample_insertedOnce() = runTest {
        assertEquals(listOf(IngestResult.INSERTED, IngestResult.DUPLICATE), ingestor.ingestAll(listOf(lotte(), lotte())))
        assertEquals(1, monthTxs().size)
    }

    @Test fun samePaymentFromTwoApps_deduped() = runTest {
        assertEquals(IngestResult.INSERTED, ingestor.ingest(hana("com.hanacard.app")))
        assertEquals(IngestResult.DUPLICATE, ingestor.ingest(hana("com.kakao.talk")))
    }

    @Test fun sameMinuteDifferentCumulative_bothInserted() = runTest {
        assertEquals(IngestResult.INSERTED, ingestor.ingest(hana()))
        assertEquals(IngestResult.INSERTED, ingestor.ingest(hana(body = HANA_BODY.replace("532,231", "542,131"))))
        assertEquals(2, monthTxs().size)
    }

    @Test fun softDeleted_doesNotReappear() = runTest {
        ingestor.ingest(lotte())
        repo.deleteTransaction(monthTxs().single().id)
        assertEquals(IngestResult.DUPLICATE, ingestor.ingest(lotte()))
        assertTrue(monthTxs().isEmpty())
    }

    @Test fun cancel_linksOriginal() = runTest {
        ingestor.ingest(lotte())
        val approvalId = monthTxs().single().id
        assertEquals(IngestResult.INSERTED, ingestor.ingest(lotte(LOTTE_SAMPLE.replace("원 승인", "원 승인취소"))))
        assertEquals(approvalId, monthTxs().single { it.kind == TxKind.CANCEL }.originalId)
    }

    @Test fun unknownCardFormat_storedOnceAsUnparsed() = runTest {
        val message = NotificationMessage("com.kakao.talk", "하나카드", "[하나카드] 홍*동님 9,900원 승인", posted)
        assertEquals(listOf(IngestResult.UNPARSED, IngestResult.UNPARSED), ingestor.ingestAll(listOf(message, message)))
        val rows = repo.observeUnparsed().first()
        assertEquals(1, rows.size)
        assertEquals(PARSER_VERSION, rows.single().parserVersion)
    }

    @Test fun privateChat_ignored_writesNothing() = runTest {
        assertEquals(IngestResult.IGNORED, ingestor.ingest(NotificationMessage("com.kakao.talk", "엄마", "저녁 먹었어?", posted)))
        assertTrue(repo.snapshot().second.isEmpty())
        assertTrue(repo.snapshot().third.isEmpty())
    }

    private fun manualLotte(approvedAt: LocalDateTime) = Transaction(
        issuer = Issuer.LOTTE, kind = TxKind.APPROVAL, amount = 11000, merchant = "세븐일레븐", mask = "7*1*",
        installment = false, approvedAt = approvedAt, source = TxSource.MANUAL, manualRecognition = false, createdAt = posted,
    )

    @Test fun manualEntrySameDay_upgradedByNotification() = runTest {
        val manualId = repo.addManual(manualLotte(LocalDateTime.of(2026, 11, 18, 9, 0)))
        assertEquals(IngestResult.MERGED, ingestor.ingest(lotte()))
        val tx = monthTxs().single()
        assertEquals(manualId, tx.id)
        assertEquals(TxSource.NOTIFICATION, tx.source)
        assertEquals(LocalDateTime.of(2026, 11, 18, 11, 43), tx.approvedAt)
        assertEquals(false, tx.manualRecognition)
    }

    @Test fun manualEntryOtherDay_notMerged() = runTest {
        repo.addManual(manualLotte(LocalDateTime.of(2026, 11, 17, 9, 0)))
        assertEquals(IngestResult.INSERTED, ingestor.ingest(lotte()))
        assertEquals(2, monthTxs().size)
    }

    @Test fun kakaoChatAfterLottePayment_notStored() = runTest {
        ingestor.ingest(lotte())
        val chat = NotificationMessage("com.kakao.talk", "친구", "회비 30,000원 결제 부탁해", posted)
        assertEquals(IngestResult.IGNORED, ingestor.ingest(chat))
        assertTrue(repo.snapshot().third.isEmpty())
    }

    @Test fun kakaoRepost_insertsEachOnce() = runTest {
        val m1 = lotte()
        val m2 = lotte(LOTTE_SAMPLE.replace("11,000원", "25,000원").replace("3,220,782", "3,245,782"))
        ingestor.ingestAll(listOf(m1))
        ingestor.ingestAll(listOf(m1, m2))
        assertEquals(2, monthTxs().size)
    }

    @Test fun concurrentDuplicates_insertOnce() = runTest {
        val results = withContext(Dispatchers.IO) {
            (1..20).map { i -> async { ingestor.ingest(hana(if (i % 2 == 0) "com.kakao.talk" else "com.hanacard.app")) } }.awaitAll()
        }
        assertEquals(1, results.count { it == IngestResult.INSERTED })
        assertEquals(1, monthTxs().size)
    }

    @Test fun reparsePending_convertsAndDismisses() = runTest {
        fun row(hash: String, body: String) = UnparsedNotification(
            contentHash = hash, packageName = "com.kakao.talk", title = "롯데카드", body = body, postedAt = posted, parserVersion = 0,
        )
        repo.insertUnparsedIfNew(row("a", LOTTE_SAMPLE))
        repo.insertUnparsedIfNew(row("b", "롯데카드 알 수 없는 문구"))

        assertEquals(1, ingestor.reparsePending())

        assertEquals(1, monthTxs().size)
        val visible = repo.observeUnparsed().first()
        assertEquals(listOf("b"), visible.map { it.contentHash })
        assertEquals(PARSER_VERSION, visible.single().parserVersion)
    }
}
