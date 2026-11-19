package com.jstensix.cardspending.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jstensix.cardspending.data.SpendingRepository
import com.jstensix.cardspending.model.Card
import com.jstensix.cardspending.model.ExclusionItem
import com.jstensix.cardspending.model.Issuer
import com.jstensix.cardspending.model.Tier
import com.jstensix.cardspending.rules.findMaskConflict
import com.jstensix.cardspending.rules.isValidLast4
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Most cards publish 3 tiers; 5 leaves room for the rest. */
const val MAX_TIERS = 5

/** One 실적 구간 row being typed: [amountText] holds digits only. */
data class TierInput(val amountText: String = "", val benefit: String = "")

data class CardEditState(
    val issuer: Issuer = Issuer.LOTTE,
    val last4: String = "",
    val nickname: String = "",
    val exclusions: Set<ExclusionItem> = emptySet(),
    val tiers: List<TierInput> = emptyList(),
    val conflictWarning: String? = null,
    val tierWarning: String? = null,
    val canSave: Boolean = false,
)

class CardEditViewModel(private val repo: SpendingRepository, private val cardId: Long?) : ViewModel() {
    private val form = MutableStateFlow(CardEditState())

    val state: StateFlow<CardEditState> = combine(form, repo.observeCards()) { f, cards ->
        val conflict = findMaskConflict(cards, toCard(f))
        val tierWarning = tierWarning(f.tiers)
        f.copy(
            conflictWarning = conflict?.let { "「${it.nickname}」과 끝 4자리의 1·3번째 숫자가 같아요. 알림으로는 두 카드를 구분할 수 없어요." },
            tierWarning = tierWarning,
            canSave = isValidLast4(f.last4) && tierWarning == null,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CardEditState())

    init {
        if (cardId != null) {
            viewModelScope.launch {
                repo.getCard(cardId)?.let { card ->
                    form.value = CardEditState(
                        card.issuer,
                        card.last4,
                        card.nickname,
                        card.exclusions,
                        tiers = card.tiers.map { TierInput(it.amount.toString(), it.benefit) },
                    )
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

    fun addTier() = form.update { if (it.tiers.size >= MAX_TIERS) it else it.copy(tiers = it.tiers + TierInput()) }
    fun removeTier(index: Int) = form.update { it.copy(tiers = it.tiers.filterIndexed { i, _ -> i != index }) }
    fun setTierAmount(index: Int, text: String) = updateTier(index) { it.copy(amountText = text.filter(Char::isDigit)) }
    fun setTierBenefit(index: Int, text: String) = updateTier(index) { it.copy(benefit = text) }

    private fun updateTier(index: Int, change: (TierInput) -> TierInput) = form.update {
        it.copy(tiers = it.tiers.mapIndexed { i, row -> if (i == index) change(row) else row })
    }

    /** Saves even when another card has the same visible digits; the warning is informational. */
    suspend fun save(): Boolean {
        val f = form.value
        if (!isValidLast4(f.last4) || tierWarning(f.tiers) != null) return false
        val tiers = f.tiers.map { Tier(it.amountText.toLong(), it.benefit.trim()) }.sortedBy { it.amount }
        repo.saveCard(toCard(f).copy(nickname = f.nickname.trim().ifBlank { f.issuer.displayName }, tiers = tiers))
        return true
    }

    suspend fun delete() {
        cardId?.let { repo.deleteCard(it) }
    }

    private fun toCard(f: CardEditState) =
        Card(id = cardId ?: 0, issuer = f.issuer, last4 = f.last4, nickname = f.nickname, exclusions = f.exclusions)
}

/** Why the typed tiers cannot be saved yet, or null when they can. */
private fun tierWarning(rows: List<TierInput>): String? {
    val amounts = rows.map { it.amountText.toLongOrNull() }
    if (amounts.any { it == null || it <= 0 }) return "구간 금액을 입력해 주세요"
    if (amounts.toSet().size != amounts.size) return "같은 금액의 구간이 있어요"
    return null
}
