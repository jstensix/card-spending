package com.jstensix.cardspending.parsing

/** Bump whenever a parser changes, so stored unrecognized notifications get parsed again. */
const val PARSER_VERSION = 1

sealed interface ParseOutcome {
    data class Parsed(val tx: ParsedTransaction) : ParseOutcome

    /** Looks like a card notification but no parser understood it: keep the original text for the user. */
    data object Candidate : ParseOutcome

    /** Not card related. Must never be stored (private chats arrive through the same listener). */
    data object Ignored : ParseOutcome
}

object CardNotificationParser {
    private val ISSUER_NAMES = listOf("롯데카드", "하나카드")
    private val MASK = Regex("""\d\*\d\*""")
    private val WON = Regex("""[\d,]+원""")
    private val PAYMENT_WORDS = Regex("""승인|결제|취소""")

    fun parse(message: NotificationMessage, knownPackages: Set<String>): ParseOutcome {
        LotteParser.parse(message)?.let { return ParseOutcome.Parsed(it) }
        HanaParser.parse(message)?.let { return ParseOutcome.Parsed(it) }
        return if (looksLikeCardNotification(message, knownPackages)) ParseOutcome.Candidate else ParseOutcome.Ignored
    }

    private fun looksLikeCardNotification(message: NotificationMessage, knownPackages: Set<String>): Boolean {
        val text = "${message.title.orEmpty()}\n${message.body}"
        if (ISSUER_NAMES.any { it in text }) return true
        if (MASK.containsMatchIn(message.body) && WON.containsMatchIn(message.body)) return true
        return message.packageName in knownPackages && WON.containsMatchIn(text) && PAYMENT_WORDS.containsMatchIn(text)
    }
}
