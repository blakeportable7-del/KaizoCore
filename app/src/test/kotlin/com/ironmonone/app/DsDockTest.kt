package com.ironmonone.app

import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The DS docked beside its tracker (Blake, 2026-10-02: "the bottom second screen to be below the tracker in tracker
 * docked mode"). The geometry runs for real against melonDS's own frame; the wiring is held to the source.
 */
class DsDockTest {
    private val dir = File("src/main/kotlin/com/ironmonone/app")
    private fun read(name: String) = File(dir, name).readText().replace("\r\n", "\n")
    private fun near(want: Float, got: Float, what: String) = assertTrue(abs(want - got) < 0.6f, "$what: $want, got $got")

    @Test
    fun `on a phone the picture runs to the right edge and the tracker takes the box above the touch screen`() {
        // 2340 x 1080, ratio 2: the 772 x 384 frame at 2.8125, flush right.
        val g = DsDock.geometry(2340f, 1080f, 2)!!
        near(168.75f / 2340f * 2340f, g.left * 2340f, "the picture's left edge")
        near(0f, g.top * 1080f, "full height")
        near(2340f, (g.left + g.width) * 2340f, "flush with the right edge")
        near(731.25f, g.trackerW, "the column beside the big top screen, 266 dp")
        near(540f, g.trackerH, "down to the touch screen")
        // The touch screen is then the box under it, in the corner: x 513..769, y 192..384 of the frame.
        val scale = 1080f / 384f
        val touchLeft = g.left * 2340f + 513f * scale
        assertTrue(touchLeft >= 2340f - g.trackerW, "the touch screen sits in the tracker's column")
        near(1080f, 384f * scale, "and runs to the bottom")
    }

    @Test
    fun `a ratio of 3 gives the tracker more height and less width, and a tablet stays centred`() {
        val g = DsDock.geometry(2340f, 1080f, 3)!!
        near(491.25f, g.trackerW, "ratio 3 width")
        near(720f, g.trackerH, "ratio 3 height")
        // 4:3, the frame is width-bound: centred on the height, the tracker from the top down to the touch screen.
        val t = DsDock.geometry(2048f, 1536f, 2)!!
        near(0f, t.left, "the whole width")
        val scale = 2048f / 772f
        val top = (1536f - 384f * scale) / 2f
        near(top / 1536f * 1536f, t.top * 1536f, "centred")
        near(top + 192f * scale, t.trackerH, "down to the touch screen")
        assertNull(DsDock.geometry(0f, 1080f, 2), "nothing to lay out before the view is measured")
    }

    @Test
    fun `PlayScreen docks it only for a tracked DS on Hybrid Top, with the pad kept off the touch screen`() {
        val play = read("PlayScreen.kt")
        assertTrue("val dsDock = dsDockIn(retro, ui, landscape && fullscreen && dsScreens && !dsTopOnly && dsLayoutName == \"hybrid-top\" && session.tracked && !streamClean && !trackerOnSecond," in play)
        assertTrue("} else if (dsDock != null) {" in play, "no column beside the game while it is docked over it")
        assertTrue(".clearOfDsDock(dsDock)," in play, "the pad keeps to the left of the column")
        assertTrue("dsDock?.let { DsDockTracker(it, Modifier.align(Alignment.TopEnd)) { trackerContent() } }" in play)
        val dock = read("DsDock.kt")
        assertTrue("TrackerOptions.landscapeTracker == LandscapeTracker.DOCKED || ui.trackerPeek" in dock, "docked and open, as the column is")
        assertTrue("if (retro != null && retro.viewport != want) retro.viewport = want" in dock, "and back to the whole view when it stops")
        assertTrue("CompositionLocalProvider(LocalTrackerRoom provides h)" in dock, "short: one card with SEE FOE")
    }
}
