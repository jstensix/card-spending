package com.jstensix.cardspending.parsing

import com.jstensix.cardspending.model.Issuer
import com.jstensix.cardspending.model.TxKind
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

private val POSTED = LocalDateTime.of(2026, 11, 18, 11, 43, 20)
private const val SAMPLE = LOTTE_SAMPLE
private fun msg(body: String) = NotificationMessage("com.kakao.talk", "롯데카드", body, POSTED)

class LotteParserTest {
    @Test fun sample_parses() {
        assertEquals(
            ParsedTransaction(
                Issuer.LOTTE, TxKind.APPROVAL, 11000, "세븐일레븐 판교LH테크노점", "7*1*", false,
                LocalDateTime.of(2026, 11, 18, 11, 43), 3220782, SAMPLE,
            ),
            LotteParser.parse(msg(SAMPLE)),
        )
    }

    @Test fun crlfAndNbsp_parsesSame() {
        val variant = SAMPLE.replace("\n", "\r\n").replace("11,000원 승인", "11,000원 승인 ")
        assertEquals(11000L, LotteParser.parse(msg(variant))?.amount)
        assertEquals("7*1*", LotteParser.parse(msg(variant))?.mask)
    }

    @Test fun installmentLine_setsInstallment() {
        assertEquals(true, LotteParser.parse(msg(SAMPLE.replace("일시불,", "3개월,")))?.installment)
    }

    @Test fun cancelWords_setCancel() {
        assertEquals(TxKind.CANCEL, LotteParser.parse(msg(SAMPLE.replace("원 승인", "원 승인취소")))?.kind)
        assertEquals(TxKind.CANCEL, LotteParser.parse(msg(SAMPLE.replace("원 승인", "원 취소")))?.kind)
    }

    @Test fun missingCumulative_stillParses() {
        val withoutCumulative = SAMPLE.replace("누적금액 3,220,782원", "")
        assertNotNull(LotteParser.parse(msg(withoutCumulative)))
        assertNull(LotteParser.parse(msg(withoutCumulative))?.cumulative)
    }

    @Test fun missingDateLine_returnsNull() {
        assertNull(LotteParser.parse(msg(SAMPLE.replace("일시불, 11/18 11:43\n", ""))))
    }

    @Test fun unrelatedText_returnsNull() {
        assertNull(LotteParser.parse(msg("안녕하세요")))
    }
}
