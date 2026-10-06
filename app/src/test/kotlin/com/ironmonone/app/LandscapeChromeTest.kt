package com.ironmonone.app

import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Landscape's chrome. Blake's notes of 2026-10-02 gave the tracker a menu of three lines and the File band an X; since
 * 2026-10-06 both are the FILE bar's (FileBarTest). What stays here: one card with SEE FOE where the tracker is short, a
 * floating window that moves to the edge and fits its content, and the DS note drawn once.
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
    fun `the window moves to every edge of the box it is drawn in, and fits what it shows`() {
        val f = FloatFrame(0f, 0f, 300f, 200f)
        assertEquals(851f - 300f, f.copy(x = 5000f).clamped(851f, 393f).x, "flush right, not short of it")
        assertEquals(393f - 200f, f.copy(y = 5000f).clamped(851f, 393f).y, "flush with the bottom")
        assertTrue(FloatFrame.MIN_W <= 160f && FloatFrame.MIN_H <= 120f, "any size, down to a title bar and a few rows")
        val src = read("FloatingTracker.kt")
        assertTrue("BoxWithConstraints(Modifier.fillMaxSize())" in src && "val areaW = maxWidth.value" in src,
            "it measures the box it moves in; LocalConfiguration's screen size leaves out the system bars")
        assertTrue("(barDp + contentH).coerceIn(FloatFrame.MIN_H, live.h)" in src, "the height set is the most it takes")
        assertTrue("val room = (live.h - barDp)" in src, "room is the height given, not the fitted one")
    }

    @Test
    fun `the landscape chrome file keeps only the room and the cards' measure`() {
        val chrome = read("LandscapeChrome.kt")
        for (gone in listOf("TrackerCornerMenu", "LandscapeMenuBand", "ScreenTapMenu", "NewRunChip", "MenuLinesButton", "DropdownMenu"))
            assertFalse(gone in chrome, gone)
        assertTrue("val LocalTrackerRoom" in chrome && "object TrackerRoom" in chrome && "val LocalAttemptInTitle" in chrome)
    }

    @Test
    fun `the DS note is drawn once, on the opponent's card`() {
        val pc = read("PcTracker.kt")
        assertTrue("TrackerOptions.carouselShows(\"Notes\") && !viewingOwn && !notesInCard" in pc)
        assertTrue("notesInCard = true" in read("NdsTrackerPanel.kt"))
    }
}
