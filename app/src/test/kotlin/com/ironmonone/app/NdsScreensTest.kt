package com.ironmonone.app

import kotlin.test.Test
import kotlin.test.assertEquals

class NdsScreensTest {
    @Test
    fun `a phone column goes side by side, a tablet column stacks, no size is side by side`() {
        assertEquals("left-right", NdsScreens.autoLayout(550, 360))
        assertEquals("left-right", NdsScreens.autoLayout(640, 400))
        // Blake's tablet, 2026-09-08: a 1480x1509 px column beside the tracker.
        assertEquals("top-bottom", NdsScreens.autoLayout(1480, 1509))
        assertEquals("top-bottom", NdsScreens.autoLayout(800, 900))
        assertEquals("left-right", NdsScreens.autoLayout(0, 0))
    }
}
