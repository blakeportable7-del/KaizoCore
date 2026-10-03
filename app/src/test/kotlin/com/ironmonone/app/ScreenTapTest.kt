package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The menu a tap brings up while the tracker is off screen (Blake, 2026-10-02: "a screen tap displays another menu
 * button on the top left, tapping away from the button and on the screen makes it disappear"). The DS frame maths,
 * the tap and the toggle run for real; the wiring is held to the source.
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
    fun `a tap on the touch screen is play, a tap anywhere else is for the menu`() {
        // Landscape 2340 x 1080 with Hybrid Top: the frame is fitted by height, so it sits 84 px in from each side.
        val hybrid = ScreenTap.DsLayout("hybrid-top")
        assertTrue(ScreenTap.onTouchScreen(hybrid, 1900f, 800f, 2340f, 1080f), "the small bottom screen")
        assertFalse(ScreenTap.onTouchScreen(hybrid, 600f, 400f, 2340f, 1080f), "the big top screen")
        assertFalse(ScreenTap.onTouchScreen(hybrid, 1900f, 300f, 2340f, 1080f), "the empty space above the small screen")
        assertFalse(ScreenTap.onTouchScreen(hybrid, 40f, 800f, 2340f, 1080f), "the black bar")
        // Portrait 1080 x 1600, stacked: the lower half.
        val stacked = ScreenTap.DsLayout("top-bottom")
        assertTrue(ScreenTap.onTouchScreen(stacked, 540f, 1200f, 1080f, 1600f))
        assertFalse(ScreenTap.onTouchScreen(stacked, 540f, 400f, 1080f, 1600f))
        assertFalse(ScreenTap.onTouchScreen(ScreenTap.DsLayout("top"), 540f, 1200f, 1080f, 1600f))
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
    fun `a tap shows the menu and the next hides it, while it stands in for the tracker`() {
        val ui = PlayUiState()
        ui.onScreenTap(10f, 10f, 100f, 100f)
        assertFalse(ui.tapMenuShown, "with the tracker on screen a tap does nothing")
        ui.tapMenuEnabled = true
        ui.onScreenTap(10f, 10f, 100f, 100f); assertTrue(ui.tapMenuShown)
        ui.onScreenTap(50f, 50f, 100f, 100f); assertFalse(ui.tapMenuShown, "tapping away hides it")
        ui.tapDsLayout = ScreenTap.DsLayout("top-bottom")
        ui.onScreenTap(50f, 90f, 100f, 100f); assertFalse(ui.tapMenuShown, "a tap on the DS touch screen is play")
        ui.onScreenTap(50f, 10f, 100f, 100f); assertTrue(ui.tapMenuShown, "on the top screen it is for the menu")
    }

    @Test
    fun `the button steps below a pad control in the corner`() {
        assertEquals(6f, ScreenTap.menuTop(emptyList(), 6f, 44f), "the corner, with nothing there")
        val l = PadGeometry.Box(20f, 8f, 60f, 48f)
        assertEquals(54f, ScreenTap.menuTop(listOf(l), 6f, 44f), "below L")
        val next = PadGeometry.Box(0f, 60f, 100f, 120f)
        assertEquals(126f, ScreenTap.menuTop(listOf(l, next), 6f, 44f), "and past the next one under it")
        // The DS landscape preset, where L covered the button on the emulator (851 x 393 dp).
        for (skin in PadSkin.entries) {
            val boxes = PadGeometry.rects(PadLayout.default(landscape = true, nds = true), 851f, 393f, landscape = true, skin = skin).values.flatten()
            val top = ScreenTap.menuTop(boxes, 6f, 44f)
            assertTrue(boxes.none { it.meets(PadGeometry.Box(6f, top, 50f, top + 44f)) }, "$skin: clear of every control")
            assertTrue(top + 44f <= 393f, "$skin: still on the screen")
        }
    }

    @Test
    fun `the game view only watches, and the button sits last over the game`() {
        val tap = read("ScreenTap.kt").substringAfter("fun listener(").substringBefore("\n    }\n")
        assertTrue("\n            false\n        }" in tap, "the listener answers false: the view still gets every touch")
        val play = read("PlayScreen.kt")
        assertTrue("view.setOnTouchListener(ScreenTap.listener(ui, ctx))" in play)
        val landscape = play.substringAfter("OverlayChip(\"MENU\") { onExitFullscreen() }").substringBefore("if (!landscape) {")
        assertTrue("ScreenTapMenu(" in landscape, "drawn after the band and the faded strip's guard, so it is on top")
        assertTrue("allowed = session.tracked && !menuOpen && !streamClean && !editingLayout" in landscape)
        val chrome = read("LandscapeChrome.kt")
        val menu = chrome.substringAfter("internal fun ScreenTapMenu(").substringBefore("\n}\n")
        assertTrue("TrackerOptions.landscapeTracker != LandscapeTracker.FLOATING" in menu && "trackerOnSecond ||" in menu,
            "off screen: hidden beside the game, or on the other display")
        assertTrue("if (!enabled && ui.tapMenuShown) ui.tapMenuShown = false" in menu, "it goes when the tracker comes back")
        assertTrue("\"Show the tracker\"" in chrome)
        assertTrue("val run = ironmonRunInPlay(attempt)" in chrome && "if (run) Text(" in chrome,
            "the attempt is a Kaizo IronMON run's: a library game showed the last run's count")
        // A pick in a dropdown never reaches the idle clock (its own window), so File brought the band up faded.
        assertEquals(3, Regex(Regex.escape("menuOpen = !menuOpen; lastTouch = android.os.SystemClock.uptimeMillis()") + "|" +
            Regex.escape("onFile = { menuOpen = true; lastTouch = android.os.SystemClock.uptimeMillis() }")).findAll(play).count(),
            "the docked menu, the floating window's and this one all wake the band")
    }
}
