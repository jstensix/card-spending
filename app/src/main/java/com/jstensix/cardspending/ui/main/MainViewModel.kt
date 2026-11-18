package com.jstensix.cardspending.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jstensix.cardspending.data.SpendingRepository
import com.jstensix.cardspending.model.Issuer
import com.jstensix.cardspending.model.TxKind
import com.jstensix.cardspending.rules.maskOf
import com.jstensix.cardspending.rules.owns
import com.jstensix.cardspending.rules.recognize
import com.jstensix.cardspending.rules.summarizeMonth
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class CardRow(
    val cardId: Long,
    val issuer: Issuer,
    val nickname: String,
    val mask: String,
    val payment: Long,
    val recognized: Long,
    /** Approvals this month on this card that do not count toward 실적. */
    val excludedCount: Int = 0,
)

data class MainUiState(
    val month: YearMonth,
    val rows: List<CardRow>,
    val unregisteredCount: Int,
    val unparsedCount: Int,
    val listenerEnabled: Boolean,
    val backupWarning: String?,
    val totalPayment: Long = 0,
    val totalRecognized: Long = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    repo: SpendingRepository,
    private val isListenerEnabled: () -> Boolean,
    backupWarning: Flow<String?>,
    initialMonth: YearMonth = YearMonth.now(),
) : ViewModel() {
    private val month = MutableStateFlow(initialMonth)
    private val listenerEnabled = MutableStateFlow(isListenerEnabled())

    val state: StateFlow<MainUiState> = combine(
        month.flatMapLatest { m -> repo.observeMonth(m).map { m to it } },
        repo.observeCards(),
        repo.observeUnparsed(),
        listenerEnabled,
        backupWarning,
    ) { (m, data), cards, unparsed, listener, warning ->
        val summary = summarizeMonth(cards, data.transactions, data.originalsById)
        val rows = summary.cards.map { row ->
            val excluded = data.transactions.count { tx ->
                !tx.deleted && tx.kind == TxKind.APPROVAL && row.card.owns(tx) &&
                    !recognize(tx, row.card, tx.originalId?.let(data.originalsById::get)).recognized
            }
            CardRow(row.card.id, row.card.issuer, row.card.nickname, maskOf(row.card.last4), row.payment, row.recognized, excluded)
        }
        MainUiState(
            month = m,
            rows = rows,
            unregisteredCount = summary.unregisteredCount,
            unparsedCount = unparsed.size,
            listenerEnabled = listener,
            backupWarning = warning,
            totalPayment = rows.sumOf { it.payment },
            totalRecognized = rows.sumOf { it.recognized },
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        MainUiState(initialMonth, emptyList(), 0, 0, listenerEnabled.value, null),
    )

    fun previousMonth() = month.update { it.minusMonths(1) }
    fun nextMonth() = month.update { it.plusMonths(1) }
    fun refreshDeviceStatus() {
        listenerEnabled.value = isListenerEnabled()
    }
}
