package com.ironmonone.app

import android.view.InputDevice
import android.view.KeyEvent
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Arrow keys from a D-pad source alone (adb's `input keyboard keyevent`, some remotes and keyboards) reached Compose's
 * focus and never the game, while X from the same keyboard worked (2026-10-06). They take the keyboard mapping now.
 */
class KeySourceTest {
    @Test
    fun `a keyboard's keys and a D-pad source's arrows take the keyboard mapping, nothing else does`() {
        assertTrue(KeySource.viaKeyboardMap(InputDevice.SOURCE_KEYBOARD, KeyEvent.KEYCODE_X))
        assertTrue(KeySource.viaKeyboardMap(InputDevice.SOURCE_KEYBOARD, KeyEvent.KEYCODE_DPAD_UP))
        for (code in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT))
            assertTrue(KeySource.viaKeyboardMap(InputDevice.SOURCE_DPAD, code), "arrow $code from a D-pad source")
        assertTrue(KeySource.viaKeyboardMap(InputDevice.SOURCE_DPAD or InputDevice.SOURCE_KEYBOARD, KeyEvent.KEYCODE_DPAD_LEFT))
        assertFalse(KeySource.viaKeyboardMap(InputDevice.SOURCE_DPAD, KeyEvent.KEYCODE_X), "only the D-pad's own codes from a D-pad source")
        assertFalse(KeySource.viaKeyboardMap(InputDevice.SOURCE_TOUCHSCREEN, KeyEvent.KEYCODE_DPAD_UP))
        assertFalse(KeySource.viaKeyboardMap(InputDevice.SOURCE_MOUSE, KeyEvent.KEYCODE_DPAD_UP))
        assertFalse(KeySource.viaKeyboardMap(0, KeyEvent.KEYCODE_DPAD_UP), "no source, no game")
    }

    @Test
    fun `the activity asks it only after the game gate, so text fields keep their keys`() {
        val main = File("src/main/kotlin/com/ironmonone/app/MainActivity.kt").readText().replace("\r\n", "\n")
        val dispatch = main.substringAfter("override fun dispatchKeyEvent(").substringBefore("\n    }\n")
        val gate = dispatch.indexOf("if (!KeyBindings.routeToGame) return super.dispatchKeyEvent(event)")
        val ask = dispatch.indexOf("if (KeySource.viaKeyboardMap(event.source, event.keyCode)) {")
        assertTrue(gate in 0 until ask, "behind routeToGame")
        assertTrue("KeyBindings.active[event.keyCode]?.let" in dispatch.substring(ask))
    }
}
