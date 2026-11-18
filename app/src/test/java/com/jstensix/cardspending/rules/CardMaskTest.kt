package com.jstensix.cardspending.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardMaskTest {
    @Test fun maskOf_keepsFirstAndThirdDigits() {
        assertEquals("7*1*", maskOf("7512"))
    }

    @Test fun isValidLast4_requiresFourDigits() {
        assertTrue(isValidLast4("0381"))
        assertFalse(isValidLast4("75a2"))
        assertFalse(isValidLast4("123"))
    }
}
