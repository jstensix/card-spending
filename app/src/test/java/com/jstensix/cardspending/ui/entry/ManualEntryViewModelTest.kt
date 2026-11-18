package com.jstensix.cardspending.ui.entry

import com.jstensix.cardspending.data.AppDatabase
import com.jstensix.cardspending.data.SpendingRepository
import com.jstensix.cardspending.model.Card
import com.jstensix.cardspending.model.Issuer
import com.jstensix.cardspending.model.TxKind
import com.jstensix.cardspending.model.TxSource
import com.jstensix.cardspending.model.UnparsedNotification
import com.jstensix.cardspending.ui.await
import com.jstensix.cardspending.ui.inMemoryDb
import com.jstensix.cardspending.ui.notificationTx
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ManualEntryViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: SpendingRepository
    private val now = LocalDateTime.of(2026, 11, 18, 12, 0)
    private var cardId = 0L

    @Before fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        db = inMemoryDb()
        repo = SpendingRepository(db) {}
        cardId = runBlocking { repo.saveCard(Card(issuer = Issuer.LOTTE, last4 = "7512", nickname = "텔로")) }
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(cancel: Boolean = false, unparsedId: Long? = null) =
        ManualEntryViewModel(repo, cardId, unparsedId, cancel) { now }

    private fun savedNovember() = runBlocking { repo.observeMonth(YearMonth.of(2026, 11)).first().transactions }

    @Test fun saveApproval_usesCardMaskAndManualSource() {
        val viewModel = vm()
        viewModel.setAmount("12000")
        viewModel.setMerchant("식당")
        viewModel.state.await { it.canSave }
        assertTrue(runBlocking { viewModel.save() })
        val tx = savedNovember().single()
        assertEquals("7*1*", tx.mask)
        assertEquals(TxSource.MANUAL, tx.source)
        assertEquals(TxKind.APPROVAL, tx.kind)
        assertEquals(12000L, tx.amount)
        assertEquals(now, tx.approvedAt)
    }

    @Test fun saveCancel_linksSelectedOriginal() {
        val approvalId = runBlocking { repo.insertNotificationTx(notificationTx(11000, "편의점", LocalDateTime.of(2026, 11, 10, 12, 0))) }
        val viewModel = vm(cancel = true)
        viewModel.setAmount("5000")
        viewModel.setMerchant("편의점")
        viewModel.setOriginal(approvalId)
        viewModel.state.await { it.canSave }
        assertTrue(runBlocking { viewModel.save() })
        assertEquals(approvalId, savedNovember().single { it.kind == TxKind.CANCEL }.originalId)
    }

    @Test fun originalCandidates_excludeSmallerAmounts() {
        runBlocking {
            repo.insertNotificationTx(notificationTx(3000, "편의점", LocalDateTime.of(2026, 11, 10, 12, 0)))
            repo.insertNotificationTx(notificationTx(11000, "편의점", LocalDateTime.of(2026, 11, 11, 12, 0)))
        }
        val viewModel = vm(cancel = true)
        viewModel.setAmount("5000")
        val state = viewModel.state.await { it.originalCandidates.isNotEmpty() }
        assertEquals(listOf(11000L), state.originalCandidates.map { it.amount })
    }

    @Test fun fromUnparsed_prefillsAndDismisses() {
        val unparsedId = runBlocking {
            repo.insertUnparsedIfNew(
                UnparsedNotification(
                    contentHash = "h", packageName = "com.kakao.talk", title = "하나카드", body = "알 수 없는 문구",
                    postedAt = LocalDateTime.of(2026, 11, 18, 17, 30), parserVersion = 1,
                ),
            )
            repo.observeUnparsed().first().single().id
        }
        val viewModel = vm(unparsedId = unparsedId)
        val state = viewModel.state.await { it.rawText != null }
        assertEquals(LocalDate.of(2026, 11, 18), state.date)
        assertEquals(LocalTime.of(17, 30), state.time)
        viewModel.setAmount("9900")
        viewModel.setMerchant("식당")
        viewModel.state.await { it.canSave }
        assertTrue(runBlocking { viewModel.save() })
        assertTrue(runBlocking { repo.observeUnparsed().first() }.isEmpty())
    }

    @Test fun sameDayPaymentExists_warns() {
        runBlocking { repo.insertNotificationTx(notificationTx(9900, "거궁찬판교점", LocalDateTime.of(2026, 11, 18, 17, 29))) }
        val viewModel = vm()
        viewModel.setAmount("9900")
        val state = viewModel.state.await { it.duplicateWarning != null }
        assertTrue(state.duplicateWarning!!.contains("거궁찬판교점"))
    }

    @Test fun invalidInput_cannotSave() {
        val viewModel = vm()
        viewModel.setAmount("0")
        viewModel.setMerchant("식당")
        assertFalse(viewModel.state.await { it.amountText == "0" }.canSave)
        viewModel.setAmount("1000")
        viewModel.setMerchant("")
        assertFalse(viewModel.state.await { it.amountText == "1000" && it.merchant.isEmpty() }.canSave)
        assertFalse(runBlocking { viewModel.save() })
    }
}
