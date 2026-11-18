package com.jstensix.cardspending.rules

import com.jstensix.cardspending.model.Issuer
import com.jstensix.cardspending.model.Transaction
import com.jstensix.cardspending.model.TxKind
import com.jstensix.cardspending.model.TxSource
import com.jstensix.cardspending.parsing.ParsedTransaction
import java.time.LocalDateTime

internal fun at(day: Int, hour: Int = 12, minute: Int = 0, month: Int = 11) = LocalDateTime.of(2026, month, day, hour, minute)

internal fun tx(
    id: Long = 0,
    amount: Long = 11000,
    merchant: String = "편의점",
    kind: TxKind = TxKind.APPROVAL,
    approvedAt: LocalDateTime = at(18),
    mask: String = "7*1*",
    issuer: Issuer = Issuer.LOTTE,
    cumulative: Long? = null,
    originalId: Long? = null,
    deleted: Boolean = false,
) = Transaction(
    id = id, issuer = issuer, kind = kind, amount = amount, merchant = merchant, mask = mask, installment = false,
    approvedAt = approvedAt, cumulative = cumulative, source = TxSource.NOTIFICATION, originalId = originalId,
    deleted = deleted, createdAt = approvedAt,
)

internal fun parsed(
    amount: Long = 11000,
    merchant: String = "편의점",
    kind: TxKind = TxKind.APPROVAL,
    approvedAt: LocalDateTime = at(18),
    mask: String = "7*1*",
    cumulative: Long? = null,
) = ParsedTransaction(Issuer.LOTTE, kind, amount, merchant, mask, false, approvedAt, cumulative, "raw")
