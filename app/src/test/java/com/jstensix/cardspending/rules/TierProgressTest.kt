package com.jstensix.cardspending.rules

import com.jstensix.cardspending.model.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TierProgressTest {
    private val tiers = listOf(Tier(300_000, "통신"), Tier(500_000, "커피"), Tier(1_000_000, "주유"))

    @Test fun noneAchieved() {
        val p = tierProgress(120_000, tiers)!!
        assertEquals(0, p.achieved)
        assertEquals(0.12f, p.fill, 0.0001f)
        assertEquals(listOf(0.3f, 0.5f, 1f), p.marks)
        assertEquals(300_000L, p.next!!.amount)
        assertEquals(180_000L, p.remaining)
    }

    @Test fun partlyAchieved() {
        val p = tierProgress(620_000, tiers)!!
        assertEquals(2, p.achieved)
        assertEquals(0.62f, p.fill, 0.0001f)
        assertEquals(1_000_000L, p.next!!.amount)
        assertEquals(380_000L, p.remaining)
    }

    @Test fun exactThreshold_isAchieved() {
        assertEquals(2, tierProgress(500_000, tiers)!!.achieved)
    }

    @Test fun allAchieved_fullAndNoNext() {
        val p = tierProgress(1_200_000, tiers)!!
        assertEquals(3, p.achieved)
        assertEquals(1f, p.fill)
        assertNull(p.next)
        assertNull(p.remaining)
    }

    // A month with more cancels than payments.
    @Test fun negativeRecognized_countsAsZero() {
        val p = tierProgress(-5_000, tiers)!!
        assertEquals(0, p.achieved)
        assertEquals(0f, p.fill)
        assertEquals(300_000L, p.remaining)
    }

    @Test fun unsortedTiers_areSorted() {
        val p = tierProgress(0, tiers.reversed())!!
        assertEquals(listOf(0.3f, 0.5f, 1f), p.marks)
        assertEquals(tiers, p.tiers)
    }

    @Test fun noTiers_null() {
        assertNull(tierProgress(100, emptyList()))
    }
}
