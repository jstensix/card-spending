package com.jstensix.cardspending.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jstensix.cardspending.data.SpendingRepository
import com.jstensix.cardspending.model.Card
import com.jstensix.cardspending.model.Transaction
import com.jstensix.cardspending.model.TxKind
import com.jstensix.cardspending.rules.Recognition
import com.jstensix.cardspending.rules.RecognitionReason
import com.jstensix.cardspending.rules.owns
import com.jstensix.cardspending.rules.recognize
import com.jstensix.cardspending.rules.summarizeMonth
import java.time.YearMonth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class TxItem(val tx: Transaction, val signedAmount: Long, val recognized: Boolean, val label: String)

data class CardDetailState(
    val card: Card?,
    val month: YearMonth,
    val items: List<TxItem>,
    val payment: Long,
    val recognized: Long,
)

fun recognitionLabel(recognition: Recognition): String = when (val reason = recognition.reason) {
    RecognitionReason.Rule -> "인정"
    is RecognitionReason.Excluded -> "미인정 · ${reason.item.label}"
    RecognitionReason.Manual -> if (recognition.recognized) "직접 지정 · 인정" else "직접 지정 · 미인정"
}

class CardDetailViewModel(private val repo: SpendingRepository, cardId: Long, month: YearMonth) : ViewModel() {
    val state: StateFlow<CardDetailState> = combine(
        repo.observeCards().map { cards -> cards.firstOrNull { it.id == cardId } },
        repo.observeMonth(month),
    ) { card, data ->
        if (card == null) return@combine CardDetailState(null, month, emptyList(), 0, 0)
        val items = data.transactions.filter { card.owns(it) }.map { tx ->
            val recognition = recognize(tx, card, tx.originalId?.let(data.originalsById::get))
            val signed = if (tx.kind == TxKind.CANCEL) -tx.amount else tx.amount
            TxItem(tx, signed, recognition.recognized, recognitionLabel(recognition))
        }
        val totals = summarizeMonth(listOf(card), data.transactions, data.originalsById).cards.single()
        CardDetailState(card, month, items, totals.payment, totals.recognized)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CardDetailState(null, month, emptyList(), 0, 0))

    /** null = back to the card's rules. */
    suspend fun setRecognition(txId: Long, value: Boolean?) = repo.setManualRecognition(txId, value)

    suspend fun delete(txId: Long) = repo.deleteTransaction(txId)
}
