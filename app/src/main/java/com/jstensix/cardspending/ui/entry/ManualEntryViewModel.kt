package com.jstensix.cardspending.ui.entry

import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.flow.first
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
    /** A same-day payment of the same amount already exists on this card (e.g. it also arrived as a notification). */
    val duplicateWarning: String? = null,
    val canSave: Boolean = false,
    /** Editing a saved hand-entered payment instead of adding one. */
    val editing: Boolean = false,
    /** False while an edited payment is still being read; the screen waits so its fields start filled. */
    val loaded: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ManualEntryViewModel(
    private val repo: SpendingRepository,
    cardId: Long?,
    private val unparsedId: Long?,
    cancel: Boolean,
    private val txId: Long? = null,
    /**
     * Holds the typed amount and merchant across process death. The screen restores its own copy of the text, so
     * without this an edit would show the user's changes but save the stored row it re-read.
     */
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val now: () -> LocalDateTime = LocalDateTime::now,
) : ViewModel() {
    private val form = MutableStateFlow(
        now().let { start ->
            EntryState(
                cardId = cardId,
                cancel = cancel,
                amountText = savedState[KEY_AMOUNT] ?: "",
                merchant = savedState[KEY_MERCHANT] ?: "",
                date = start.toLocalDate(),
                time = start.toLocalTime().withSecond(0).withNano(0),
                editing = txId != null,
                loaded = txId == null,
            )
        },
    )

    /** The saved row being edited; its id, origin and creation time carry over on save. */
    private var editingTx: Transaction? = null

    val state: StateFlow<EntryState> = combine(form, repo.observeCards()) { f, cards -> f.copy(cards = cards) }
        .mapLatest { f ->
            val card = f.selectedCard()
            val amount = f.amount()
            val candidates = if (f.cancel && card != null && amount != null) {
                // An approval being edited into a cancel cannot cancel itself.
                repo.approvalsForCancel(card.issuer, maskOf(card.last4), f.approvedAt(), amount).filter { it.id != txId }
            } else {
                emptyList()
            }
            // The same payment may already be here from a notification (e.g. app push parsed, KakaoTalk copy unrecognized).
            val sameDay = if (card != null && amount != null) {
                val kind = if (f.cancel) TxKind.CANCEL else TxKind.APPROVAL
                repo.findSameDay(TxSource.NOTIFICATION, card.issuer, maskOf(card.last4), amount, kind, f.date).firstOrNull()
            } else {
                null
            }
            f.copy(
                originalCandidates = candidates,
                duplicateWarning = sameDay?.let {
                    "같은 날 같은 금액의 결제가 이미 있어요: ${it.merchant} ${it.approvedAt.toLocalTime()}. 중복이 아닌지 확인해 주세요."
                },
                canSave = card != null && amount != null && f.merchant.isNotBlank(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, form.value)

    init {
        if (txId != null) {
            viewModelScope.launch {
                val tx = repo.getTransaction(txId)
                editingTx = tx
                if (tx == null) {
                    form.update { it.copy(loaded = true) }
                    return@launch
                }
                // Payments store the card as issuer + mask; a deleted card leaves the choice to the user.
                val card = repo.observeCards().first().firstOrNull { it.issuer == tx.issuer && maskOf(it.last4) == tx.mask }
                form.update {
                    it.copy(
                        cardId = card?.id,
                        cancel = tx.kind == TxKind.CANCEL,
                        amountText = savedState[KEY_AMOUNT] ?: tx.amount.toString(),
                        merchant = savedState[KEY_MERCHANT] ?: tx.merchant,
                        date = tx.approvedAt.toLocalDate(),
                        time = tx.approvedAt.toLocalTime().withSecond(0).withNano(0),
                        installment = tx.installment,
                        originalId = tx.originalId,
                        loaded = true,
                    )
                }
            }
        }
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
    fun setAmount(text: String) {
        val digits = text.filter(Char::isDigit)
        savedState[KEY_AMOUNT] = digits
        form.update { it.copy(amountText = digits) }
    }

    fun setMerchant(value: String) {
        savedState[KEY_MERCHANT] = value
        form.update { it.copy(merchant = value) }
    }
    fun setDate(value: LocalDate) = form.update { it.copy(date = value) }
    fun setTime(value: LocalTime) = form.update { it.copy(time = value) }
    fun setInstallment(value: Boolean) = form.update { it.copy(installment = value) }
    fun setOriginal(id: Long?) = form.update { it.copy(originalId = id) }

    suspend fun save(): Boolean {
        val f = form.value
        val card = f.cardId?.let { repo.getCard(it) } ?: return false
        val amount = f.amount() ?: return false
        if (f.merchant.isBlank()) return false
        if (txId != null) {
            val original = editingTx ?: return false
            return repo.updateManual(
                original.copy(
                    issuer = card.issuer,
                    kind = if (f.cancel) TxKind.CANCEL else TxKind.APPROVAL,
                    amount = amount,
                    merchant = f.merchant.trim(),
                    mask = maskOf(card.last4),
                    installment = f.installment,
                    approvedAt = f.approvedAt(),
                    originalId = if (f.cancel) f.originalId else null,
                ),
            )
        }
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

private const val KEY_AMOUNT = "amountText"
private const val KEY_MERCHANT = "merchant"
