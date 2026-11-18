package com.jstensix.cardspending.rules

import com.jstensix.cardspending.model.Card
import com.jstensix.cardspending.model.Transaction
import com.jstensix.cardspending.model.TxKind
import java.time.LocalDateTime
import java.time.YearMonth

data class CardMonthSummary(val card: Card, val payment: Long, val recognized: Long)

data class MonthSummary(val cards: List<CardMonthSummary>, val unregisteredCount: Int)

/** [start, end): the 1st 00:00 of the month up to, not including, the 1st of the next month. */
fun monthBounds(month: YearMonth): Pair<LocalDateTime, LocalDateTime> =
    month.atDay(1).atStartOfDay() to month.plusMonths(1).atDay(1).atStartOfDay()

/** Cancels count in the month they happen, as negative amounts. */
fun summarizeMonth(cards: List<Card>, transactions: List<Transaction>, originalsById: Map<Long, Transaction>): MonthSummary {
    val live = transactions.filter { !it.deleted }
    val rows = cards.map { card ->
        var payment = 0L
        var recognized = 0L
        live.filter { card.owns(it) }.forEach { tx ->
            val signed = if (tx.kind == TxKind.CANCEL) -tx.amount else tx.amount
            payment += signed
            if (recognize(tx, card, tx.originalId?.let(originalsById::get)).recognized) recognized += signed
        }
        CardMonthSummary(card, payment, recognized)
    }
    val unregistered = live.count { tx -> cards.none { it.owns(tx) } }
    return MonthSummary(rows, unregistered)
}
