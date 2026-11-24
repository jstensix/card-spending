package com.jstensix.cardspending.parsing

import com.jstensix.cardspending.model.Issuer
import com.jstensix.cardspending.model.TxKind
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class HanaSmsParserTest {
    private fun sms(body: String, at: LocalDateTime) =
        NotificationMessage("com.samsung.android.messaging", HANA_SMS_TITLE, body, at)

    // The SMS gives only the date: the day comes from the text, the clock time from when the SMS arrived.
    @Test fun sample_parsed() {
        val tx = HanaSmsParser.parse(sms(HANA_SMS_BODY, LocalDateTime.of(2026, 11, 24, 11, 30)))!!
        assertEquals(Issuer.HANA, tx.issuer)
        assertEquals(TxKind.APPROVAL, tx.kind)
        assertEquals(26950L, tx.amount)
        assertEquals("SK브로드밴드-자동납", tx.merchant)
        assertEquals("0*8*", tx.mask)
        assertFalse(tx.installment)
        assertEquals(LocalDateTime.of(2026, 11, 23, 11, 30), tx.approvedAt)
        assertNull(tx.cumulative)
    }

    @Test fun yearBoundary_lastYear() {
        val body = HANA_SMS_BODY.replace("11/23", "12/31")
        val tx = HanaSmsParser.parse(sms(body, LocalDateTime.of(2027, 1, 1, 9, 5)))!!
        assertEquals(LocalDateTime.of(2026, 12, 31, 9, 5), tx.approvedAt)
    }

    @Test fun merchantWithSpaces() {
        val tx = HanaSmsParser.parse(sms("하나0*8*승인 김*수님 세븐일레븐 종로3가역점 4,500원 11/24 정상처리", LocalDateTime.of(2026, 11, 24, 8, 0)))!!
        assertEquals("세븐일레븐 종로3가역점", tx.merchant)
        assertEquals(4500L, tx.amount)
    }

    // Only the one confirmed layout is read; anything else stays in 알림함 for the user.
    @Test fun otherFormat_null() {
        assertNull(HanaSmsParser.parse(sms("하나0*8*취소 김*수님 가게 4,500원 11/24 정상처리", LocalDateTime.of(2026, 11, 24, 8, 0))))
        assertNull(HanaSmsParser.parse(NotificationMessage("com.hanacard.app", HANA_TITLE, HANA_BODY, LocalDateTime.of(2026, 11, 18, 17, 30))))
    }
}
