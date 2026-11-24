package com.jstensix.cardspending.rules

import com.jstensix.cardspending.model.Transaction
import com.jstensix.cardspending.parsing.ParsedTransaction

/**
 * The same payment can arrive twice (Hana app push and KakaoTalk, or a KakaoTalk re-post).
 * The cumulative total tells two real same-minute payments apart, because it grows with each one. Alerts without
 * one (Hana SMS) fall back to the merchant: a batch of same-amount auto-pay SMS can share one minute.
 */
fun isSameTransaction(existing: Transaction, incoming: ParsedTransaction): Boolean =
    existing.issuer == incoming.issuer &&
        existing.mask == incoming.mask &&
        existing.amount == incoming.amount &&
        existing.kind == incoming.kind &&
        existing.approvedAt == incoming.approvedAt &&
        (existing.cumulative == null || incoming.cumulative == null || existing.cumulative == incoming.cumulative) &&
        (existing.cumulative != null || incoming.cumulative != null || existing.merchant == incoming.merchant)
