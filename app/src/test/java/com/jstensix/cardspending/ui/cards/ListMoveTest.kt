package com.jstensix.cardspending.ui.cards

import org.junit.Assert.assertEquals
import org.junit.Test

class ListMoveTest {
    private val abc = listOf("A", "B", "C")

    @Test fun move_down() {
        assertEquals(listOf("B", "C", "A"), abc.move(0, 2))
    }

    @Test fun move_up() {
        assertEquals(listOf("C", "A", "B"), abc.move(2, 0))
    }

    @Test fun move_samePlace_unchanged() {
        assertEquals(abc, abc.move(1, 1))
    }
}
