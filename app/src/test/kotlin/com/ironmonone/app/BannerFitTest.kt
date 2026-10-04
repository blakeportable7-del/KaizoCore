package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The battle banner's words beside its buttons, or under them, and never a letter to a line (Blake, 2026-10-03, a Nat.
 * Dex Emerald double battle with the tracker docked: "Something bad is happening with double battles"; the side words
 * stood one letter per line down a tall banner and TRAINER BATTLE did not show). The plan runs for real; what is drawn
 * is held to the source.
 */
class BannerFitTest {
    private val dir = File("src/main/kotlin/com/ironmonone/app")
    private fun read(name: String) = File(dir, name).readText().replace("\r\n", "\n")
    private fun code(text: String) = text.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("//[^\\n]*"), "")

    // Rough widths in px at a phone's density: the band's, the title's whole and short words, the side's.
    private val band = 480
    private val title = 230
    private val titleShort = 120
    private val sideFull = 240
    private val sideShort = 200

    private fun plan(room: Int, hasLines: Boolean = true, lines: Int = sideFull, linesShort: Int = sideShort) =
        BannerFit.plan(room, band, title, titleShort, lines, linesShort, hasLines)

    @Test
    fun `in Blake's banner the buttons leave a sliver, so the title and the side go under them, whole`() {
        // About 5 dp beside SEE MINE, SETUP and the menu in a 181 dp docked column.
        val p = plan(room = 14)
        assertFalse(p.titleInRow, "not even TRAINER fits beside the buttons, so it goes under them")
        assertFalse(p.titleShort, "and has the band's width there for TRAINER BATTLE")
        assertFalse(p.linesInRow, "the side goes under too")
        assertFalse(p.linesShort, "in its whole words")
        assertTrue(p.under)
    }

    @Test
    fun `a wide banner keeps everything beside the buttons`() {
        val p = plan(room = 300)
        assertTrue(p.titleInRow && !p.titleShort && p.linesInRow && !p.linesShort && !p.under)
    }

    @Test
    fun `in between, the title shortens before it moves, and the side moves before it is cut`() {
        val p = plan(room = 150)
        assertTrue(p.titleInRow, "TRAINER fits beside the buttons")
        assertTrue(p.titleShort, "TRAINER BATTLE does not")
        assertFalse(p.linesInRow, "the side's short words do not fit there either, so the side goes under")
        assertFalse(p.linesShort, "where its whole words fit")
        val roomy = plan(room = 210)
        assertTrue(roomy.linesInRow && roomy.linesShort, "MINE LEFT fits beside the buttons where MINE ON THE LEFT does not")
    }

    @Test
    fun `a single battle with nothing under the title only ever moves the title`() {
        assertFalse(plan(room = 14, hasLines = false).titleInRow)
        assertTrue(plan(room = 14, hasLines = false).linesInRow, "no lines to move")
        assertFalse(plan(room = 300, hasLines = false).under)
        assertFalse(plan(room = 300, hasLines = false).linesShort)
    }

    @Test
    fun `at any width the plan puts every word where its form fits, or under the buttons across the band`() {
        for (room in 0..band step 7) {
            val p = plan(room)
            val titleRoom = if (p.titleInRow) room else band
            val titleW = if (p.titleShort) titleShort else title
            assertTrue(titleW <= titleRoom, "room $room: the title fits where it is put")
            val linesRoom = if (p.linesInRow) room else band
            val linesW = if (p.linesShort) sideShort else sideFull
            assertTrue(linesW <= linesRoom, "room $room: the side fits where it is put")
            // The lines are never beside the buttons with the title under them.
            assertFalse(p.linesInRow && !p.titleInRow, "room $room")
        }
    }

    @Test
    fun `no word in a banner can wrap, so none can stand a letter to a line`() {
        val pc = code(read("PcTracker.kt"))
        val band = pc.substringAfter("internal fun PcBannerBand(").substringBefore("\n}\n")
        val banner = pc.substringAfter("fun PcBattleBanner(").substringBefore("\n}\n")
        assertTrue(band.length > 1500 && banner.length > 1000, "both bodies were cut out")
        // PixText draws one line unless wrap = true is asked for (maxLines = 1, softWrap = false).
        assertFalse("wrap = true" in banner || "wrap = true" in band, "nothing in the banner wraps")
        assertTrue("maxLines = if (wrap) Int.MAX_VALUE else 1" in pc && "softWrap = wrap)" in pc)
        // Each word is measured at its own width on one line, and placed only where BannerFit says it fits.
        assertTrue("val one = androidx.compose.ui.unit.Constraints()" in band)
        for (slot in listOf("TITLE", "TITLE_SHORT", "SIDE", "SIDE_SHORT", "NOTE")) assertTrue("measured(BandSlot.$slot," in band, slot)
        // Those measurements are never drawn and say nothing to a screen reader; each word is drawn once, as planned.
        assertTrue("subcompose(slot) { Box(Modifier.clearAndSetSemantics { }) { c() } }.first().measure(one)" in band)
        assertTrue("subcompose(BandSlot.SHOWN_TITLE) { if (plan.titleShort) labelShort() else label() }" in band)
        assertTrue("val plan = BannerFit.plan(" in band)
        assertTrue("for (p in under) { p.place(0, y); y += p.height + gapPx }" in band, "the lines under the buttons span the band")
        // The side and its short words, the attempt and the weather all go to the band, not into the title's line.
        assertTrue("side = side?.let { s -> { PixText(s.full, PcRef.FONT - 2, Pc.Text, weight = FontWeight.Medium) } }," in banner)
        assertTrue("sideShort = side?.let { s -> { PixText(s.short, PcRef.FONT - 2, Pc.Text, weight = FontWeight.Medium) } }," in banner)
        assertTrue("labelShort = { PixText(if (isWild) \"WILD\" else \"TRAINER\"" in banner, "TRAINER at the least")
        // Both trackers draw this one banner: the DS panel's banner is the same code.
        assertTrue("PcBattleBanner(" in read("NdsTrackerPanel.kt") && "PcBattleBanner(" in read("TrackerPanel.kt"))
        assertEquals(1, Regex("fun PcBattleBanner\\(").findAll(pc).count())
    }
}
