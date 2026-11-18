package com.jstensix.cardspending.parsing

import com.jstensix.cardspending.model.Issuer
import com.jstensix.cardspending.model.TxKind
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

private val POSTED = LocalDateTime.of(2026, 11, 18, 17, 29, 40)
private const val BODY = HANA_BODY
private fun msg(title: String?, body: String = BODY) = NotificationMessage("com.hanacard.app", title, body, POSTED)

class HanaParserTest {
    @Test fun sample_parses() {
        val tx = HanaParser.parse(msg("(결제) 9,900원"))!!
        assertEquals(Issuer.HANA, tx.issuer)
        assertEquals(TxKind.APPROVAL, tx.kind)
        assertEquals(9900L, tx.amount)
        assertEquals("거궁찬판교점", tx.merchant)
        assertEquals("0*8*", tx.mask)
        assertFalse(tx.installment)
        assertEquals(LocalDateTime.of(2026, 11, 18, 17, 29), tx.approvedAt)
        assertEquals(532231L, tx.cumulative)
    }

    @Test fun newlineSeparated_parsesSame() {
        val a = HanaParser.parse(msg("(결제) 9,900원"))!!
        val b = HanaParser.parse(msg("(결제) 9,900원", BODY.replace(" / ", "\n")))!!
        assertEquals(a.copy(rawText = ""), b.copy(rawText = ""))
    }

    @Test fun cancelTitles_setCancel() {
        assertEquals(TxKind.CANCEL, HanaParser.parse(msg("(취소) 9,900원"))?.kind)
        assertEquals(TxKind.CANCEL, HanaParser.parse(msg("(결제취소) 9,900원"))?.kind)
    }

    @Test fun checkCardInstallment_setsInstallment() {
        assertEquals(true, HanaParser.parse(msg("(결제) 9,900원", BODY.replace("신용(일시불,", "체크(3개월,")))?.installment)
    }

    @Test fun missingTitle_returnsNull() {
        assertNull(HanaParser.parse(msg(null)))
    }

    @Test fun unrelatedBody_returnsNull() {
        assertNull(HanaParser.parse(msg("(결제) 9,900원", "이벤트 안내")))
    }
}
