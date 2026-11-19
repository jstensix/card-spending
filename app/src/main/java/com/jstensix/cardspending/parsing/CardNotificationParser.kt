package com.jstensix.cardspending.parsing

import com.jstensix.cardspending.model.Card
import com.jstensix.cardspending.rules.isValidLast4
import com.jstensix.cardspending.rules.maskOf

/** Bump whenever a parser changes, so stored unrecognized notifications get parsed again. */
const val PARSER_VERSION = 2

sealed interface ParseOutcome {
    data class Parsed(val tx: ParsedTransaction) : ParseOutcome

    /** Looks like a card notification but no parser understood it: keep the original text for the user. */
    data object Candidate : ParseOutcome

    /** Not card related. Must never be stored (private chats arrive through the same listener). */
    data object Ignored : ParseOutcome
}

object CardNotificationParser {
    private val ISSUER_NAMES = listOf("롯데카드", "하나카드")

    /** Chat apps: a friend's message must never be mistaken for a card alert, whatever its words. */
    private val MESSENGER_PACKAGES = setOf(
        "com.kakao.talk",
        "jp.naver.line.android",
        "org.telegram.messenger",
        "com.whatsapp",
        "com.facebook.orca",
        "com.samsung.android.messaging",
        "com.google.android.apps.messaging",
    )
    /**
     * Recognized formats are parsed for any card. Anything else is kept for the user only when it names one of
     * [cards]: real payment alerts always carry the card number (masked like 7*1*), while ads, bill notices and
     * other apps merely mentioning an issuer do not.
     */
    fun parse(message: NotificationMessage, cards: List<Card>): ParseOutcome {
        LotteParser.parse(message)?.let { return ParseOutcome.Parsed(it) }
        HanaParser.parse(message)?.let { return ParseOutcome.Parsed(it) }
        return if (looksLikeCardNotification(message, cards)) ParseOutcome.Candidate else ParseOutcome.Ignored
    }

    private fun looksLikeCardNotification(message: NotificationMessage, cards: List<Card>): Boolean {
        val title = message.title.orEmpty()
        // In a chat only the sender/channel name is trustworthy (KakaoTalk 알림톡 shows "롯데카드", "하나카드").
        val fromMessenger = message.conversation || message.packageName in MESSENGER_PACKAGES
        if (fromMessenger && ISSUER_NAMES.none { it in title }) return false
        return namesRegisteredCard("$title\n${message.body}", cards)
    }

    /**
     * The masked number (7*1*) or the plain last 4 digits, not inside a longer digit run such as 174190.
     * Only digits bound the match: a comma-grouped amount like 17,419 never contains the 4-digit run, while
     * "(일시불,0381)" must still match. Cards with a malformed last4 (e.g. from a hand-edited backup) are skipped.
     */
    private fun namesRegisteredCard(text: String, cards: List<Card>): Boolean =
        cards.filter { isValidLast4(it.last4) }.any { card ->
            maskOf(card.last4) in text || Regex("""(?<!\d)${card.last4}(?!\d)""").containsMatchIn(text)
        }
}
