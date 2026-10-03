package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Every place the tracker scrolls says so (Blake, 2026-10-02, on whether the docked tracker scrolls: it did, with
 * nothing to show it). Held to the source: the arrow only while there is more below, a 44 dp touch box, and no
 * full-size layer that could take a swipe from the column.
 */
class TrackerScrollTest {
    private val dir = File("src/main/kotlin/com/ironmonone/app")
    private fun read(name: String) = File(dir, name).readText().replace("\r\n", "\n")

    @Test
    fun `the docked column, the DS box, the floating window and both portrait panels use it`() {
        val play = read("PlayScreen.kt")
        assertEquals(2, Regex(Regex.escape("TrackerScroll(")).findAll(play).count(), "portrait DS, portrait GBA and Game Boy")
        // The docked column is in TrackerEdge.kt since rc34, with no bar beside it (TrackerEdgeTest).
        val docked = read("TrackerEdge.kt").substringAfter("internal fun DockedTracker(").substringBefore("\n}\n")
        assertTrue("Modifier.width(panes.width(windowW).dp).fillMaxHeight()" in docked)
        assertTrue("TrackerScroll(Modifier.fillMaxSize()) { content() }" in docked)
        assertTrue("TrackerScroll(modifier.size(w, h))" in read("DsDock.kt"))
        assertTrue("TrackerScroll(Modifier.fillMaxSize(), background = null)" in read("FloatingTracker.kt"))
    }

    @Test
    fun `the arrow shows only while there is more, and only it takes touches`() {
        val src = read("TrackerScroll.kt")
        assertTrue("if (scroll.canScrollForward) MoreBelow(" in src)
        assertTrue("modifier.size(PcMin.TOUCH_DP.dp)" in src && "contentDescription = \"More below. Scroll down\"" in src)
        val box = src.substringAfter("internal fun TrackerScroll(").substringBefore("\n}\n")
        assertFalse("pointerInput" in box, "nothing over the column but the arrow")
    }
}
