package com.ironmonone.pc

import com.ironmonone.pcui.PcPanel
import com.ironmonone.pcui.PcScreens
import com.ironmonone.pcui.Samples
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Out of an info card with one click (Blake, 2026-10-10: "click the move, see the info card, click again and im back";
 * "click on nothing clickable exits"): a click on something the card uses still does its job, a click on nothing closes
 * every card back to the Pokemon's.
 */
class PanelOutClickTest {
    @Test
    fun `a click on nothing closes the info cards, a click on the back arrow still goes back one`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "kc-panel-click").apply { deleteRecursively(); mkdirs() }
        val p = PanelOut(dir) {}
        val input = PcPanel.Input(Samples.party, 1)
        p.show(input, "", force = true)
        p.screens.push(PcScreens.Screen.PokemonInfo(Samples.party.party[0].mon.species))
        p.screens.push(PcScreens.Screen.MoveInfo(33))
        p.show(input, "", force = true)
        // The back arrow (BACK_ARROW at 129, 142; its hit box 127, 142, 16 by 12) is clickable: one card back, not all.
        p.clickAt(135f, 147f)
        assertEquals(1, p.screens.stack.size, "the back arrow went back one card")
        p.show(input, "", force = true)
        // The card's top-left corner is nothing clickable: back to the Pokemon's card.
        p.clickAt(1f, 1f)
        assertNull(p.screens.top, "a click on nothing closed the info cards")
    }

    @Test
    fun `the hand cursor is over the back arrow and not over nothing, fresh on every look`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "kc-panel-hover").apply { deleteRecursively(); mkdirs() }
        val p = PanelOut(dir) {}
        val input = PcPanel.Input(Samples.party, 1)
        p.screens.push(PcScreens.Screen.MoveInfo(33))
        p.show(input, "", force = true)
        kotlin.test.assertTrue(p.hoverAt(135f, 147f), "over the back arrow")
        kotlin.test.assertFalse(p.hoverAt(1f, 1f), "over nothing")
        kotlin.test.assertTrue(p.hoverAt(135f, 147f), "over the back arrow again")
    }
}
