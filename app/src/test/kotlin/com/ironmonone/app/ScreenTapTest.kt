package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A tap on the game, read from the game view's own touches (ScreenTap), for the FILE bar (TapZoneTest holds which taps
 * count). The DS frame maths and the tap run for real; the wiring is held to the source.
 */
class ScreenTapTest {
    private val dir = File("src/main/kotlin/com/ironmonone/app")
    private fun read(name: String) = File(dir, name).readText().replace("\r\n", "\n")

    @Test
    fun `the DS touch screen sits where melonDS draws it`() {
        val stacked = ScreenTap.frame(ScreenTap.DsLayout("top-bottom", gap = 10))
        assertEquals(256f, stacked.width); assertEquals(394f, stacked.height)
        assertEquals(ScreenTap.Area(0f, 202f, 256f, 394f), stacked.touch, "below the top screen and the gap")
        assertEquals(ScreenTap.Area(0f, 0f, 256f, 192f), ScreenTap.frame(ScreenTap.DsLayout("bottom-top", gap = 10)).touch)
        val side = ScreenTap.frame(ScreenTap.DsLayout("left-right", gap = 10))
        assertEquals(512f, side.width, "side by side takes no gap")
        assertEquals(ScreenTap.Area(256f, 0f, 512f, 192f), side.touch)
        // Hybrid: 256r + 256 + 2r wide, 192r high; the small touch screen at x 256r + r/2, at the column's foot.
        val h2 = ScreenTap.frame(ScreenTap.DsLayout("hybrid-top", hybridRatio = 2))
        assertEquals(772f, h2.width); assertEquals(384f, h2.height)
        assertEquals(ScreenTap.Area(513f, 192f, 769f, 384f), h2.touch)
        val h3 = ScreenTap.frame(ScreenTap.DsLayout("hybrid-top", hybridRatio = 3))
        assertEquals(1030f, h3.width); assertEquals(576f, h3.height)
        assertEquals(ScreenTap.Area(769f, 384f, 1025f, 576f), h3.touch)
        assertEquals(ScreenTap.Area(0f, 0f, 512f, 384f), ScreenTap.frame(ScreenTap.DsLayout("hybrid-bottom")).touch, "the big screen is the touch screen")
        assertNull(ScreenTap.frame(ScreenTap.DsLayout("top")).touch, "top only: nothing to touch")
    }

    @Test
    fun `only a short still touch is a tap`() {
        val t = ScreenTap.Taps(slopPx = 20f)
        t.down(100f, 100f, at = 0); assertEquals(100f to 100f, t.up(at = 150))
        t.down(100f, 100f, at = 0); t.move(110f, 105f); assertEquals(100f to 100f, t.up(at = 150), "a wobble inside the slop")
        t.down(100f, 100f, at = 0); t.move(160f, 100f); assertNull(t.up(at = 150), "a swipe")
        t.down(100f, 100f, at = 0); assertNull(t.up(at = ScreenTap.TAP_MS + 1), "a hold")
        t.down(100f, 100f, at = 0); t.cancel(); assertNull(t.up(at = 100), "a second finger")
        assertNull(t.up(at = 100), "an up with no down")
    }

    @Test
    fun `the game view only watches, and a tap on it goes to the FILE bar's rules`() {
        val tap = read("ScreenTap.kt").substringAfter("fun listener(").substringBefore("\n    }\n")
        assertTrue("\n            false\n        }" in tap, "the listener answers false: the view still gets every touch")
        assertTrue("ui.onScreenTap(x, y, v.width.toFloat(), v.height.toFloat())" in tap)
        val play = read("PlayScreen.kt")
        assertTrue("view.setOnTouchListener(ScreenTap.listener(ui, ctx))" in play)
        val ui = read("SideScreens.kt").substringAfter("class PlayUiState {").substringBefore("\n}\n")
        assertTrue("fun onScreenTap(x: Float, y: Float, viewW: Float, viewH: Float) { FileBar.onScreenTap(x, y, viewW, viewH) }" in ui)
        // With nothing composed to say what is on screen, a tap does nothing.
        FileBar.reset()
        PlayUiState().onScreenTap(10f, 10f, 100f, 100f)
        assertFalse(FileBar.open)
    }
}
