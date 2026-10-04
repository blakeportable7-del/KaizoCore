package com.ironmonone.app

import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Landscape's chrome after Blake's notes of 2026-10-02: one menu of three lines in place of the attempt row, the File
 * band centred with arrows and an X, a floating window that moves to the edge, resizes from its sides and bottom,
 * locks and fits its content, one card with SEE FOE where the tracker is short, and the DS note drawn once. The
 * frame maths runs for real; what is drawn is held to the source.
 */
class LandscapeChromeTest {
    private val dir = File("src/main/kotlin/com/ironmonone/app")
    private fun read(name: String) = File(dir, name).readText().replace("\r\n", "\n")

    @Test
    fun `both cards where there is room for them, one with SEE FOE where the tracker is short`() {
        assertTrue(TrackerRoom.stackBoth(null), "unmeasured keeps the old behaviour")
        assertTrue(TrackerRoom.stackBoth(393.dp), "Black 2's docked column held both")
        assertFalse(TrackerRoom.stackBoth(200.dp), "the space above the DS bottom screen does not")
        val play = read("PlayScreen.kt")
        assertEquals(2, Regex(Regex.escape("stackBoth = TrackerRoom.stackBoth(LocalTrackerRoom.current),")).findAll(play).count(),
            "the GBA and the DS panel in the landscape column both ask")
        assertFalse("stackBoth = true," in play)
    }

    @Test
    fun `the window moves to every edge of the box it is drawn in, and a left edge drag keeps its right edge`() {
        val f = FloatFrame(0f, 0f, 300f, 200f)
        assertEquals(851f - 300f, f.copy(x = 5000f).clamped(851f, 393f).x, "flush right, not short of it")
        assertEquals(393f - 200f, f.copy(y = 5000f).clamped(851f, 393f).y, "flush with the bottom")
        val g = FloatFrame(400f, 50f, 300f, 200f)
        val wider = g.leftEdge(-100f)
        assertEquals(300f, wider.x); assertEquals(400f, wider.w); assertEquals(g.x + g.w, wider.x + wider.w)
        val tight = g.leftEdge(10_000f)
        assertEquals(FloatFrame.MIN_W, tight.w, "never narrower than the least"); assertEquals(g.x + g.w, tight.x + tight.w)
        assertTrue(FloatFrame.MIN_W <= 160f && FloatFrame.MIN_H <= 120f, "any size, down to a title bar and a few rows")
        val src = read("FloatingTracker.kt")
        assertTrue("BoxWithConstraints(Modifier.fillMaxSize())" in src && "val areaW = maxWidth.value" in src,
            "it measures the box it moves in; LocalConfiguration's screen size leaves out the system bars")
    }

    @Test
    fun `the lock pins it, and the window fits what it shows`() {
        val src = read("FloatingTracker.kt")
        assertTrue("val locked = TrackerOptions.floatingLocked" in src)
        assertTrue("if (locked) Modifier else Modifier\n                            .pointerInput(areaW, areaH)" in src, "no dragging while locked")
        assertTrue("if (!locked) {\n            // The grabs" in src, "no resizing while locked")
        assertTrue("TrackerOptions.floatingLocked = !locked; TrackerOptions.save()" in src)
        assertTrue("floatingLocked=\$floatingLocked" in read("TrackerOptions.kt"), "the lock is saved")
        assertTrue("(BAR_DP + contentH).coerceIn(FloatFrame.MIN_H, shown.h)" in src, "the height set is the most it takes")
        assertTrue("val room = (shown.h - BAR_DP)" in src, "room is the height given, not the fitted one")
    }

    @Test
    fun `the attempt, FILE, the screens and docking sit under one menu of three lines`() {
        val chrome = read("LandscapeChrome.kt")
        val menu = chrome.substringAfter("internal fun TrackerCornerMenu(").substringBefore("\n}\n")
        assertTrue("MenuLinesButton(" in menu)
        for (item in listOf("\"ATTEMPT \$attempt\"", "\"File\"", "\"2 screens\"", "\"Dock the tracker beside the game\"", "\"Hide the tracker\""))
            assertTrue(item in menu, item)
        val play = read("PlayScreen.kt")
        assertFalse("OverlayChip(if (menuOpen) \"HIDE\" else \"FILE\")" in play, "the row of chips is gone")
        assertTrue("headerTrailing = corner" in play)
        assertTrue("PcCanvas(Modifier.width(PcMin.TOUCH_DP.dp)) { menu(onDock) }" in read("FloatingTracker.kt"), "the window's menu is in its title bar")
    }

    @Test
    fun `the File band is centred, shows arrows while it scrolls and closes with an X`() {
        val band = read("LandscapeChrome.kt").substringAfter("internal fun LandscapeMenuBand(").substringBefore("\n}\n")
        assertTrue("LoopingRow(loop, Modifier.weight(1f).padding(vertical = 6.dp), gap = 6.dp, center = true, content = chips)" in band,
            "a short row centres in the band, a long one goes round (LoopRowTest)")
        assertTrue("if (overflows) BandArrow(" in band && "Scroll the menu left" in band && "Scroll the menu right" in band,
            "an arrow at each end, only while the row overflows")
        assertTrue("loop.page(forward = false)" in band && "loop.page(forward = true)" in band, "the arrows move through the loop")
        assertTrue("contentDescription = \"Close the menu\"" in band && "onClose()" in band)
        assertTrue(band.indexOf("contentDescription = \"Close the menu\"") > band.indexOf("LoopingRow("), "the X stays put, outside the loop")
        assertTrue("LandscapeMenuBand(" in read("PlayScreen.kt"))
    }

    /**
     * rc32 audit P2 #22: hidden from its corner and then floated from the screen-tap menu, the tracker drew as a window
     * while Play still held it hidden, so the second screen stayed empty and CAM drew a camera in both places.
     */
    @Test
    fun `floating a hidden tracker from the screen tap brings it back, with no peek left behind`() {
        val menu = read("LandscapeChrome.kt").substringAfter("internal fun ScreenTapMenu(").substringBefore("\n}\n")
        val float = menu.substringAfter("onFloat = if (trackerOnSecond) null else { {").substringBefore("} },")
        val save = float.indexOf("TrackerOptions.landscapeTracker = LandscapeTracker.FLOATING; TrackerOptions.save()")
        assertTrue(save >= 0)
        assertTrue(float.indexOf("onShow(); ui.trackerPeek = false") > save, "shown again once it floats, then the peek cleared")
    }

    @Test
    fun `the DS note is drawn once, on the opponent's card`() {
        val pc = read("PcTracker.kt")
        assertTrue("TrackerOptions.carouselShows(\"Notes\") && !viewingOwn && !notesInCard" in pc)
        assertTrue("notesInCard = true" in read("NdsTrackerPanel.kt"))
    }
}
