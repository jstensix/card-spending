package com.jstensix.cardspending.rules

import com.jstensix.cardspending.model.Card
import com.jstensix.cardspending.model.Transaction

private val LAST4 = Regex("""^\d{4}$""")

fun isValidLast4(value: String): Boolean = LAST4.matches(value)

/** Card notifications show only the 1st and 3rd of the last four digits, e.g. 7512 -> "7*1*". */
fun maskOf(last4: String): String {
    require(isValidLast4(last4)) { "last4 must be 4 digits" }
    return "${last4[0]}*${last4[2]}*"
}

fun Card.owns(tx: Transaction): Boolean = issuer == tx.issuer && maskOf(last4) == tx.mask

/** Another card of the same issuer whose notifications would look identical to [candidate]'s. */
fun findMaskConflict(cards: List<Card>, candidate: Card): Card? {
    if (!isValidLast4(candidate.last4)) return null
    val mask = maskOf(candidate.last4)
    return cards.firstOrNull { it.id != candidate.id && it.issuer == candidate.issuer && maskOf(it.last4) == mask }
}
