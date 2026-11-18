package com.jstensix.cardspending.parsing

import com.jstensix.cardspending.model.Issuer
import com.jstensix.cardspending.model.TxKind
import java.time.LocalDateTime

data class ParsedTransaction(
    val issuer: Issuer,
    val kind: TxKind,
    val amount: Long,
    val merchant: String,
    val mask: String,
    val installment: Boolean,
    val approvedAt: LocalDateTime,
    val cumulative: Long?,
    val rawText: String,
)
