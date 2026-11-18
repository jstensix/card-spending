package com.jstensix.cardspending.rules

import com.jstensix.cardspending.model.Card
import com.jstensix.cardspending.model.ExclusionItem
import com.jstensix.cardspending.model.Issuer
import com.jstensix.cardspending.model.Transaction
import com.jstensix.cardspending.model.TxKind
import com.jstensix.cardspending.model.TxSource
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionTest {
    private val at = LocalDateTime.of(2026, 11, 18, 12, 0)
    private fun tx(merchant: String, kind: TxKind = TxKind.APPROVAL, manual: Boolean? = null) =
        Transaction(
            issuer = Issuer.LOTTE, kind = kind, amount = 1000, merchant = merchant, mask = "7*1*",
            installment = false, approvedAt = at, source = TxSource.NOTIFICATION, manualRecognition = manual, createdAt = at,
        )
    private val taxCard = Card(1, Issuer.LOTTE, "7512", "롯데", setOf(ExclusionItem.TAX))

    @Test fun excludedMerchant_notRecognized() {
        assertEquals(
            Recognition(false, RecognitionReason.Excluded(ExclusionItem.TAX)),
            recognize(tx("지방세 납부"), taxCard, null),
        )
    }

    @Test fun noExclusions_recognized() {
        assertEquals(
            Recognition(true, RecognitionReason.Rule),
            recognize(tx("지방세 납부"), taxCard.copy(exclusions = emptySet()), null),
        )
    }

    @Test fun manualOverridesRule() {
        assertEquals(Recognition(true, RecognitionReason.Manual), recognize(tx("지방세 납부", manual = true), taxCard, null))
    }

    @Test fun cancel_followsOriginal() {
        assertEquals(
            Recognition(false, RecognitionReason.Excluded(ExclusionItem.TAX)),
            recognize(tx("다른이름", TxKind.CANCEL), taxCard, tx("국세")),
        )
    }

    @Test fun cancelManual_overridesOriginal() {
        assertEquals(
            Recognition(false, RecognitionReason.Manual),
            recognize(tx("x", TxKind.CANCEL, manual = false), taxCard, tx("편의점")),
        )
    }

    @Test fun cancelWithoutOriginal_usesOwnMerchantRules() {
        assertEquals(
            Recognition(false, RecognitionReason.Excluded(ExclusionItem.TAX)),
            recognize(tx("국세", TxKind.CANCEL), taxCard, null),
        )
    }
}
