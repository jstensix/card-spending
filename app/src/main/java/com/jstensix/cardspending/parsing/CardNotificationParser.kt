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
    private val MASK = Regex("""\d\*\d\*""")
    private val AMOUNT = Regex("""\d[\d,]*(\.\d+)?\s*(원|USD|JPY|EUR|CNY|GBP|달러|엔|유로|위안)""")
    private val PAYMENT_WORDS = Regex("""승인|결제|취소""")

    fun parse(message: NotificationMessage, knownPackages: Set<String>): ParseOutcome {
        LotteParser.parse(message)?.let { return ParseOutcome.Parsed(it) }
        HanaParser.parse(message)?.let { return ParseOutcome.Parsed(it) }
        return if (looksLikeCardNotification(message, knownPackages)) ParseOutcome.Candidate else ParseOutcome.Ignored
    }

    private fun looksLikeCardNotification(message: NotificationMessage, knownPackages: Set<String>): Boolean {
        val title = message.title.orEmpty()
        // In a chat only the sender/channel name is trustworthy (KakaoTalk 알림톡 shows "롯데카드", "하나카드").
        if (message.conversation || message.packageName in MESSENGER_PACKAGES) return ISSUER_NAMES.any { it in title }

        val text = "$title\n${message.body}"
        if (ISSUER_NAMES.any { it in text }) return true
        // A masked card number marks a card alert even when the amount is in a foreign currency.
        if (MASK.containsMatchIn(text)) return true
        return message.packageName in knownPackages && AMOUNT.containsMatchIn(text) && PAYMENT_WORDS.containsMatchIn(text)
    }
}
