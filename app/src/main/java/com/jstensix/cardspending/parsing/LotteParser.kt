package com.jstensix.cardspending.parsing

import com.jstensix.cardspending.model.Issuer
import com.jstensix.cardspending.model.TxKind

/**
 * Lotte Card KakaoTalk alert:
 *   가맹점명 / {금액}원 승인 / {이름} {상품명}(7*1*) / 일시불, MM/DD HH:mm / 누적금액 {금액}원
 * The cancel wording has not been seen yet; any "취소" on the amount line is treated as a cancel.
 */
object LotteParser {
    private val AMOUNT_LINE = Regex("""^([\d,]+)원\s*(.*)$""")
    private val MASK = Regex("""\((\d\*\d\*)\)\s*$""")
    private val DATE_LINE = Regex("""^(.+?),\s*(\d{1,2})/(\d{1,2})\s+(\d{1,2}):(\d{2})$""")
    private val CUMULATIVE = Regex("""누적금액\s*([\d,]+)원""")

    fun parse(message: NotificationMessage): ParsedTransaction? {
        val lines = normalizeLines(message.body)
        val amountIndex = lines.indexOfFirst { line ->
            AMOUNT_LINE.find(line)?.groupValues?.get(2)?.let { "승인" in it || "취소" in it } == true
        }
        if (amountIndex < 1 || amountIndex + 2 >= lines.size) return null

        val amountMatch = AMOUNT_LINE.find(lines[amountIndex]) ?: return null
        val amount = parseWon(amountMatch.groupValues[1]) ?: return null
        val kind = if ("취소" in amountMatch.groupValues[2]) TxKind.CANCEL else TxKind.APPROVAL
        val mask = MASK.find(lines[amountIndex + 1])?.groupValues?.get(1) ?: return null
        val date = DATE_LINE.find(lines[amountIndex + 2]) ?: return null
        val (payType, month, day, hour, minute) = date.destructured
        val approvedAt = inferApprovedAt(month.toInt(), day.toInt(), hour.toInt(), minute.toInt(), message.postedAt)
            ?: return null
        val cumulative = lines.firstNotNullOfOrNull { CUMULATIVE.find(it) }?.groupValues?.get(1)?.let(::parseWon)

        return ParsedTransaction(
            issuer = Issuer.LOTTE,
            kind = kind,
            amount = amount,
            merchant = lines[amountIndex - 1],
            mask = mask,
            installment = payType.trim() != "일시불",
            approvedAt = approvedAt,
            cumulative = cumulative,
            rawText = message.body,
        )
    }
}
