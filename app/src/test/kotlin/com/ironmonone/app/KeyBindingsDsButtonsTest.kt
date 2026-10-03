package com.ironmonone.app

import android.view.KeyEvent
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * rc32 audit P2 #24: a keyboard counts as a controller, so it hides the touch pad, and the touch pad was the only way
 * to press the DS's X and Y. X opens the menu in every Gen 4 and 5 game, so a keyboard player could not save. X and
 * Y are buttons a key can drive now, with defaults of their own, and a key a player already bound stays theirs.
 */
class KeyBindingsDsButtonsTest {
    private fun file() = File(Files.createTempDirectory("keys").toFile(), KeyBindings.FILE).apply { parentFile.mkdirs() }

    @Test
    fun `X and Y are on the list, after R, and drive the core's X and Y`() {
        val names = KeyBindings.Button.entries.map { it.name }
        assertTrue("X" in names && "Y" in names)
        assertEquals(names.indexOf("R") + 1, names.indexOf("X"))
        assertEquals(KeyEvent.KEYCODE_BUTTON_X, KeyBindings.Button.X.coreKey)
        assertEquals(KeyEvent.KEYCODE_BUTTON_Y, KeyBindings.Button.Y.coreKey)
        assertTrue("DS" in KeyBindings.Button.X.label && "DS" in KeyBindings.Button.Y.label, "the list says they are for DS games")
    }

    @Test
    fun `a fresh install reaches X and Y from the keyboard, on keys no other default uses`() {
        KeyBindings(file())
        assertEquals(KeyEvent.KEYCODE_BUTTON_X, KeyBindings.active[KeyEvent.KEYCODE_D])
        assertEquals(KeyEvent.KEYCODE_BUTTON_Y, KeyBindings.active[KeyEvent.KEYCODE_C])
        val defaults = KeyBindings.DEFAULTS.values
        assertEquals(defaults.size, defaults.toSet().size, "no key drives two buttons")
        assertEquals(KeyBindings.Button.entries.toSet(), KeyBindings.DEFAULTS.keys, "every button has a default")
    }

    @Test
    fun `a file saved before X and Y keeps every key the player bound`() {
        val f = file()
        // A player who gave D to A and C to Quick save, in a file written before X and Y existed.
        f.writeText("A=${KeyEvent.KEYCODE_D}\nACTION_QUICK_SAVE=${KeyEvent.KEYCODE_C}\n")
        val kb = KeyBindings(f)
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, KeyBindings.active[KeyEvent.KEYCODE_D], "D is still A")
        assertEquals(0, kb.keyFor(KeyBindings.Button.X), "X gave way to the player's own D")
        assertEquals(0, kb.keyFor(KeyBindings.Button.Y), "Y gave way to the player's Quick save on C")
        assertEquals(KeyBindings.Action.QUICK_SAVE, KeyBindings.activeActions[KeyEvent.KEYCODE_C])
        // Defaults nobody took are kept: B is still Z.
        assertEquals(KeyEvent.KEYCODE_Z, kb.keyFor(KeyBindings.Button.B))
    }

    @Test
    fun `a file that names X and Y is read as written`() {
        val f = file()
        f.writeText("X=${KeyEvent.KEYCODE_Q}\nY=${KeyEvent.KEYCODE_W}\n")
        val kb = KeyBindings(f)
        assertEquals(KeyEvent.KEYCODE_Q, kb.keyFor(KeyBindings.Button.X))
        assertEquals(KeyEvent.KEYCODE_BUTTON_Y, KeyBindings.active[KeyEvent.KEYCODE_W])
        // And a bind writes them, so they come back after a restart.
        kb.bind(KeyBindings.Button.Y, KeyEvent.KEYCODE_E)
        assertEquals(KeyEvent.KEYCODE_E, KeyBindings(f).keyFor(KeyBindings.Button.Y))
    }
}
