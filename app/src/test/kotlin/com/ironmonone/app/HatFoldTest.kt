package com.ironmonone.app

import android.view.KeyEvent
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A controller's stick and d-pad hat (rc32 audit P2 #31). They were folded into the game's d-pad on every screen and
 * the motion marked handled, so Android never turned a hat into the d-pad keys that move through the menus: on Home,
 * Library and More an Xbox or PlayStation pad's d-pad did nothing, and with Play's text dialogs or the layout editor
 * open the stick still moved the game. Motion now follows the same gate as keys, and a direction held into it is let go.
 */
class HatFoldTest {
    private fun down(k: Int) = HatFold.Press(KeyEvent.ACTION_DOWN, k)
    private fun up(k: Int) = HatFold.Press(KeyEvent.ACTION_UP, k)

    @Test
    fun `an axis crossing is one press, and a turn lets the old direction go first`() {
        val h = HatFold()
        assertEquals(listOf(down(KeyEvent.KEYCODE_DPAD_RIGHT)), h.move(1, 0))
        assertEquals(emptyList(), h.move(1, 0), "held: nothing more")
        assertEquals(listOf(up(KeyEvent.KEYCODE_DPAD_RIGHT), down(KeyEvent.KEYCODE_DPAD_LEFT)), h.move(-1, 0))
        assertEquals(listOf(down(KeyEvent.KEYCODE_DPAD_UP)), h.move(-1, -1), "a diagonal is both")
        assertEquals(listOf(up(KeyEvent.KEYCODE_DPAD_LEFT), up(KeyEvent.KEYCODE_DPAD_UP), down(KeyEvent.KEYCODE_DPAD_DOWN)), h.move(0, 1))
        assertEquals(0, h.x); assertEquals(1, h.y)
    }

    @Test
    fun `when the pad stops being the game's, what it held is let go, once`() {
        val h = HatFold()
        h.move(1, 1)
        assertEquals(listOf(up(KeyEvent.KEYCODE_DPAD_RIGHT), up(KeyEvent.KEYCODE_DPAD_DOWN)), h.releaseAll())
        assertEquals(emptyList(), h.releaseAll(), "nothing held, nothing sent")
        assertEquals(listOf(down(KeyEvent.KEYCODE_DPAD_LEFT)), h.move(-1, 0), "and the next move starts from nothing held")
    }

    @Test
    fun `motion reaches the game only while the Play screen routes keys to it, as keys do`() {
        val main = File("src/main/kotlin/com/ironmonone/app/MainActivity.kt").readText().replace("\r\n", "\n")
        val motion = main.substringAfter("override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {").substringBefore("\n    }\n")
        val gate = motion.indexOf("if (!KeyBindings.routeToGame) {")
        assertTrue(gate > 0, "the same gate as dispatchKeyEvent's")
        val gated = motion.substring(gate, motion.indexOf("}", gate))
        assertTrue("press(hat.releaseAll())" in gated && "return super.dispatchGenericMotionEvent(event)" in gated,
            "a held direction is let go, and Android gets the motion to move focus with")
        assertTrue(gate < motion.indexOf("press(hat.move("), "before any press reaches the core")
    }
}
