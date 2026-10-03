package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * rc34: no bar between the game and the docked tracker (Blake, 2026-10-03: "i really dislike the vertical bar between
 * the game screen and tracker on landscape mode"). The tracker's own left edge resizes it. Each touch is decided by
 * EdgeDrag, held here move by move on a real PaneSizes; the column's pointer loop that feeds it, the grip, TalkBack's
 * node and the call in Play are held to the source.
 */
class TrackerEdgeTest {
    private val dir = File("src/main/kotlin/com/ironmonone/app")
    private fun read(name: String) = File(dir, name).readText().replace("\r\n", "\n")

    // A phone at 2.75 px per dp with Android's usual 8 dp touch slop. Moves are written in dp and fed in px, as the
    // pointer loop feeds them.
    private val density = 2.75f
    private val slop = 8f * density
    private val window = 851f
    private fun px(dp: Float) = dp * density
    private fun edge(panes: PaneSizes) = EdgeDrag(px(TrackerEdge.ZONE_DP), slop, density, panes, window)
    private fun EdgeDrag.moveDp(dx: Float, dy: Float) = move(px(dx), px(dy))

    @Test
    fun `a drag from the edge moves the split once it passes the slop sideways, and the edge stays under the finger`() {
        val panes = PaneSizes(null, null)
        val start = panes.width(window)
        val e = edge(panes)
        assertTrue(e.down(px(10f)), "10 dp in is the edge")
        assertEquals(EdgeDrag.Step.WATCH, e.moveDp(-3f, 0f))
        assertEquals(EdgeDrag.Step.WATCH, e.moveDp(-3f, 1f))
        assertEquals(start, panes.width(window), "nothing moves while it could still be a tap")
        assertFalse(e.resizing)
        assertEquals(EdgeDrag.Step.TAKE, e.moveDp(-3f, 0f), "9 dp sideways is past the slop")
        assertTrue(e.resizing)
        assertEquals(start + 9f, panes.width(window), 0.01f, "the whole way from the down, not only the part past the slop")
        assertEquals(EdgeDrag.Step.TAKE, e.moveDp(-20f, 6f))
        assertEquals(start + 29f, panes.width(window), 0.01f, "up or down no longer matters once it moves the split")
        assertEquals(EdgeDrag.Step.TAKE, e.moveDp(40f, 0f))
        assertEquals(start - 11f, panes.width(window), 0.01f, "and back the other way")
        assertTrue(e.up(), "the lift after a resize is taken too")
        assertFalse(e.resizing, "the grip goes when the finger lifts")
    }

    @Test
    fun `a tap in the zone is never taken, however much the finger trembles`() {
        val panes = PaneSizes(0.3f, null)
        val e = edge(panes)
        assertTrue(e.down(px(2f)))
        for ((dx, dy) in listOf(1f to 1f, -2f to 0.5f, 3f to -2f, 2.5f to 1f))   // 4.5 dp right and 0.5 down at most
            assertEquals(EdgeDrag.Step.WATCH, e.moveDp(dx, dy), "a tremble is not a drag")
        assertFalse(e.resizing)
        assertFalse(e.up(), "a tap's lift is the card's")
        assertEquals(0.3f, panes.fraction)
    }

    @Test
    fun `a touch that comes down outside the zone does nothing`() {
        val panes = PaneSizes(0.3f, null)
        val e = edge(panes)
        assertFalse(e.down(px(TrackerEdge.ZONE_DP + 1f)), "25 dp in is the card's")
        assertEquals(EdgeDrag.Step.LEAVE, e.moveDp(-60f, 0f))
        assertEquals(EdgeDrag.Step.LEAVE, e.moveDp(-60f, 0f))
        assertFalse(e.up())
        assertEquals(0.3f, panes.fraction)
        assertTrue(edge(panes).down(px(TrackerEdge.ZONE_DP)), "24 dp in is still the edge")
        assertTrue(edge(panes).down(0f))
    }

    @Test
    fun `a swipe up or down from the zone is the column's scroll`() {
        val panes = PaneSizes(0.3f, null)
        val e = edge(panes)
        assertTrue(e.down(px(8f)))
        assertEquals(EdgeDrag.Step.WATCH, e.moveDp(2f, 5f))
        assertEquals(EdgeDrag.Step.LEAVE, e.moveDp(1f, 4f), "9 dp down before 8 sideways: the column's scroll starts there")
        assertEquals(EdgeDrag.Step.LEAVE, e.moveDp(-60f, 0f), "a scroll that turns sideways stays a scroll")
        assertFalse(e.up())
        assertEquals(0.3f, panes.fraction)
    }

    @Test
    fun `the clamp holds, 170 dp of game and 150 of tracker`() {
        val panes = PaneSizes(null, null)
        val e = edge(panes)
        assertTrue(e.down(px(12f)))
        assertEquals(EdgeDrag.Step.TAKE, e.moveDp(-2000f, 0f))
        assertEquals(window - 170f, panes.width(window), 0.01f)
        assertEquals(EdgeDrag.Step.TAKE, e.moveDp(4000f, 0f))
        assertEquals(150f, panes.width(window), 0.01f)
        assertTrue(e.up())
    }

    @Test
    fun `TalkBack's two actions step the same clamp, and say when they can go no further`() {
        val panes = PaneSizes(null, null)
        assertEquals(26, TrackerEdge.percent(panes))
        assertTrue(TrackerEdge.wider(panes, window))
        assertEquals(31, TrackerEdge.percent(panes), "5% of the screen a step")
        while (TrackerEdge.wider(panes, window)) Unit
        assertEquals(window - 170f, panes.width(window), 0.01f)
        assertFalse(TrackerEdge.wider(panes, window), "nothing moved")
        while (TrackerEdge.narrower(panes, window)) Unit
        assertEquals(150f, panes.width(window), 0.01f)
        assertFalse(TrackerEdge.narrower(panes, window))
    }

    @Test
    fun `Play has no bar, and one line hands the docked column to DockedTracker`() {
        val play = read("PlayScreen.kt")
        val pane = play.substringAfter("val trackerPane: @Composable () -> Unit = {").substringBefore("\n    }\n")
        assertTrue(
            "LandscapeTracker.DOCKED || ui.trackerPeek)) {\n" +
                "              // No bar: the game meets the tracker, and the tracker's left edge resizes it (TrackerEdge.kt, rc34).\n" +
                "              DockedTracker(panes, windowWidthDp, trackerContent)\n" +
                "          } else {" in pane,
            "the docked branch is the one call",
        )
        assertEquals(1, Regex(Regex.escape("DockedTracker(")).findAll(play).count())
        for (gone in listOf("Resize tracker", "reset the split", "detectDragGestures", "detectTapGestures", "onDoubleTap",
            "DEFAULT_FRACTION", "Modifier.width(24.dp)", "repeat(3)", "panes.drag(", "panes.fraction"))
            assertFalse(gone in play, "the bar is gone from Play: $gone")
    }

    @Test
    fun `the column reads each touch before its cards, and takes nothing until it is a resize`() {
        val src = read("TrackerEdge.kt")
        val loop = src.substringAfter("private fun Modifier.edgeResize(").substringBefore("\n/**")
        assertTrue("pointerInput(panes, windowW) {" in loop, "keyed on the window too (rc32 audit P3 #56)")
        assertTrue("awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)" in loop)
        assertTrue("awaitPointerEvent(PointerEventPass.Initial)" in loop)
        assertTrue("EdgeDrag(TrackerEdge.ZONE_DP.dp.toPx(), viewConfiguration.touchSlop, density, panes, windowW)" in loop)
        assertTrue("if (!edge.down(down.position.x)) return@awaitEachGesture" in loop)
        assertTrue("if (step == EdgeDrag.Step.LEAVE) break" in loop)
        assertEquals(2, Regex(Regex.escape(".consume()")).findAll(loop).count(), "a resize's moves and its lift, nothing else")
        assertTrue("if (step == EdgeDrag.Step.TAKE) {\n                        change.consume()\n                        dragging.value = true" in loop)
        assertTrue("if (edge.up()) change.consume()" in loop)
        for (no in listOf("detectTapGestures", "onDoubleTap", "detectDragGestures", "DEFAULT_FRACTION", "PointerEventPass.Main"))
            assertFalse(no in src, "no double tap and no second detector: $no")
    }

    @Test
    fun `the grip is drawn only while dragging, and TalkBack's node takes no touch`() {
        val src = read("TrackerEdge.kt")
        val docked = src.substringAfter("internal fun DockedTracker(").substringBefore("\n}\n")
        assertTrue(".edgeResize(panes, windowW, dragging)" in docked, "on the column itself, not a layer over it")
        assertTrue("drawContent()\n                if (dragging.value) edgeGrip()" in docked, "over the cards, and only while dragging")
        assertTrue("TrackerScroll(Modifier.fillMaxSize()) { content() }\n        EdgeLabel(panes, windowW)" in docked)
        assertEquals(1, Regex(Regex.escape("dragging.value = true")).findAll(src).count())
        assertTrue("} finally {\n                dragging.value = false" in src, "gone when the finger lifts or the touch is cancelled")
        val label = src.substringAfter("private fun EdgeLabel(").substringBefore("\n}\n")
        for (no in listOf("pointerInput", "clickable", "draggable", "Gestures", "scrollable"))
            assertFalse(no in label, "TalkBack's node takes no touch: $no")
        assertTrue("contentDescription = \"Resize tracker\"" in label)
        assertTrue("CustomAccessibilityAction(\"Make tracker wider\") { TrackerEdge.wider(panes, windowW) }" in label)
        assertTrue("CustomAccessibilityAction(\"Make tracker narrower\") { TrackerEdge.narrower(panes, windowW) }" in label)
    }
}
