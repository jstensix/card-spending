package com.jstensix.cardspending.parsing

import com.jstensix.cardspending.model.Card
import com.jstensix.cardspending.model.Issuer
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class CardNotificationParserTest {
    private val t = LocalDateTime.of(2026, 11, 18, 12, 0)
    private fun m(pkg: String, title: String?, body: String) = NotificationMessage(pkg, title, body, t)

    // Registered cards: Lotte 7419 shows as 7*1*, Hana 0381 as 0*8*.
    private val cards = listOf(
        Card(id = 1, issuer = Issuer.LOTTE, last4 = "7419", nickname = "텔로"),
        Card(id = 2, issuer = Issuer.HANA, last4 = "0381", nickname = "심플"),
    )

    private fun outcome(message: NotificationMessage, registered: List<Card> = cards) = CardNotificationParser.parse(message, registered)

    @Test fun lotteSample_parsed() {
        assertEquals(Issuer.LOTTE, (outcome(m("com.kakao.talk", "롯데카드", LOTTE_SAMPLE)) as ParseOutcome.Parsed).tx.issuer)
    }

    // Recognized formats are stored whether or not the card is registered.
    @Test fun hanaSample_parsedWithoutCards() {
        val parsed = outcome(m("com.hanacard.app", HANA_TITLE, HANA_BODY), emptyList())
        assertEquals(Issuer.HANA, (parsed as ParseOutcome.Parsed).tx.issuer)
    }

    @Test fun privateChat_ignored() {
        assertEquals(ParseOutcome.Ignored, outcome(m("com.kakao.talk", "엄마", "저녁 먹었어?")))
    }

    @Test fun unknownIssuerFormat_candidate() {
        assertEquals(ParseOutcome.Candidate, outcome(m("com.kakao.talk", "하나카드", "[하나카드] 홍*동님 9,900원 승인 0*8*")))
    }

    @Test fun issuerTitleWithoutNumber_ignored() {
        assertEquals(ParseOutcome.Ignored, outcome(m("com.kakao.talk", "롯데카드", "이번 달 혜택 안내")))
    }

    @Test fun issuerNameOnly_ignored() {
        assertEquals(ParseOutcome.Ignored, outcome(m("com.pickle.client", "잭슨", "롯데카드 있으시면 하이마트(859,140원)가 더 싸요")))
    }

    @Test fun adWithoutCardNumber_ignored() {
        assertEquals(ParseOutcome.Ignored, outcome(m("com.hanaskcard.paycla", "(광고)[하나카드](1800-1111)", "이번 주 혜택을 확인하세요")))
    }

    @Test fun unregisteredMask_ignored() {
        assertEquals(ParseOutcome.Ignored, outcome(m("x", null, "카드(5*2*) 12,000원")))
    }

    @Test fun maskAndAmountInUnknownLayout_candidate() {
        assertEquals(ParseOutcome.Candidate, outcome(m("x", null, "카드(0*8*) 12,000원")))
    }

    @Test fun plainLast4_candidate() {
        assertEquals(ParseOutcome.Candidate, outcome(m("x", null, "카드 끝자리 7419 승인 12,000원")))
    }

    @Test fun last4InsideNumber_notMatched() {
        assertEquals(ParseOutcome.Ignored, outcome(m("x", null, "결제 17,419원")))
        assertEquals(ParseOutcome.Ignored, outcome(m("x", null, "주문번호 174190")))
    }

    @Test fun noCards_unknownTextIgnored() {
        assertEquals(ParseOutcome.Ignored, outcome(m("x", null, "카드(0*8*) 12,000원"), emptyList()))
    }

    @Test fun kakaoChatWithPaymentWords_ignored() {
        assertEquals(ParseOutcome.Ignored, outcome(m("com.kakao.talk", "친구", "회비 30,000원 결제 부탁해 7419")))
    }

    @Test fun kakaoChatMentioningIssuer_ignored() {
        assertEquals(ParseOutcome.Ignored, outcome(m("com.kakao.talk", "친구", "롯데카드 혜택 좋더라 10,000원 할인 7*1*")))
    }

    @Test fun conversationMessage_onlyTitleCounts() {
        val chat = NotificationMessage("com.some.messenger", "친구", "하나카드 0*8* 5,000원", t, conversation = true)
        assertEquals(ParseOutcome.Ignored, outcome(chat))
    }

    @Test fun foreignCurrencyCardPush_candidate() {
        assertEquals(
            ParseOutcome.Candidate,
            outcome(m("com.hanacard.app", "(해외승인) 12.00 USD", "AMAZON / 신용(일시불,0*8*) / 11.18 17:29")),
        )
    }
}
