package com.jstensix.cardspending.ui.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jstensix.cardspending.data.SpendingRepository
import com.jstensix.cardspending.model.Card
import com.jstensix.cardspending.model.Transaction
import com.jstensix.cardspending.model.TxKind
import com.jstensix.cardspending.model.TxSource
import com.jstensix.cardspending.rules.maskOf
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EntryState(
    val cards: List<Card> = emptyList(),
    val cardId: Long? = null,
    val cancel: Boolean = false,
    val amountText: String = "",
    val merchant: String = "",
    val date: LocalDate = LocalDate.MIN,
    val time: LocalTime = LocalTime.MIDNIGHT,
    val installment: Boolean = false,
    val originalCandidates: List<Transaction> = emptyList(),
    val originalId: Long? = null,
    val rawText: String? = null,
    val canSave: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ManualEntryViewModel(
    private val repo: SpendingRepository,
    cardId: Long?,
    private val unparsedId: Long?,
    cancel: Boolean,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) : ViewModel() {
    private val form = MutableStateFlow(
        now().let { start ->
            EntryState(cardId = cardId, cancel = cancel, date = start.toLocalDate(), time = start.toLocalTime().withSecond(0).withNano(0))
        },
    )

    val state: StateFlow<EntryState> = combine(form, repo.observeCards()) { f, cards -> f.copy(cards = cards) }
        .mapLatest { f ->
            val card = f.selectedCard()
            val amount = f.amount()
            val candidates = if (f.cancel && card != null && amount != null) {
                repo.approvalsForCancel(card.issuer, maskOf(card.last4), f.approvedAt(), amount)
            } else {
                emptyList()
            }
            f.copy(
                originalCandidates = candidates,
                canSave = card != null && amount != null && f.merchant.isNotBlank(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, form.value)

    init {
        if (unparsedId != null) {
            viewModelScope.launch {
                repo.getUnparsed(unparsedId)?.let { item ->
                    form.update {
                        it.copy(
                            rawText = listOfNotNull(item.title, item.body).joinToString("\n"),
                            date = item.postedAt.toLocalDate(),
                            time = item.postedAt.toLocalTime().withSecond(0).withNano(0),
                        )
                    }
                }
            }
        }
    }

    fun setCard(id: Long) = form.update { it.copy(cardId = id, originalId = null) }
    fun setCancel(value: Boolean) = form.update { it.copy(cancel = value, originalId = null) }
    fun setAmount(text: String) = form.update { it.copy(amountText = text.filter(Char::isDigit)) }
    fun setMerchant(value: String) = form.update { it.copy(merchant = value) }
    fun setDate(value: LocalDate) = form.update { it.copy(date = value) }
    fun setTime(value: LocalTime) = form.update { it.copy(time = value) }
    fun setInstallment(value: Boolean) = form.update { it.copy(installment = value) }
    fun setOriginal(id: Long?) = form.update { it.copy(originalId = id) }

    suspend fun save(): Boolean {
        val f = form.value
        val card = f.cardId?.let { repo.getCard(it) } ?: return false
        val amount = f.amount() ?: return false
        if (f.merchant.isBlank()) return false
        repo.addManual(
            Transaction(
                issuer = card.issuer,
                kind = if (f.cancel) TxKind.CANCEL else TxKind.APPROVAL,
                amount = amount,
                merchant = f.merchant.trim(),
                mask = maskOf(card.last4),
                installment = f.installment,
                approvedAt = f.approvedAt(),
                source = TxSource.MANUAL,
                originalId = if (f.cancel) f.originalId else null,
                createdAt = now(),
            ),
        )
        unparsedId?.let { repo.dismissUnparsed(it) }
        return true
    }

    private fun EntryState.selectedCard(): Card? = cards.firstOrNull { it.id == cardId }
    private fun EntryState.amount(): Long? = amountText.toLongOrNull()?.takeIf { it > 0 }
    private fun EntryState.approvedAt(): LocalDateTime = date.atTime(time.withSecond(0).withNano(0))
}
