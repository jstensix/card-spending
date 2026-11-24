package com.jstensix.cardspending.parsing

import com.jstensix.cardspending.model.Issuer
import com.jstensix.cardspending.model.TxKind

/**
 * Hana Card approval SMS (the only layout seen so far, e.g. for 자동납부):
 *   [Web발신]
 *   하나0*8*승인 김*수님 SK브로드밴드-자동납 26,950원 11/23 정상처리
 * It carries the date but no time, so the clock time is when the SMS arrived. The date stays the one in the
 * text: a month-end payment notified the next day must count toward the month it was made.
 */
object HanaSmsParser {
    private val APPROVAL = Regex("""하나(\d\*\d\*)승인\s+\S+님\s+(.+?)\s+([\d,]+)원\s+(\d{1,2})/(\d{1,2})\s+정상처리""")

    fun parse(message: NotificationMessage): ParsedTransaction? {
        val text = normalizeLines(message.body).joinToString(" ")
        val match = APPROVAL.find(text) ?: return null
        val (mask, merchant, won, month, day) = match.destructured
        val amount = parseWon(won) ?: return null
        val at = message.postedAt
        val approvedAt = inferApprovedAt(month.toInt(), day.toInt(), at.hour, at.minute, at) ?: return null
        return ParsedTransaction(
            issuer = Issuer.HANA,
            kind = TxKind.APPROVAL,
            amount = amount,
            merchant = merchant.trim(),
            mask = mask,
            installment = false,
            approvedAt = approvedAt,
            cumulative = null,
            rawText = "${message.title}\n${message.body}",
        )
    }
}
