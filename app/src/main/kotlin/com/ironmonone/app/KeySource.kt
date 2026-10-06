package com.ironmonone.app

import android.view.InputDevice
import android.view.KeyEvent

/**
 * Which keys go through the keyboard mapping (KeyBindings.active) on their way to the game. A keyboard's keys do; and
 * the arrow keys do when they arrive from a D-pad source alone (SOURCE_DPAD): `adb shell input keyboard keyevent`, some
 * remotes and some keyboards send them that way, and they fell through to Compose's focus, so X worked and the arrows
 * moved nothing (2026-10-06). Only while Play routes keys to the game (KeyBindings.routeToGame), so text fields keep them.
 */
object KeySource {
    /** The D-pad's own key codes, the ones a SOURCE_DPAD event carries. */
    val DPAD_CODES = setOf(
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER,
    )

    private fun has(source: Int, kind: Int) = (source and kind) == kind

    /** Whether a key from [source] with [keyCode] is looked up in the keyboard mapping. */
    fun viaKeyboardMap(source: Int, keyCode: Int): Boolean =
        has(source, InputDevice.SOURCE_KEYBOARD) || (has(source, InputDevice.SOURCE_DPAD) && keyCode in DPAD_CODES)
}
