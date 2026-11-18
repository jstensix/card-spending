package com.jstensix.cardspending.parsing

import com.jstensix.cardspending.model.Issuer
import com.jstensix.cardspending.model.TxKind

/**
 * Hana Card app push:
 *   title: (결제) 9,900원
 *   body:  가맹점 / 신용(일시불,0*8*) / MM.DD HH:mm / 누적이용금액 {금액}원
 * The real body separator (newline or " / ") is unconfirmed, so both are accepted.
 */
object HanaParser {
    private val TITLE = Regex("""^\((.+?)\)\s*([\d,]+)원$""")
    private val CARD_PART = Regex("""^(신용|체크)\((.+?),(\d\*\d\*)\)$""")
    private val DATE_PART = Regex("""^(\d{1,2})\.(\d{1,2})\s+(\d{1,2}):(\d{2})$""")
    private val CUMULATIVE = Regex("""누적이용금액\s*([\d,]+)원""")

    fun parse(message: NotificationMessage): ParsedTransaction? {
        val title = message.title?.let { normalizeLines(it).joinToString(" ") } ?: return null
        val titleMatch = TITLE.find(title) ?: return null
        val label = titleMatch.groupValues[1]
        val kind = when {
            "취소" in label -> TxKind.CANCEL
            "결제" in label || "승인" in label -> TxKind.APPROVAL
            else -> return null
        }
        val amount = parseWon(titleMatch.groupValues[2]) ?: return null

        val parts = normalizeLines(message.body)
            .flatMap { it.split(" / ") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (parts.size < 3) return null

        val card = CARD_PART.find(parts[1]) ?: return null
        val date = DATE_PART.find(parts[2]) ?: return null
        val (month, day, hour, minute) = date.destructured
        val approvedAt = inferApprovedAt(month.toInt(), day.toInt(), hour.toInt(), minute.toInt(), message.postedAt)
            ?: return null
        val cumulative = parts.firstNotNullOfOrNull { CUMULATIVE.find(it) }?.groupValues?.get(1)?.let(::parseWon)

        return ParsedTransaction(
            issuer = Issuer.HANA,
            kind = kind,
            amount = amount,
            merchant = parts[0],
            mask = card.groupValues[3],
            installment = card.groupValues[2].trim() != "일시불",
            approvedAt = approvedAt,
            cumulative = cumulative,
            rawText = "${message.title}\n${message.body}",
        )
    }
}
