package com.jstensix.cardspending.rules

import com.jstensix.cardspending.model.Card
import com.jstensix.cardspending.model.ExclusionItem
import com.jstensix.cardspending.model.Issuer
import com.jstensix.cardspending.model.TxKind
import java.time.LocalDateTime
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthlySummaryTest {
    private val taxCard = Card(1, Issuer.LOTTE, "7512", "롯데", setOf(ExclusionItem.TAX))

    @Test fun excludedAndCancel_computeTotals() {
        val txs = listOf(
            tx(id = 1, amount = 10000, merchant = "편의점"),
            tx(id = 2, amount = 50000, merchant = "국세"),
            tx(id = 3, amount = 3000, merchant = "편의점", kind = TxKind.CANCEL, originalId = 1),
        )
        val summary = summarizeMonth(listOf(taxCard), txs, txs.associateBy { it.id })
        assertEquals(CardMonthSummary(taxCard, 57000, 7000), summary.cards.single())
    }

    @Test fun cancelOfPreviousMonth_reducesThisMonth() {
        val october = tx(id = 1, amount = 20000, merchant = "편의점", approvedAt = at(20, month = 10))
        val cancel = tx(id = 2, amount = 20000, merchant = "편의점", kind = TxKind.CANCEL, originalId = 1)
        val summary = summarizeMonth(listOf(taxCard), listOf(cancel), mapOf(1L to october))
        assertEquals(CardMonthSummary(taxCard, -20000, -20000), summary.cards.single())
    }

    @Test fun unmatchedMask_countsUnregistered() {
        val summary = summarizeMonth(listOf(taxCard), listOf(tx(id = 1, mask = "9*9*")), emptyMap())
        assertEquals(1, summary.unregisteredCount)
        assertEquals(CardMonthSummary(taxCard, 0, 0), summary.cards.single())
    }

    @Test fun deleted_ignored() {
        val summary = summarizeMonth(listOf(taxCard), listOf(tx(id = 1, deleted = true)), emptyMap())
        assertEquals(CardMonthSummary(taxCard, 0, 0), summary.cards.single())
        assertEquals(0, summary.unregisteredCount)
    }

    @Test fun cardWithoutTransactions_hasZeroRow() {
        val other = Card(2, Issuer.HANA, "0381", "하나")
        val summary = summarizeMonth(listOf(taxCard, other), listOf(tx(id = 1, amount = 1000)), emptyMap())
        assertEquals(listOf(CardMonthSummary(taxCard, 1000, 1000), CardMonthSummary(other, 0, 0)), summary.cards)
    }

    @Test fun monthBounds_novemberRange() {
        assertEquals(
            LocalDateTime.of(2026, 11, 1, 0, 0) to LocalDateTime.of(2026, 12, 1, 0, 0),
            monthBounds(YearMonth.of(2026, 11)),
        )
    }
}
