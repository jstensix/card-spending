package com.jstensix.cardspending.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jstensix.cardspending.data.SpendingRepository
import com.jstensix.cardspending.model.Issuer
import com.jstensix.cardspending.rules.maskOf
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
)

data class MainUiState(
    val month: YearMonth,
    val rows: List<CardRow>,
    val unregisteredCount: Int,
    val unparsedCount: Int,
    val listenerEnabled: Boolean,
    val backupWarning: String?,
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
        MainUiState(
            month = m,
            rows = summary.cards.map {
                CardRow(it.card.id, it.card.issuer, it.card.nickname, maskOf(it.card.last4), it.payment, it.recognized)
            },
            unregisteredCount = summary.unregisteredCount,
            unparsedCount = unparsed.size,
            listenerEnabled = listener,
            backupWarning = warning,
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
