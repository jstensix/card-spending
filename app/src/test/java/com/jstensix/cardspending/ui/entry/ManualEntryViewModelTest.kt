package com.jstensix.cardspending.ui.entry

import androidx.lifecycle.SavedStateHandle
import com.jstensix.cardspending.data.AppDatabase
import com.jstensix.cardspending.data.SpendingRepository
import com.jstensix.cardspending.model.Card
import com.jstensix.cardspending.model.Issuer
import com.jstensix.cardspending.model.Transaction
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

    // Edit mode is opened from a payment's sheet: no card or cancel preset, only the payment id.
    private fun editVm(txId: Long) = ManualEntryViewModel(repo, null, null, false, txId) { now }

    private fun manualTx(
        amount: Long,
        merchant: String,
        at: LocalDateTime,
        mask: String = "7*1*",
        issuer: Issuer = Issuer.LOTTE,
        kind: TxKind = TxKind.APPROVAL,
        installment: Boolean = false,
        originalId: Long? = null,
        manualRecognition: Boolean? = null,
    ): Long = runBlocking {
        repo.addManual(
            Transaction(
                issuer = issuer, kind = kind, amount = amount, merchant = merchant, mask = mask, installment = installment,
                approvedAt = at, source = TxSource.MANUAL, originalId = originalId, manualRecognition = manualRecognition,
                createdAt = at.plusMinutes(1),
            ),
        )
    }

    private fun stored(id: Long) = runBlocking { repo.getTransaction(id) }!!

    @Test fun edit_prefillsAllFields() {
        val id = manualTx(12000, "식당", LocalDateTime.of(2026, 11, 10, 19, 30), installment = true)
        val state = editVm(id).state.await { it.loaded }
        assertTrue(state.editing)
        assertEquals(cardId, state.cardId)
        assertEquals("12000", state.amountText)
        assertEquals("식당", state.merchant)
        assertEquals(LocalDate.of(2026, 11, 10), state.date)
        assertEquals(LocalTime.of(19, 30), state.time)
        assertTrue(state.installment)
        assertFalse(state.cancel)
    }

    @Test fun edit_save_updatesSameRowKeepingIdCreatedAtAndRecognition() {
        val at = LocalDateTime.of(2026, 11, 10, 19, 30)
        val id = manualTx(12000, "식당", at, manualRecognition = false)
        val viewModel = editVm(id)
        viewModel.state.await { it.loaded }
        viewModel.setAmount("15000")
        viewModel.setMerchant("식당2")
        viewModel.state.await { it.canSave && it.amountText == "15000" && it.merchant == "식당2" }
        assertTrue(runBlocking { viewModel.save() })
        val tx = savedNovember().single()
        assertEquals(id, tx.id)
        assertEquals(15000L, tx.amount)
        assertEquals("식당2", tx.merchant)
        assertEquals(false, tx.manualRecognition)
        assertEquals(at.plusMinutes(1), tx.createdAt)
        assertEquals(TxSource.MANUAL, tx.source)
    }

    @Test fun edit_cancelToApproval_clearsOriginal() {
        val approvalId = runBlocking { repo.insertNotificationTx(notificationTx(30000, "마트", LocalDateTime.of(2026, 11, 9, 12, 0))) }
        val id = manualTx(5000, "마트", LocalDateTime.of(2026, 11, 10, 12, 0), kind = TxKind.CANCEL, originalId = approvalId)
        val viewModel = editVm(id)
        assertEquals(approvalId, viewModel.state.await { it.loaded }.originalId)
        viewModel.setCancel(false)
        viewModel.state.await { !it.cancel && it.canSave }
        assertTrue(runBlocking { viewModel.save() })
        assertEquals(TxKind.APPROVAL, stored(id).kind)
        assertEquals(null, stored(id).originalId)
    }

    @Test fun edit_excludesSelfFromOriginalCandidates() {
        val otherId = runBlocking { repo.insertNotificationTx(notificationTx(30000, "마트", LocalDateTime.of(2026, 11, 9, 12, 0))) }
        val id = manualTx(20000, "마트", LocalDateTime.of(2026, 11, 10, 12, 0))
        val viewModel = editVm(id)
        viewModel.state.await { it.loaded }
        viewModel.setCancel(true)
        val ids = viewModel.state.await { it.cancel && it.originalCandidates.isNotEmpty() }.originalCandidates.map { it.id }
        assertTrue(otherId in ids)
        assertFalse(id in ids)
    }

    @Test fun edit_notificationTx_refusesSave() {
        val id = runBlocking { repo.insertNotificationTx(notificationTx(11000, "편의점", LocalDateTime.of(2026, 11, 10, 12, 0))) }
        val viewModel = editVm(id)
        viewModel.state.await { it.loaded }
        viewModel.setAmount("1")
        viewModel.state.await { it.amountText == "1" }
        assertFalse(runBlocking { viewModel.save() })
        assertEquals(11000L, stored(id).amount)
    }

    @Test fun typedText_isKeptInSavedState() {
        val saved = SavedStateHandle()
        val viewModel = ManualEntryViewModel(repo, cardId, null, false, null, saved) { now }
        viewModel.setAmount("9900")
        viewModel.setMerchant("식당")
        assertEquals("9900", saved.get<String>("amountText"))
        assertEquals("식당", saved.get<String>("merchant"))
    }

    // Android killed the app mid-edit: the screen restores what was typed, so the ViewModel must save that,
    // not the stored row it re-reads.
    @Test fun edit_afterProcessDeath_restoredTextWinsOverStoredRow() {
        val id = manualTx(12000, "식당", LocalDateTime.of(2026, 11, 10, 19, 30))
        val saved = SavedStateHandle(mapOf("amountText" to "15000", "merchant" to "식당2"))
        val viewModel = ManualEntryViewModel(repo, null, null, false, id, saved) { now }
        val state = viewModel.state.await { it.loaded }
        assertEquals("15000", state.amountText)
        assertEquals("식당2", state.merchant)
        viewModel.state.await { it.canSave }
        assertTrue(runBlocking { viewModel.save() })
        assertEquals(15000L, stored(id).amount)
        assertEquals("식당2", stored(id).merchant)
    }

    @Test fun edit_cardGone_leavesCardEmpty() {
        val id = manualTx(8000, "서점", LocalDateTime.of(2026, 11, 10, 12, 0), mask = "0*8*", issuer = Issuer.HANA)
        val state = editVm(id).state.await { it.loaded }
        assertEquals(null, state.cardId)
        assertFalse(state.canSave)
    }
}
