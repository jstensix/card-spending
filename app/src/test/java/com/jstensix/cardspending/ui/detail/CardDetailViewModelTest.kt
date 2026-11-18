package com.jstensix.cardspending.ui.detail

import com.jstensix.cardspending.data.AppDatabase
import com.jstensix.cardspending.data.SpendingRepository
import com.jstensix.cardspending.model.Card
import com.jstensix.cardspending.model.ExclusionItem
import com.jstensix.cardspending.model.Issuer
import com.jstensix.cardspending.model.TxKind
import com.jstensix.cardspending.ui.await
import com.jstensix.cardspending.ui.inMemoryDb
import com.jstensix.cardspending.ui.notificationTx
import java.time.LocalDateTime
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CardDetailViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: SpendingRepository
    private val november = YearMonth.of(2026, 11)
    private var cardId = 0L
    private var taxTxId = 0L

    @Before fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        db = inMemoryDb()
        repo = SpendingRepository(db) {}
        runBlocking {
            cardId = repo.saveCard(Card(issuer = Issuer.LOTTE, last4 = "7512", nickname = "텔로", exclusions = setOf(ExclusionItem.TAX)))
            taxTxId = repo.insertNotificationTx(notificationTx(50000, "국세", LocalDateTime.of(2026, 11, 4, 12, 0)))
            repo.insertNotificationTx(notificationTx(10000, "편의점", LocalDateTime.of(2026, 11, 3, 12, 0)))
        }
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm() = CardDetailViewModel(repo, cardId, november)
    private fun CardDetailState.labelOf(txId: Long) = items.single { it.tx.id == txId }.label

    @Test fun excludedItem_labelAndTotals() {
        val state = vm().state.await { it.items.size == 2 }
        assertEquals("미인정 · 세금", state.labelOf(taxTxId))
        assertEquals(60000L, state.payment)
        assertEquals(10000L, state.recognized)
    }

    @Test fun manualRecognition_updatesLabelAndTotals() {
        val viewModel = vm()
        viewModel.state.await { it.items.size == 2 }
        runBlocking { viewModel.setRecognition(taxTxId, true) }
        val state = viewModel.state.await { it.recognized == 60000L }
        assertEquals("직접 지정 · 인정", state.labelOf(taxTxId))
    }

    @Test fun resetToRule_restoresRuleLabel() {
        val viewModel = vm()
        runBlocking { viewModel.setRecognition(taxTxId, true) }
        viewModel.state.await { it.items.size == 2 && it.labelOf(taxTxId) == "직접 지정 · 인정" }
        runBlocking { viewModel.setRecognition(taxTxId, null) }
        val state = viewModel.state.await { it.items.size == 2 && it.labelOf(taxTxId) == "미인정 · 세금" }
        assertEquals(10000L, state.recognized)
    }

    @Test fun delete_removesItem() {
        val viewModel = vm()
        viewModel.state.await { it.items.size == 2 }
        runBlocking { viewModel.delete(taxTxId) }
        val state = viewModel.state.await { it.items.size == 1 }
        assertEquals("편의점", state.items.single().tx.merchant)
    }

    @Test fun cancel_hasNegativeSignedAmount() {
        val cancelId = runBlocking {
            repo.insertNotificationTx(notificationTx(3000, "편의점", LocalDateTime.of(2026, 11, 5, 12, 0), kind = TxKind.CANCEL))
        }
        val state = vm().state.await { it.items.size == 3 }
        assertEquals(-3000L, state.items.single { it.tx.id == cancelId }.signedAmount)
    }
}
