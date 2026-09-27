package com.ironmonone.app

import android.view.KeyEvent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The remapper never takes the phone's own keys (audit, 2026-09-27). */
class KeySystemKeysTest {
    @Test
    fun `back, home, volume and power are the phone's, game keys are not`() {
        for (k in listOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_VOLUME_UP,
                KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_APP_SWITCH))
            assertTrue(KeyBindings.isSystemKey(k), "key $k")
        for (k in listOf(KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_ENTER))
            assertFalse(KeyBindings.isSystemKey(k), "key $k")
    }
}
