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

class CardMaskConflictTest {
    private val existing = com.jstensix.cardspending.model.Card(1, com.jstensix.cardspending.model.Issuer.LOTTE, "7512", "텔로")

    @Test fun findMaskConflict_sameIssuerSameMask() {
        val candidate = com.jstensix.cardspending.model.Card(0, com.jstensix.cardspending.model.Issuer.LOTTE, "7913", "새 카드")
        assertEquals(existing, findMaskConflict(listOf(existing), candidate))
    }

    @Test fun findMaskConflict_otherIssuer_null() {
        val candidate = com.jstensix.cardspending.model.Card(0, com.jstensix.cardspending.model.Issuer.HANA, "7512", "하나")
        org.junit.Assert.assertNull(findMaskConflict(listOf(existing), candidate))
    }
}
