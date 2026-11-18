package com.jstensix.cardspending.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jstensix.cardspending.data.SpendingRepository
import com.jstensix.cardspending.model.Card
import com.jstensix.cardspending.model.ExclusionItem
import com.jstensix.cardspending.model.Issuer
import com.jstensix.cardspending.rules.findMaskConflict
import com.jstensix.cardspending.rules.isValidLast4
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CardEditState(
    val issuer: Issuer = Issuer.LOTTE,
    val last4: String = "",
    val nickname: String = "",
    val exclusions: Set<ExclusionItem> = emptySet(),
    val conflictWarning: String? = null,
    val canSave: Boolean = false,
)

class CardEditViewModel(private val repo: SpendingRepository, private val cardId: Long?) : ViewModel() {
    private val form = MutableStateFlow(CardEditState())

    val state: StateFlow<CardEditState> = combine(form, repo.observeCards()) { f, cards ->
        val conflict = findMaskConflict(cards, toCard(f))
        f.copy(
            conflictWarning = conflict?.let { "「${it.nickname}」과 끝 4자리의 1·3번째 숫자가 같아요. 알림으로는 두 카드를 구분할 수 없어요." },
            canSave = isValidLast4(f.last4),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CardEditState())

    init {
        if (cardId != null) {
            viewModelScope.launch {
                repo.getCard(cardId)?.let { card ->
                    form.value = CardEditState(card.issuer, card.last4, card.nickname, card.exclusions)
                }
            }
        }
    }

    fun setIssuer(value: Issuer) = form.update { it.copy(issuer = value) }
    fun setLast4(value: String) = form.update { it.copy(last4 = value.take(4)) }
    fun setNickname(value: String) = form.update { it.copy(nickname = value) }
    fun toggleExclusion(item: ExclusionItem) = form.update {
        it.copy(exclusions = if (item in it.exclusions) it.exclusions - item else it.exclusions + item)
    }

    /** Saves even when another card has the same visible digits; the warning is informational. */
    suspend fun save(): Boolean {
        val f = form.value
        if (!isValidLast4(f.last4)) return false
        repo.saveCard(toCard(f).copy(nickname = f.nickname.trim().ifBlank { f.issuer.displayName }))
        return true
    }

    suspend fun delete() {
        cardId?.let { repo.deleteCard(it) }
    }

    private fun toCard(f: CardEditState) =
        Card(id = cardId ?: 0, issuer = f.issuer, last4 = f.last4, nickname = f.nickname, exclusions = f.exclusions)
}
