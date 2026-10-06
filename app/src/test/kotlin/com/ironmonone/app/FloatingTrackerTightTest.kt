package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The floating window's edge on the tracker's lines (Blake, 2026-10-03: "shave off the buffer space around that tracker
 * and bring the edge to the lines"), and its one slim top row with no buttons of its own since the FILE bar (2026-10-06).
 * The frame maths runs for real; what is drawn is held to the source.
 */
class FloatingTrackerTightTest {
    private val dir = File("src/main/kotlin/com/ironmonone/app")
    private fun read(name: String) = File(dir, name).readText().replace("\r\n", "\n")
    private fun code(text: String) = text.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("//[^\\n]*"), "")

    private val areaW = 851f
    private val areaH = 393f

    @Test
    fun `clamping keeps the window on screen and flush to every edge, and leaves room beside and under it for the corner`() {
        val f = FloatFrame(0f, 0f, 300f, 200f)
        assertEquals(areaW - 300f, f.copy(x = 5000f).clamped(areaW, areaH).x, "flush right")
        assertEquals(areaH - 200f, f.copy(y = 5000f).clamped(areaW, areaH).y, "flush with the bottom")
        assertEquals(0f, f.copy(x = -50f, y = -50f).clamped(areaW, areaH).x, "flush left")
        val huge = FloatFrame(0f, 0f, 5000f, 5000f).clamped(areaW, areaH)
        assertEquals(areaW - FloatGrabs.OUT, huge.w, "never the whole width: the corner button always has room")
        assertEquals(areaH - FloatGrabs.OUT, huge.h, "never the whole height")
        val small = FloatFrame(10f, 10f, 1f, 1f).clamped(areaW, areaH)
        assertEquals(FloatFrame.MIN_W, small.w); assertEquals(FloatFrame.MIN_H, small.h)
        // A box smaller than the least window still gives the least window, and does not throw.
        assertEquals(FloatFrame.MIN_W, f.clamped(100f, 100f).w)
    }

    @Test
    fun `the window has no border of its own, and gives the panels its narrow margin`() {
        val src = code(read("FloatingTracker.kt"))
        assertFalse("EDGE_DP" in src || "frameW" in src || "frameDp" in src, "the 16 dp inner border is gone")
        assertTrue("LocalTrackerMargin provides FLOAT_MARGIN" in src)
        assertTrue("private val FLOAT_MARGIN = PaddingValues(FloatGrabs.IN.dp)" in src)
        assertTrue(FloatGrabs.IN <= 2f, "about 2 dp at most between the border and the boxes")
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
        assertTrue("val fitH = if (contentH > 0f) (barDp + contentH).coerceIn(FloatFrame.MIN_H, live.h) else live.h" in src)
        assertTrue("val room = (live.h - barDp)" in src, "room is the height given, not the fitted one")
    }

    @Test
    fun `the top is one slim row, the text slot, then the swap in a battle, each with 44 dp to touch`() {
        val src = code(read("FloatingTracker.kt"))
        val bar = src.substringAfter("Row(Modifier.fillMaxWidth().height(barDp.dp).background(windowFill(Pc.Ground)), verticalAlignment = Alignment.CenterVertically) {")
            .substringBefore("\n                }\n")
        val title = bar.indexOf("WindowBarTextSlot(segs, parts?.onTextTap, parts?.tapLabel, Modifier.fillMaxWidth())")
        val swap = bar.indexOf("SwapIconButton(it)")
        assertTrue(title in 0 until swap, "the text, then the swap")
        assertTrue("Box(Modifier.weight(1f).padding(start = 6.dp, end = 2.dp).overhang())" in bar, "the text takes the room")
        assertTrue("Box(Modifier.overhang().onGloballyPositioned" in bar, "the swap keeps its whole box")
        assertTrue(WindowBarFit.ROW_DP < PcMin.TOUCH_DP, "the row is drawn slimmer than a touch box")
        val over = src.substringAfter("private fun Modifier.overhang()").substringBefore("\n}\n")
        assertTrue("maxHeight = Constraints.Infinity" in over && "minOf(p.height, c.maxHeight)" in over && "(h - p.height) / 2" in over,
            "measured whole, laid out at the row's height, centred on it")
        // Narrowest window, in a battle: the text still has over 100 dp beside the swap.
        assertTrue(FloatFrame.MIN_W - PcMin.TOUCH_DP - 8f > 100f)
    }
}
