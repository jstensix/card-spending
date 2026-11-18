package com.jstensix.cardspending.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test fun formatWon_positive() {
        assertEquals("523,400원", formatWon(523400))
    }

    @Test fun formatWon_negative() {
        assertEquals("-20,000원", formatWon(-20000))
    }

    @Test fun formatWon_zero() {
        assertEquals("0원", formatWon(0))
    }
}
