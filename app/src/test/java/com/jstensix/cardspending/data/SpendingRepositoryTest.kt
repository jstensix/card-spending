package com.jstensix.cardspending.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jstensix.cardspending.model.Card
import com.jstensix.cardspending.model.ExclusionItem
import com.jstensix.cardspending.model.Issuer
import com.jstensix.cardspending.model.Transaction
import com.jstensix.cardspending.model.TxKind
import com.jstensix.cardspending.model.TxSource
import com.jstensix.cardspending.model.UnparsedNotification
import java.time.LocalDateTime
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SpendingRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: SpendingRepository
    private var changes = 0
    private val november = YearMonth.of(2026, 11)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = SpendingRepository(db) { changes++ }
    }

    @After fun tearDown() = db.close()

    private fun tx(
        approvedAt: LocalDateTime,
        source: TxSource = TxSource.NOTIFICATION,
        kind: TxKind = TxKind.APPROVAL,
        originalId: Long? = null,
        sourcePackage: String? = "com.kakao.talk",
        amount: Long = 11000,
    ) = Transaction(
        issuer = Issuer.LOTTE, kind = kind, amount = amount, merchant = "편의점", mask = "7*1*", installment = false,
        approvedAt = approvedAt, source = source, sourcePackage = sourcePackage, originalId = originalId, createdAt = approvedAt,
    )

    private fun unparsed(hash: String) = UnparsedNotification(
        contentHash = hash, packageName = "com.kakao.talk", title = "하나카드", body = "?",
        postedAt = LocalDateTime.of(2026, 11, 18, 12, 0), parserVersion = 1,
    )

    @Test fun saveCard_roundTripsExclusions() = runTest {
        val id = repo.saveCard(Card(issuer = Issuer.LOTTE, last4 = "7512", nickname = "텔로", exclusions = setOf(ExclusionItem.TAX, ExclusionItem.INSTALLMENT)))
        assertEquals(setOf(ExclusionItem.TAX, ExclusionItem.INSTALLMENT), repo.getCard(id)?.exclusions)
        assertEquals(listOf("텔로"), repo.observeCards().first().map { it.nickname })
    }

    @Test fun monthRange_includesLastMinute_excludesNextMonth() = runTest {
        repo.insertNotificationTx(tx(LocalDateTime.of(2026, 11, 30, 23, 59)))
        repo.insertNotificationTx(tx(LocalDateTime.of(2026, 12, 1, 0, 0)))
        assertEquals(
            listOf(LocalDateTime.of(2026, 11, 30, 23, 59)),
            repo.observeMonth(november).first().transactions.map { it.approvedAt },
        )
    }

    @Test fun observeMonth_includesOriginalOfCancel() = runTest {
        val octoberId = repo.insertNotificationTx(tx(LocalDateTime.of(2026, 10, 20, 12, 0)))
        repo.insertNotificationTx(tx(LocalDateTime.of(2026, 11, 2, 12, 0), kind = TxKind.CANCEL, originalId = octoberId))
        val data = repo.observeMonth(november).first()
        assertEquals(LocalDateTime.of(2026, 10, 20, 12, 0), data.originalsById[octoberId]?.approvedAt)
    }

    @Test fun deleteNotificationTx_isSoft() = runTest {
        val at = LocalDateTime.of(2026, 11, 18, 12, 0)
        val id = repo.insertNotificationTx(tx(at))
        repo.deleteTransaction(id)
        assertTrue(repo.observeMonth(november).first().transactions.isEmpty())
        assertEquals(1, repo.findSameKey(Issuer.LOTTE, "7*1*", 11000, TxKind.APPROVAL, at).size)
    }

    @Test fun deleteManualTx_isHard() = runTest {
        val id = repo.addManual(tx(LocalDateTime.of(2026, 11, 18, 12, 0), source = TxSource.MANUAL, sourcePackage = null))
        repo.deleteTransaction(id)
        assertNull(repo.getTransaction(id))
    }

    @Test fun insertUnparsedIfNew_ignoresSameHash() = runTest {
        repo.insertUnparsedIfNew(unparsed("h1"))
        repo.insertUnparsedIfNew(unparsed("h1"))
        assertEquals(1, repo.observeUnparsed().first().size)
    }

    @Test fun dismissedUnparsed_hiddenButBlocksReinsert() = runTest {
        repo.insertUnparsedIfNew(unparsed("h1"))
        repo.dismissUnparsed(repo.observeUnparsed().first().single().id)
        assertTrue(repo.observeUnparsed().first().isEmpty())
        repo.insertUnparsedIfNew(unparsed("h1"))
        assertTrue(repo.observeUnparsed().first().isEmpty())
    }

    @Test fun writes_callOnChanged() = runTest {
        repo.saveCard(Card(issuer = Issuer.HANA, last4 = "0381", nickname = "하나"))
        val id = repo.addManual(tx(LocalDateTime.of(2026, 11, 18, 12, 0), source = TxSource.MANUAL, sourcePackage = null))
        repo.setManualRecognition(id, true)
        assertEquals(3, changes)
    }

    @Test fun knownSourcePackages_distinct() = runTest {
        repo.insertNotificationTx(tx(LocalDateTime.of(2026, 11, 1, 12, 0), sourcePackage = "a"))
        repo.insertNotificationTx(tx(LocalDateTime.of(2026, 11, 2, 12, 0), sourcePackage = "a"))
        repo.insertNotificationTx(tx(LocalDateTime.of(2026, 11, 3, 12, 0), sourcePackage = "b"))
        assertEquals(setOf("a", "b"), repo.knownSourcePackages())
    }
}
