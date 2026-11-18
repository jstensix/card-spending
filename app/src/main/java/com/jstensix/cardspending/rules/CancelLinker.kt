package com.jstensix.cardspending.rules

import com.jstensix.cardspending.model.Transaction
import com.jstensix.cardspending.model.TxKind
import com.jstensix.cardspending.parsing.ParsedTransaction

/** The approval a cancel most likely undoes: same merchant preferred, then the latest one. Partial cancels allowed. */
fun findOriginal(cancel: ParsedTransaction, approvals: List<Transaction>): Transaction? {
    val eligible = approvals.filter {
        it.kind == TxKind.APPROVAL && !it.deleted && it.amount >= cancel.amount && !it.approvedAt.isAfter(cancel.approvedAt)
    }
    val sameMerchant = eligible.filter { it.merchant == cancel.merchant }
    return (sameMerchant.ifEmpty { eligible }).maxByOrNull { it.approvedAt }
}
