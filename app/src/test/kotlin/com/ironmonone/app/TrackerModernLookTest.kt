package com.ironmonone.app

import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * KaizoCore's own tracker look (Blake, 2026-10-02: "modernize the look of the tracker. KaizoCore should have its own
 * distinct tracker look. Still keep the color scheme themes", and "Lots of wasted space in our current tracker").
 * The unit the canvas picks runs for real; what is drawn is held to the source, as the other tracker tests do.
 */
class TrackerModernLookTest {
    private val dir = File("src/main/kotlin/com/ironmonone/app")
    private fun read(name: String) = File(dir, name).readText().replace("\r\n", "\n")

    // ---------------------------------------------------------------- the wide card

    @Test
    fun `the wide card is the head, a rule and the moves box side by side inside the margins`() {
        assertEquals(140, PcRef.HEAD_W)
        assertEquals(PcRef.MARGIN + PcRef.HEAD_W + 1 + PcRef.MOVES_W + PcRef.MARGIN, PcRef.WIDE_WIDTH)
        assertEquals(291, PcRef.WIDE_WIDTH)
    }

    @Test
    fun `a phone in portrait gets the wide card, at a unit that fills its width`() {
        val (unit, wide) = canvasUnit(393.dp, 224.dp)
        assertTrue(wide, "393dp holds 291 units at 1.35dp")
        assertEquals(393f / 291f, unit.value, 0.0001f)
        // A 360dp phone still does, just above the floor.
        assertTrue(canvasUnit(360.dp, 224.dp).second)
        assertTrue(canvasUnit(360.dp, 224.dp).first >= WIDE_MIN_RPX)
    }

    @Test
    fun `the landscape split and a narrow window keep the stacked card at the unit they always had`() {
        // The split is 26% of a landscape phone: 221dp on an 851dp window.
        val (split, splitWide) = canvasUnit(221.dp, 224.dp)
        assertFalse(splitWide)
        assertEquals(221f / 150f, split.value, 0.0001f)
        // Under 1.2dp a unit the wide card's text would be too small: the stacked card, capped as before.
        val (narrow, narrowWide) = canvasUnit(340.dp, 224.dp)
        assertFalse(narrowWide, "340dp would draw the wide card at 1.17dp")
        assertEquals(224f / 150f, narrow.value, 0.0001f)
    }

    @Test
    fun `a wide card never draws bigger than the split's card, unless a second display raises the cap`() {
        val (tablet, wide) = canvasUnit(1000.dp, 224.dp)
        assertTrue(wide)
        assertEquals(224f / 150f, tablet.value, 0.0001f, "capped where the split's card is")
        assertEquals(400f / 150f, canvasUnit(1000.dp, 400.dp).first.value, 0.0001f, "a display's own cap")
    }

    @Test
    fun `every Pokemon card goes through PcMonCard, and the canvas tells it whether it is wide`() {
        for (f in listOf("TrackerPanel.kt", "NdsTrackerPanel.kt")) {
            val s = read(f)
            assertEquals(2, Regex("PcMonCard\\(head = \\{").findAll(s).count(), "$f: your card and the enemy's")
            assertFalse(Regex("PcCard \\{\\s*PcHeadBlock\\(").containsMatchIn(s), "$f still stacks a card by hand")
        }
        val pc = read("PcTracker.kt")
        assertTrue("CompositionLocalProvider(LocalRpx provides rpx, LocalTrackerWide provides wide)" in pc)
        val card = pc.substringAfter("fun PcMonCard(").substringBefore("\n}\n")
        assertTrue("if (LocalTrackerWide.current)" in card)
        assertTrue("Column(Modifier.width(PcRef.HEAD_W.rp)) { head() }" in card, "the head at the reference's 140 units")
        assertTrue("Column(Modifier.weight(1f))" in card && "LocalMovesBeside provides true" in card, "the rest beside it")
        assertTrue("head()\n        rest()" in card, "and stacked, as the reference has it, when it is not wide")
    }

    @Test
    fun `beside the head the moves table drops the rule over its header, and the DS card the one over its encounter row`() {
        val moves = read("PcTracker.kt").substringAfter("fun PcMovesSection(").substringBefore("\n}\n")
        assertTrue("if (!LocalMovesBeside.current) Box(Modifier.fillMaxWidth().height(1.dp)" in moves)
        assertTrue("if (!LocalMovesBeside.current) androidx.compose.foundation.layout.Box(" in read("NdsTrackerPanel.kt"))
    }

    // ---------------------------------------------------------------- the pieces

    @Test
    fun `a status is a pill in the games' colour for it, and a screen reader hears the word`() {
        val codes = listOf("BRN", "FRZ", "PAR", "PSN", "SLP", "FNT")
        assertEquals(codes.size, codes.map { TrackerStatus.color(it) }.toSet().size, "six statuses, six colours")
        assertEquals("Paralyzed", TrackerStatus.spoken("PAR"))
        assertEquals("Asleep", TrackerStatus.spoken("slp"))
        assertEquals(TrackerStatus.color("PSN"), TrackerStatus.color("TOX"), "badly poisoned is poison's colour")
        val head = read("PcTracker.kt").substringAfter("fun PcHeadBlock(").substringBefore("\n}\n")
        assertTrue("if (status.isNotEmpty()) TrackerStatusPill(status, " in head)
        assertFalse("PcAssets.status(" in head, "the pixel image is not drawn any more")
    }

    @Test
    fun `everything takes its colour from the theme, so presets, custom themes and Auto Pokemon Themes repaint it`() {
        val look = read("TrackerLook.kt")
        for (token in listOf("Pc.Border.copy(alpha = 0.55f)", "Pc.Border.copy(alpha = 0.28f)", "Pc.Text.copy(alpha = 0.10f)"))
            assertTrue(token in look, token)
        // Read at draw time, never captured: a getter, not a val holding a colour.
        assertTrue("val outline: Color get() =" in look && "val divider: Color get() =" in look && "val inset: Color get() =" in look)
        val pc = read("PcTracker.kt")
        val card = pc.substringAfter("fun PcCard(content").substringBefore("\n}\n")
        assertTrue("TrackerBackground.boxFill(Pc.Ground)" in card && "TrackerLook.outline" in card)
        val marks = pc.substringAfter("fun PcMarkColumn(").substringBefore("\n}\n")
        assertTrue("TrackerLook.inset" in marks && "markColor.copy(alpha = 0.22f)" in marks, "a mark chip is washed in its mark's colour")
    }

    @Test
    fun `the new files keep the copy rules`() {
        for (name in listOf("TrackerLook.kt", "PcTracker.kt", "TrackerPanel.kt", "NdsTrackerPanel.kt")) {
            val text = read(name)
            assertFalse(text.contains(0x2014.toChar()), "$name has an em dash")
            assertFalse(text.contains(0x2013.toChar()), "$name has an en dash")
            assertFalse(Regex("\\bAI\\b").containsMatchIn(text), "$name mentions AI")
        }
    }
}
