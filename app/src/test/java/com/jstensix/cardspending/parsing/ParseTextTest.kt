package com.jstensix.cardspending.parsing

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParseTextTest {
    @Test fun parseWon_handlesCommas() {
        assertEquals(11000L, parseWon("11,000"))
        assertEquals(3220782L, parseWon("3,220,782"))
        assertEquals(900L, parseWon("900"))
        assertNull(parseWon("abc"))
    }

    @Test fun normalizeLines_trimsAndDropsBlanks() {
        assertEquals(listOf("a", "b", "c"), normalizeLines("a\r\n b \n\n c"))
    }

    @Test fun sameMonth_usesPostedYear() {
        assertEquals(
            LocalDateTime.of(2026, 11, 18, 11, 43),
            inferApprovedAt(11, 18, 11, 43, LocalDateTime.of(2026, 11, 18, 11, 43, 5)),
        )
    }

    @Test fun monthEndNotifiedNextMonth_staysInMonth() {
        assertEquals(
            LocalDateTime.of(2026, 11, 30, 23, 59),
            inferApprovedAt(11, 30, 23, 59, LocalDateTime.of(2026, 12, 1, 0, 0, 30)),
        )
    }

    @Test fun decemberNotifiedInJanuary_usesPreviousYear() {
        assertEquals(
            LocalDateTime.of(2026, 12, 31, 23, 59),
            inferApprovedAt(12, 31, 23, 59, LocalDateTime.of(2027, 1, 1, 0, 0, 10)),
        )
    }

    @Test fun invalidDate_returnsNull() {
        assertNull(inferApprovedAt(2, 30, 10, 0, LocalDateTime.of(2026, 3, 1, 9, 0)))
    }
}
