package com.jstensix.cardspending.ui.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PresentationTest {
    @Test fun recognizedRatio_normal() {
        assertEquals(481200f / 523400f, recognizedRatio(523400, 481200)!!, 0.0001f)
    }

    @Test fun recognizedRatio_clamped() {
        assertEquals(1f, recognizedRatio(1000, 1500))
        assertEquals(0f, recognizedRatio(1000, -500))
    }

    @Test fun recognizedPercent_floors() {
        assertEquals(93, recognizedPercent(643400, 601200))
    }

    @Test fun recognizedPercent_nonPositivePayment_null() {
        assertNull(recognizedPercent(0, 0))
        assertNull(recognizedPercent(-20000, -20000))
        assertNull(recognizedRatio(-1, -1))
    }

    @Test fun dayLabel_koreanWeekday() {
        assertEquals("11월 18일 (수)", dayLabel(LocalDate.of(2026, 11, 18)))
        assertEquals("11월 17일 (화)", dayLabel(LocalDate.of(2026, 11, 17)))
    }

    @Test fun groupByDay_keepsOrder() {
        val d18 = LocalDate.of(2026, 11, 18)
        val d17 = LocalDate.of(2026, 11, 17)
        val groups = groupByDay(listOf("a" to d18, "b" to d18, "c" to d17)) { it.second }
        assertEquals(listOf(d18, d17), groups.map { it.date })
        assertEquals(listOf("a", "b"), groups[0].items.map { it.first })
        assertEquals("11월 17일 (화)", groups[1].label)
    }
}
