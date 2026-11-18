package com.jstensix.cardspending.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExitOnSecondBackTest {
    private var now = 10_000L
    private val gate = ExitOnSecondBack(windowMillis = 2_000, now = { now })

    @Test
    fun firstPress_onlyWarns() {
        assertFalse(gate.onBack())
    }

    @Test
    fun secondPressWithinWindow_exits() {
        gate.onBack()
        now += 1_999
        assertTrue(gate.onBack())
    }

    @Test
    fun secondPressAfterWindow_warnsAgain_thenQuickPressExits() {
        gate.onBack()
        now += 2_001
        assertFalse(gate.onBack())
        now += 500
        assertTrue(gate.onBack())
    }

    @Test
    fun afterExit_nextPressStartsOver() {
        gate.onBack()
        gate.onBack()
        now += 100
        assertFalse(gate.onBack())
    }
}
