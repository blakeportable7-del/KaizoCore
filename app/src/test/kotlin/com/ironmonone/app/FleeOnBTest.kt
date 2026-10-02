package com.ironmonone.app

import android.view.KeyEvent
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-to-Run is armed by the menu B goes down on (2026-10-01, Blake: "it is making the game locked into the bag").
 * It fired on every B released in a wild battle: in the Bag its A picked an item again after every B, and after a B
 * out of the move menu it ran, the game being back on the action menu by the time B came up.
 */
class FleeOnBTest {
    private var runs = 0
    private var menu = true

    @BeforeTest fun setUp() { FleeOnB.released { }; FleeOnB.onFlee = { runs++ }; FleeOnB.menuUp = { menu } }
    @AfterTest fun tearDown() { FleeOnB.released { }; FleeOnB.onFlee = null; FleeOnB.menuUp = null }

    private fun b(action: Int) = FleeOnB.handle(action, KeyEvent.KEYCODE_BUTTON_B)

    @Test
    fun `B pressed on the action menu runs as it comes up`() {
        b(KeyEvent.ACTION_DOWN); b(KeyEvent.ACTION_UP)
        assertEquals(1, runs)
    }

    @Test
    fun `B pressed in the Bag or the move menu never runs, even back on the menu when it comes up`() {
        menu = false
        b(KeyEvent.ACTION_DOWN)
        menu = true                      // the game took the B and is on the action menu again
        b(KeyEvent.ACTION_DOWN)          // a held key repeats
        b(KeyEvent.ACTION_UP)
        assertEquals(0, runs)
        b(KeyEvent.ACTION_DOWN); b(KeyEvent.ACTION_UP)
        assertEquals(1, runs, "the next B on the action menu runs")
    }

    /** DS (2026-10-02): no tracker read of its battle menu, so B never runs there; the RUN button does. */
    @Test
    fun `a game whose battle menu nothing can see never runs on B`() {
        FleeOnB.menuUp = null
        b(KeyEvent.ACTION_DOWN); b(KeyEvent.ACTION_UP)
        assertEquals(0, runs)
    }

    @Test
    fun `no wild battle, no run`() {
        FleeOnB.onFlee = null
        var ran = false
        FleeOnB.pressed(); FleeOnB.released { ran = true }
        assertEquals(false, ran)
    }

    @Test
    fun `the on-screen pad and the controller go through the same arming`() {
        menu = false
        var pad = 0
        FleeOnB.pressed(); FleeOnB.released { pad++ }
        menu = true
        FleeOnB.pressed(); FleeOnB.released { pad++ }
        assertEquals(1, pad)
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertTrue("if (keyCode == KeyEvent.KEYCODE_BUTTON_B) FleeOnB.pressed()" in play)
        assertTrue("if (keyCode == KeyEvent.KEYCODE_BUTTON_B) FleeOnB.released(currentOnB)" in play)
        assertTrue("FleeOnB.menuUp = { actionMenuUp() ?: false }" in play)
        assertTrue("(gbRef as? com.ironmonone.tracker.ActionMenuGate)?.isChoosingActionInWild()" in play, "the Game Boy trackers' gate too")
        // A controller's B is read before the core gets it.
        val main = File("src/main/kotlin/com/ironmonone/app/MainActivity.kt").readText()
        for (key in listOf("event.keyCode", "mapped")) {
            val read = main.indexOf("FleeOnB.handle(event.action, $key)")
            val sent = main.indexOf("LibretroDroid.onKeyEvent(0, event.action, $key)")
            assertTrue(read in 0 until sent, "the menu is read before the press reaches the core ($key)")
        }
    }
}
