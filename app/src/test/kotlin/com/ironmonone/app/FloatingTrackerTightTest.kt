package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The floating window's edge on the tracker's lines (Blake, 2026-10-03: "shave off the buffer space around that tracker
 * and bring the edge to the lines, the hamburger menu and lock and attempt will be moved closer together"). The frame and
 * grab maths run for real; what is drawn is held to the source.
 */
class FloatingTrackerTightTest {
    private val dir = File("src/main/kotlin/com/ironmonone/app")
    private fun read(name: String) = File(dir, name).readText().replace("\r\n", "\n")
    private fun code(text: String) = text.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("//[^\\n]*"), "")

    private val areaW = 851f
    private val areaH = 393f

    @Test
    fun `clamping keeps the window on screen and flush to every edge, and leaves room beside and under it for a grab`() {
        val f = FloatFrame(0f, 0f, 300f, 200f)
        assertEquals(areaW - 300f, f.copy(x = 5000f).clamped(areaW, areaH).x, "flush right")
        assertEquals(areaH - 200f, f.copy(y = 5000f).clamped(areaW, areaH).y, "flush with the bottom")
        assertEquals(0f, f.copy(x = -50f, y = -50f).clamped(areaW, areaH).x, "flush left")
        val huge = FloatFrame(0f, 0f, 5000f, 5000f).clamped(areaW, areaH)
        assertEquals(areaW - FloatGrabs.OUT, huge.w, "never the whole width: a side grab always has room")
        assertEquals(areaH - FloatGrabs.OUT, huge.h, "never the whole height: the bottom grab always has room")
        // Moved to either side, the grab on the other side still has its full reach.
        val atLeft = FloatGrabs.of(huge, huge.h, 44f, areaW, areaH)
        assertEquals(FloatGrabs.OUT + FloatGrabs.IN, atLeft.right.w, "flush left, the right edge can still be caught")
        val atRight = huge.copy(x = 5000f).clamped(areaW, areaH)
        assertEquals(FloatGrabs.OUT + FloatGrabs.IN, FloatGrabs.of(atRight, atRight.h, 44f, areaW, areaH).left.w, "flush right, the left edge")
        val small = FloatFrame(10f, 10f, 1f, 1f).clamped(areaW, areaH)
        assertEquals(FloatFrame.MIN_W, small.w); assertEquals(FloatFrame.MIN_H, small.h)
        // A box smaller than the least window still gives the least window, and does not throw.
        assertEquals(FloatFrame.MIN_W, f.clamped(100f, 100f).w)
    }

    @Test
    fun `a left edge drag keeps the right edge, down to the least width and up to the most`() {
        val g = FloatFrame(400f, 50f, 300f, 200f)
        val wider = g.leftEdge(-100f, areaW - FloatGrabs.OUT)
        assertEquals(300f, wider.x); assertEquals(400f, wider.w)
        assertEquals(FloatFrame.MIN_W, g.leftEdge(10_000f).w, "never narrower than the least")
        val most = g.leftEdge(-10_000f, maxW = 500f)
        assertEquals(500f, most.w, "never wider than the most it may be")
        assertEquals(g.x + g.w, most.x + most.w, "and the right edge stayed put")
        assertEquals(0f, g.leftEdge(-10_000f).x, "with no most, it stops at the box's left side")
    }

    @Test
    fun `the grabs sit outside the visible edge and reach in only over the window's margin`() {
        val f = FloatFrame(300f, 40f, 260f, 300f)
        val drawnH = 250f
        val bar = 44f
        val g = FloatGrabs.of(f, drawnH, bar, areaW, areaH)
        val right = f.x + f.w
        val bottom = f.y + drawnH
        // In over the edge by the margin only: never over a box, so never over SETUP, SEE FOE or the gear.
        assertEquals(f.x + FloatGrabs.IN, g.left.x + g.left.w, "the left grab ends at the margin")
        assertEquals(right - FloatGrabs.IN, g.right.x, "the right grab starts at the margin")
        assertEquals(bottom - FloatGrabs.IN, g.bottom.y, "the bottom grab starts at the margin")
        assertEquals(FloatGrabs.OUT + FloatGrabs.IN, g.right.w)
        assertTrue(FloatGrabs.IN <= 2f, "about 2 dp at most between the border and the boxes")
        // Below the title bar, which moves the window instead.
        assertEquals(f.y + bar, g.left.y); assertEquals(f.y + bar, g.right.y)
        assertEquals(bottom, g.left.y + g.left.h, "a side grab runs down to the drawn bottom, not the height set")
        // The corners reach further out than a side, both ways.
        assertEquals(FloatGrabs.CORNER + FloatGrabs.IN, g.bottomRight.w)
        assertEquals(FloatGrabs.CORNER + FloatGrabs.IN, g.bottomLeft.h)
        assertTrue(FloatGrabs.CORNER > FloatGrabs.OUT)
        assertEquals(f.x + FloatGrabs.IN, g.bottomLeft.x + g.bottomLeft.w)
    }

    @Test
    fun `a grab is cut to the box, and one with no room left takes no new touch`() {
        // Flush right and flush with the bottom: nothing is left outside on those sides.
        val f = FloatFrame(areaW - 300f, 20f, 300f, areaH - 20f)
        val g = FloatGrabs.of(f, f.h, 44f, areaW, areaH)
        assertFalse(g.right.usable, "no room right of a window at the right edge")
        assertFalse(g.bottom.usable, "nor under one at the bottom")
        assertFalse(g.bottomRight.usable)
        assertTrue(g.left.usable, "the left side still resizes")
        assertTrue(g.left.x >= 0f && g.right.x + g.right.w <= areaW, "inside the box")
        // Close to an edge, a grab is narrower but still there.
        val near = FloatFrame(areaW - 300f - 8f, 12f, 300f, 200f)
        val n = FloatGrabs.of(near, 200f, 44f, areaW, areaH)
        assertEquals(8f + FloatGrabs.IN, n.right.w, 0.001f, "the default frame's 8 dp, and the margin")
        assertEquals(areaW, n.right.x + n.right.w, 0.001f)
    }

    @Test
    fun `the window has no border of its own any more, and gives the panels its narrow margin`() {
        val src = code(read("FloatingTracker.kt"))
        assertFalse("EDGE_DP" in src || "frameW" in src || "frameDp" in src, "the 16 dp inner border is gone")
        assertTrue("LocalTrackerMargin provides FLOAT_MARGIN" in src)
        assertTrue("private val FLOAT_MARGIN = PaddingValues(FloatGrabs.IN.dp)" in src)
        // Only the floating window sets it: docked, portrait and the second display keep the panel's own margin.
        for (f in dir.listFiles { x -> x.extension == "kt" }!!) {
            // The Tracker HUD is the floating window's other face (TrackerHud.kt).
            if (f.name == "FloatingTracker.kt" || f.name == "TrackerHud.kt") continue
            assertFalse("LocalTrackerMargin provides" in f.readText(), "${f.name} must not change the margin")
        }
        assertTrue("LocalTrackerMargin.current ?: PaddingValues(PcRef.MARGIN.rp)" in src, "the panel's own margin otherwise")
        for (panel in listOf("TrackerPanel.kt", "NdsTrackerPanel.kt"))
            assertTrue(".then(trackerBackdrop()).padding(trackerMargin())" in read(panel), panel)
        // The window fits the tracker, with nothing under the cards.
        assertTrue("val fitH = if (contentH > 0f) (barDp + contentH).coerceIn(FloatFrame.MIN_H, shown.h) else shown.h" in src)
        // The grabs are placed from FloatGrabs, outside the window, and only while it is unlocked.
        val raw = read("FloatingTracker.kt")
        assertTrue("if (!locked) {\n            // The grabs" in raw)
        val grabs = raw.substringAfter("if (!locked) {\n            // The grabs").substringBefore("\n        }\n")
        for (g in listOf("g.right", "g.left", "g.bottom,", "g.bottomRight", "g.bottomLeft")) assertTrue("EdgeHandle($g" in grabs, g)
        assertTrue(grabs.indexOf("EdgeHandle(g.bottomRight") > grabs.indexOf("EdgeHandle(g.bottom,"), "the corners are on top")
        assertFalse("Alignment.CenterEnd" in src || "Alignment.BottomEnd" in src, "no grab is aligned inside the window")
    }

    @Test
    fun `the lock, the grip, the text slot, the swap, the gear and the menu sit in one row, with 44 dp to touch`() {
        val src = code(read("FloatingTracker.kt"))
        val bar = src.substringAfter("verticalAlignment = Alignment.CenterVertically,\n                ) {").substringBefore("\n                }\n")
        val lock = bar.indexOf("LockButton(locked)")
        val dots = bar.indexOf("if (!locked) GripDots()")
        val title = bar.indexOf("WindowBarTextSlot(segs")
        val swap = bar.indexOf("parts?.swap?.let { SwapIconButton(it) }")
        val gear = bar.indexOf("parts?.onGear?.let { TrackerGearButton(onClick = it) }")
        val menu = bar.indexOf("PcCanvas(Modifier.width(PcMin.TOUCH_DP.dp)) { menu(dock) }")
        assertTrue(lock in 0 until dots && dots < title && title < swap && swap < gear && gear < menu, "in that order")
        // The text slot takes the room the buttons leave (2026-10-04, one row): its words scroll there when they do not fit.
        assertTrue("WindowBarTextSlot(segs, parts?.onTextTap, parts?.tapLabel, Modifier.weight(1f)" in bar)
        // Only the narrow window's second row leaves a gap where the words were (WindowBarFit).
        assertFalse(Regex("Spacer\\((?!Modifier.weight\\(1f\\)\\))").containsMatchIn(bar.substring(lock, menu)), "and no other gap is put between them")
        assertTrue("private const val BAR_DP = 44" in src && 44 >= PcMin.TOUCH_DP, "the bar, and the lock's box, are a touch target tall")
        assertTrue("Modifier.size(BAR_DP.dp).clickable(role = Role.Button)" in src, "the lock's box is the bar's height both ways")
        // The rest of the bar is the handle: the drag and the double tap are on the whole row.
        assertTrue("Modifier.fillMaxWidth().height(BAR_DP.dp).background(windowFill(Pc.Ground))" in src)
        assertTrue("detectTapGestures(onDoubleTap = { move(FloatFrame.default(areaW, areaH)) })" in src)
    }
}
