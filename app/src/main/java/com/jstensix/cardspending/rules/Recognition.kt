package com.jstensix.cardspending.rules

import com.jstensix.cardspending.model.Card
import com.jstensix.cardspending.model.ExclusionItem
import com.jstensix.cardspending.model.Transaction
import com.jstensix.cardspending.model.TxKind

sealed interface RecognitionReason {
    data object Rule : RecognitionReason
    data class Excluded(val item: ExclusionItem) : RecognitionReason
    data object Manual : RecognitionReason
}

data class Recognition(val recognized: Boolean, val reason: RecognitionReason)

/**
 * Manual choice wins. A cancel follows its original approval, so cancelling an excluded payment
 * lowers the payment total but not the recognized total.
 */
fun recognize(tx: Transaction, card: Card, original: Transaction?): Recognition {
    tx.manualRecognition?.let { return Recognition(it, RecognitionReason.Manual) }
    if (tx.kind == TxKind.CANCEL && original != null) return recognize(original, card, null)
    val excluded = ExclusionItem.entries.firstOrNull { it in card.exclusions && it.matches(tx.merchant, tx.installment) }
    return if (excluded != null) {
        Recognition(false, RecognitionReason.Excluded(excluded))
    } else {
        Recognition(true, RecognitionReason.Rule)
    }
}
