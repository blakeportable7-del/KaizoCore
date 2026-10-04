package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The File strip and the layout editor's bar go round, and end at a docked DS tracker's left edge (Blake, 2026-10-03:
 * "You can't select the last buttons on this menu", "So I can't get to the menu button at all", "They both should
 * have infinite scroll"). When to go round runs for real; what is drawn is held to the source.
 */
class LoopRowTest {
    private val dir = File("src/main/kotlin/com/ironmonone/app")
    private fun read(name: String) = File(dir, name).readText().replace("\r\n", "\n")
    private fun body(src: String, head: String) = src.substringAfter(head).substringBefore("\n}\n")

    @Test
    fun `it goes round only when one copy is wider than the row, and never for a screen reader`() {
        assertTrue(LoopRow.loops(rowPx = 1200, windowPx = 800, reader = false), "wider than its window")
        assertFalse(LoopRow.loops(rowPx = 800, windowPx = 800, reader = false), "exactly fits: shown once")
        assertFalse(LoopRow.loops(rowPx = 500, windowPx = 800, reader = false), "a wide screen shows them once")
        assertFalse(LoopRow.loops(rowPx = 1200, windowPx = 800, reader = true), "TalkBack hears one list, not an endless one")
        assertFalse(LoopRow.loops(rowPx = 1200, windowPx = 0, reader = false), "nothing goes round before the row is laid out")
        assertEquals(1, LoopRow.copies(false), "one copy, no duplicates, when it does not go round")
        assertTrue(LoopRow.copies(true) >= 10_000, "enough copies that no thumb reaches either end")
        assertEquals(LoopRow.COUNT / 2, LoopRow.start(true), "it opens in the middle, with room both ways")
        assertEquals(0, LoopRow.start(false))
    }

    @Test
    fun `the copies are one lazy row, opened in the middle, and a copy that fits is drawn once`() {
        val row = body(read("LoopingRow.kt"), "internal fun LoopingRow(")
        assertTrue("LaunchedEffect(loops) { state.list.scrollToItem(LoopRow.start(loops)) }" in row)
        assertTrue("items(LoopRow.copies(loops))" in row && "LazyRow(" in row)
        assertTrue("if (loops) Modifier else Modifier.widthIn(min = window)" in row, "a short row can centre in the band")
        assertTrue("Modifier.onSizeChanged { state.rowPx = it.width }" in row, "the copy is measured at its own width")
        val reader = body(read("LoopingRow.kt"), "internal fun rememberScreenReaderOn(")
        assertTrue("isTouchExplorationEnabled" in reader && "addTouchExplorationStateChangeListener" in reader &&
            "removeTouchExplorationStateChangeListener" in reader, "it follows TalkBack being turned on and off")
    }

    @Test
    fun `both bars go round, the X and DONE stay put`() {
        val band = body(read("LandscapeChrome.kt"), "internal fun LandscapeMenuBand(")
        assertTrue("LoopingRow(loop," in band)
        val bar = body(read("PadFree.kt"), "fun LayoutToolbar(")
        val done = bar.indexOf("LayoutChip(\"Done\", accent = true, onClick = onDone)")
        val loop = bar.indexOf("LoopingRow(loop, Modifier.weight(1f), gap = 6.dp) {")
        assertTrue(done in 0 until loop, "DONE comes first, outside the loop: it must never scroll away")
        for (chip in listOf("LayoutChip(\"Cancel\"", "LayoutChip(\"Reset\")", "\"Bar to top\" else \"Bar to bottom\"", "LayoutChip(\"Screens: \""))
            assertTrue(bar.indexOf(chip) > loop, "$chip goes round")
        assertFalse("horizontalScroll" in bar, "the old scroll is gone")
        // The confirm dialogs stay outside the copies, so a chip from any copy opens the one dialog.
        assertTrue(bar.indexOf("confirm?.let { (title, yes, run) ->") > bar.indexOf("modifier = Modifier.padding(horizontal = 6.dp),"))
    }

    @Test
    fun `both bars end at a docked DS tracker's left edge`() {
        assertEquals(0f, DsDock.cover(null), "no dock, no room kept")
        val g = DsDock.geometry(2340f, 1080f, 2)!!
        assertEquals(g.trackerW, DsDock.cover(g), "the room is the tracker's own width")
        val dock = read("DsDock.kt")
        assertTrue("DsDock.coverPx = DsDock.cover(g)" in dock, "kept up to date with the dock")
        assertTrue("DisposableEffect(Unit) { onDispose { DsDock.coverPx = 0f } }" in dock, "and cleared when Play goes")
        assertTrue("modifier.padding(end = dsDockClearance())" in body(read("LandscapeChrome.kt"), "internal fun LandscapeMenuBand("), "the File strip")
        assertTrue("Modifier.padding(end = dsDockClearance()).fillMaxWidth(0.72f)" in body(read("PadFree.kt"), "fun LayoutToolbar("),
            "the layout editor's bar, still centred in what is left so the corners stay free")
        // The docked tracker is drawn at the top right of the same box as both bars.
        val play = read("PlayScreen.kt")
        assertTrue("dsDock?.let { DsDockTracker(it, Modifier.align(Alignment.TopEnd)) { trackerContent() } }" in play)
    }
}
