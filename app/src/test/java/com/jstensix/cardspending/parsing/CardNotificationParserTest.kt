package com.jstensix.cardspending.parsing

import com.jstensix.cardspending.model.Issuer
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class CardNotificationParserTest {
    private val t = LocalDateTime.of(2026, 11, 18, 12, 0)
    private fun m(pkg: String, title: String?, body: String) = NotificationMessage(pkg, title, body, t)

    @Test fun lotteSample_parsed() {
        val outcome = CardNotificationParser.parse(m("com.kakao.talk", "롯데카드", LOTTE_SAMPLE), emptySet())
        assertEquals(Issuer.LOTTE, (outcome as ParseOutcome.Parsed).tx.issuer)
    }

    @Test fun hanaSample_parsed() {
        val outcome = CardNotificationParser.parse(m("com.hanacard.app", HANA_TITLE, HANA_BODY), emptySet())
        assertEquals(Issuer.HANA, (outcome as ParseOutcome.Parsed).tx.issuer)
    }

    @Test fun privateChat_ignored() {
        assertEquals(
            ParseOutcome.Ignored,
            CardNotificationParser.parse(m("com.kakao.talk", "엄마", "저녁 먹었어?"), emptySet()),
        )
    }

    @Test fun unknownIssuerFormat_candidate() {
        assertEquals(
            ParseOutcome.Candidate,
            CardNotificationParser.parse(m("com.kakao.talk", "하나카드", "[하나카드] 홍*동님 9,900원 승인"), emptySet()),
        )
    }

    @Test fun maskAndAmountInUnknownLayout_candidate() {
        assertEquals(
            ParseOutcome.Candidate,
            CardNotificationParser.parse(m("x", null, "카드(0*8*) 12,000원"), emptySet()),
        )
    }

    @Test fun knownPackagePaymentText_candidate_unknownPackage_ignored() {
        val body = "결제 12,000원 완료"
        assertEquals(
            ParseOutcome.Candidate,
            CardNotificationParser.parse(m("com.hana.app", "알림", body), setOf("com.hana.app")),
        )
        assertEquals(
            ParseOutcome.Ignored,
            CardNotificationParser.parse(m("com.shop", "쿠팡", body), setOf("com.hana.app")),
        )
    }
}
