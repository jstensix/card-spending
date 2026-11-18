package com.jstensix.cardspending.parsing

import java.time.DateTimeException
import java.time.LocalDateTime

private val WON_DIGITS = Regex("""^\d{1,3}(,\d{3})*$|^\d+$""")

/** "3,220,782" -> 3220782. Returns null for anything that is not a plain won amount. */
fun parseWon(text: String): Long? {
    val trimmed = text.trim()
    if (!WON_DIGITS.matches(trimmed)) return null
    return trimmed.replace(",", "").toLongOrNull()
}

/** Splits notification text into trimmed, non-blank lines. Card apps mix CRLF, NBSP and zero-width spaces. */
fun normalizeLines(text: String): List<String> =
    text.replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace(' ', ' ')
        .replace("​", " ")
        .split('\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

/**
 * Notifications carry month/day but no year. Use the year the notification arrived, except when the
 * approval month is later than the arrival month (a 12/31 approval notified on 1/1 belongs to last year).
 */
fun inferApprovedAt(month: Int, day: Int, hour: Int, minute: Int, postedAt: LocalDateTime): LocalDateTime? {
    val year = if (month > postedAt.monthValue) postedAt.year - 1 else postedAt.year
    return try {
        LocalDateTime.of(year, month, day, hour, minute)
    } catch (e: DateTimeException) {
        null
    }
}
