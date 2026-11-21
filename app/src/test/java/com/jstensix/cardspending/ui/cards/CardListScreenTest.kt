package com.jstensix.cardspending.ui.cards

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import com.jstensix.cardspending.model.Card
import com.jstensix.cardspending.model.Issuer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Drives the real drag gestures: what the tab shows after a drop must be exactly what gets saved. */
@RunWith(RobolectricTestRunner::class)
class CardListScreenTest {
    @get:Rule val rule = createComposeRule()

    private val a = Card(id = 1, issuer = Issuer.LOTTE, last4 = "7512", nickname = "AAA")
    private val b = Card(id = 2, issuer = Issuer.LOTTE, last4 = "0381", nickname = "BBB")
    private val c = Card(id = 3, issuer = Issuer.LOTTE, last4 = "1234", nickname = "CCC")

    private var stored by mutableStateOf(listOf(a, b, c))
    private val saves = mutableListOf<List<Long>>()
    private val edits = mutableListOf<Long>()

    private fun show() {
        val byId = listOf(a, b, c).associateBy { it.id }
        rule.setContent {
            CardListScreen(
                cards = stored,
                onEdit = { edits += it },
                onAdd = {},
                // Stands in for Room: the saved order comes back as a fresh list with equal content.
                onReorder = { ids -> saves += ids; stored = ids.map { byId.getValue(it).copy() } },
            )
        }
        rule.waitForIdle()
    }

    private fun shownOrder(): List<String> =
        listOf("AAA", "BBB", "CCC").sortedBy { rule.onNodeWithText(it).fetchSemanticsNode().positionInRoot.y }

    /** Distance between two neighbouring cards. */
    private fun pitch(): Float =
        rule.onNodeWithText("BBB").fetchSemanticsNode().positionInRoot.y - rule.onNodeWithText("AAA").fetchSemanticsNode().positionInRoot.y

    private fun longPressDrag(text: String, dy: Float, steps: Int = 16) {
        val node = rule.onNodeWithText(text)
        node.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        repeat(steps) {
            node.performTouchInput { moveBy(Offset(0f, dy / steps)) }
            rule.mainClock.advanceTimeBy(64)
            rule.waitForIdle()
        }
        node.performTouchInput { up() }
        rule.mainClock.advanceTimeBy(2000)
        rule.waitForIdle()
    }

    @Test fun sameCardDraggedTwice_savesSecondMove() {
        show()
        val p = pitch()
        longPressDrag("AAA", p * 1.05f)
        longPressDrag("AAA", p * 1.05f)
        assertEquals(listOf("BBB", "CCC", "AAA"), shownOrder())
        assertEquals(listOf(listOf(2L, 1L, 3L), listOf(2L, 3L, 1L)), saves)
    }

    @Test fun differentCardsDragged_eachSaved() {
        show()
        val p = pitch()
        longPressDrag("AAA", p * 1.05f)
        longPressDrag("CCC", -p * 2.05f)
        assertEquals(listOf(listOf(2L, 1L, 3L), listOf(3L, 2L, 1L)), saves)
    }

    @Test fun longPressReleasedInPlace_neitherSavesNorOpensEdit() {
        show()
        longPressDrag("BBB", 0f, steps = 0)
        assertEquals(emptyList<List<Long>>(), saves)
        assertEquals(emptyList<Long>(), edits)
    }

    @Test fun tap_opensEdit() {
        show()
        rule.onNodeWithText("BBB").performTouchInput { click(center) }
        rule.waitForIdle()
        assertEquals(listOf(2L), edits)
    }
}
