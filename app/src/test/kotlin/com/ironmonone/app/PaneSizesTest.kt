package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * rc32 audit P2 #46: the tracker width and the floating window's frame were rebuilt from the values read when the game
 * opened whenever the window changed size, on every rotation, and the reset was saved over the player's choice.
 * P2 #56: Play read both in its own body, so every drag event recomposed the whole screen. P3 #56: the divider's drag
 * block kept the first window's width.
 */
class PaneSizesTest {
    private fun play() = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText().replace("\r\n", "\n")

    @Test
    fun `the column keeps its share through a rotation, and the share is what is saved`() {
        val p = PaneSizes(null, null)
        assertEquals(PaneSizes.DEFAULT_FRACTION, p.fraction)
        p.drag(-(320f - p.width(800f)), 800f)          // dragged left until the column is 320dp wide
        assertEquals(320f, p.width(800f), 0.01f)
        assertEquals(0.4f, p.fraction, 0.0001f)
        // Portrait, then back: nothing derives it again from the values the game opened with.
        p.width(360f)
        assertEquals(320f, p.width(800f), 0.01f)
        assertEquals(0.4f, p.fraction, 0.0001f)
    }

    @Test
    fun `the split keeps 170dp of game and 150 of tracker`() {
        val p = PaneSizes(0.3f, null)
        p.drag(-10_000f, 640f)
        assertEquals(640f - 170f, p.width(640f), 0.01f)
        p.drag(10_000f, 640f)
        assertEquals(150f, p.width(640f), 0.01f)
        assertEquals(PaneSizes.DEFAULT_FRACTION, PaneSizes(0.95f, null).fraction, "a share outside 0.1..0.9 read back is the default")
    }

    @Test
    fun `the floating window stays where it was put, whatever window comes and goes`() {
        val p = PaneSizes(null, listOf(500f, 10f, 250f, 300f))
        assertEquals(FloatFrame(500f, 10f, 250f, 300f), p.frameFor(360f, 800f), "a portrait window does not move it")
        assertEquals(FloatFrame(500f, 10f, 250f, 300f), p.frame)
        val fresh = PaneSizes(null, null)
        assertNull(fresh.frame, "nothing saved until it is moved")
        assertEquals(FloatFrame.default(851f, 393f), fresh.frameFor(851f, 393f))
    }

    @Test
    fun `Play reads neither in its own body, and saves them without recomposing`() {
        val play = play()
        assertFalse("remember(windowWidthDp, session.id)" in play, "the width was derived again from prefs0 on a rotation")
        assertFalse("remember(windowWidthDp, windowHeightDp, session.id)" in play)
        assertTrue("val panes = remember(session.id) { PaneSizes(prefs0.trackerFraction, prefs0.floatFrame) }" in play)
        assertTrue("LaunchedEffect(speed, muted, dsTopOnly, session.id) { panes.saveWith(speed, muted, dsTopOnly) { store.saveGameSettings(session, it) } }" in play)
        assertTrue("panes = panes, windowW = windowWidthDp, windowH = windowHeightDp," in play, "the window reads its frame itself")
        val settings = File("src/main/kotlin/com/ironmonone/app/GameSettings.kt").readText()
        assertTrue("snapshotFlow { fraction to frame }.collectLatest" in settings)
    }

    @Test
    fun `the edge's drag is keyed on the window`() {
        // rc34: the bar is gone, and the tracker's own left edge is dragged instead (TrackerEdge.kt, TrackerEdgeTest).
        assertTrue("DockedTracker(panes, windowWidthDp, trackerContent)" in play())
        val edge = File("src/main/kotlin/com/ironmonone/app/TrackerEdge.kt").readText().replace("\r\n", "\n")
        assertTrue("pointerInput(panes, windowW) {" in edge)
        assertFalse("pointerInput(Unit)" in edge)
        assertTrue("EdgeDrag(TrackerEdge.ZONE_DP.dp.toPx(), viewConfiguration.touchSlop, density, panes, windowW)" in edge)
        assertTrue("panes.drag(mx / density, windowW)" in edge && "panes.drag(dx / density, windowW)" in edge)
    }
}
